//! admin 认证服务(FLOW-09/10):BCrypt 行锁登录、admin JWT 签发、会话生命周期。
//! 登录安全三件套(2026-09-15):
//! - 失败锁定:账号维度(连续失败达 auth_config 阈值锁 N 分钟)+ IP 维度(1h 内 20 次熔断);
//!   不存在账号同样计数——锁定响应恒定,枚举者无法区分账号存在性
//! - 恒定响应:邮箱不存在跑 dummy BCrypt(耗时对齐);禁用与密码错统一 40103
//! - 真随机 jti:CSPRNG(uuid v4),替代旧 LCG(可预测)

use common::state::SharedState;
use sea_orm::ConnectionTrait;
use sea_orm::Statement;
use sea_orm::TransactionTrait;

use crate::enums::AdminStatus;
use crate::security::{JwtProvider, ADMIN_ACCESS_TTL};
use crate::service::{permissions, SvcError};

/// 恒定响应 dummy hash(bcrypt cost 12,对齐真实口令哈希耗时;明文无意义)
const DUMMY_BCRYPT_HASH: &str = "$2b$12$W8oAAsy9SsoHcldMhHbG/OuTwCGa2alhcsyv22.7MSSxomW2Y0JTe";

/// IP 维度失败熔断阈值(1h 固定窗口;硬编码——字典爆破防线,不随业务配置)
const IP_FAIL_LIMIT_PER_HOUR: i64 = 20;

pub struct AdminLoginOutcome {
    pub token: String,
    pub admin: AdminDto,
    pub permission_keys: Vec<String>,
    pub is_super: bool,
}

pub struct AdminDto {
    pub id: i64,
    pub name: Option<String>,
    pub email: String,
    pub role_id: i64,
    pub status: i32,
    pub last_login_at: Option<String>,
}

/// FLOW-09 adminLogin:失败锁定 → 行锁 → BCrypt 校验(40103)→ 会话签发。
pub async fn login(
    state: &SharedState,
    jwt: &JwtProvider,
    email: &str,
    password: &str,
    ip: &str,
    user_agent: Option<&str>,
) -> Result<AdminLoginOutcome, SvcError> {
    let email = email.trim().to_lowercase();

    // 锁定参数(auth_config 可配,admin 后台调整;读取失败回退默认值——锁定是增强,fail-open)
    let (max_attempts, lock_seconds) = match crate::service::authconfig::get(state).await {
        Ok(c) => (
            c.admin_login_max_attempts.max(1) as i64,
            c.admin_login_lock_minutes.max(1) as u64 * 60,
        ),
        Err(e) => {
            tracing::warn!(error = %e, "[admin-login] auth_config 不可读,锁定参数回退默认(5 次/15 分钟)");
            (5, 900)
        }
    };

    // STEP-0 锁定检查(Redis 缺席 fail-open,不锁死全部管理员)
    if login_locked(state, &email, ip).await {
        return Err(SvcError::code(42903));
    }

    // 事务 + 行锁(SELECT ... FOR UPDATE,对齐 Java selectByEmailForUpdate)
    let txn = state.db.begin().await?;
    let row = txn
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT id, name, email, password_hash, role_id, status, last_login_at
               FROM admin_user WHERE email = ? FOR UPDATE"#,
            [email.clone().into()],
        ))
        .await?;
    let Some(row) = row else {
        // 恒定响应:不存在账号也跑一次 BCrypt(耗时对齐,消除时序枚举信道)后同样计数锁定
        let _ = bcrypt::verify(password, DUMMY_BCRYPT_HASH);
        record_login_failure(state, &email, ip, max_attempts, lock_seconds).await;
        return Err(SvcError::code(40103));
    };
    let admin_id = row
        .try_get_by_index::<u64>(0)
        .map_err(|_| SvcError::code(50000))? as i64;
    let name = row.try_get_by_index::<Option<String>>(1).ok().flatten();
    let stored_hash = row
        .try_get_by_index::<String>(3)
        .map_err(|_| SvcError::code(50000))?;
    let role_id = row
        .try_get_by_index::<i64>(4)
        .map_err(|_| SvcError::code(50000))?;
    let status = row
        .try_get_by_index::<i8>(5)
        .map_err(|_| SvcError::code(50000))?;
    let last_login_at = row
        .try_get_by_index::<Option<chrono::NaiveDateTime>>(6)
        .ok()
        .flatten()
        .map(common::time::format_iso);

    // BCrypt 校验(40103)
    if !bcrypt::verify(password, &stored_hash).unwrap_or(false) {
        record_login_failure(state, &email, ip, max_attempts, lock_seconds).await;
        return Err(SvcError::code(40103));
    }
    // 禁用统一 40103(原 40302 会确认「密码正确」,关闭错误码枚举信道);同样计数保持完全恒定
    if status != AdminStatus::Active.code() as i8 {
        record_login_failure(state, &email, ip, max_attempts, lock_seconds).await;
        return Err(SvcError::code(40103));
    }

    // admin JWT 签发(jti = CSPRNG uuid v4)
    let jti = uuid::Uuid::new_v4().to_string();
    let token = jwt
        .issue_admin(admin_id, role_id, &jti, ADMIN_ACCESS_TTL)
        .map_err(|_| SvcError::code(50000))?;

    // admin_session INSERT + last_login_at 更新(事务内)
    txn.execute(Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        r#"INSERT INTO admin_session (admin_id, token_id, ip, device, status, created_at, updated_at)
           VALUES (?, ?, ?, ?, 1, NOW(), NOW())"#,
        [
            admin_id.into(),
            jti.clone().into(),
            ip.into(),
            user_agent.into(),
        ],
    ))
    .await?;
    txn.execute(Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        r#"UPDATE admin_user SET last_login_at = NOW(), version = version + 1 WHERE id = ?"#,
        [admin_id.into()],
    ))
    .await?;
    txn.commit().await?;

    // 登录成功:清账号失败计数(锁定标记不在此清——能成功说明未锁定)
    if let Some(mut conn) = state.redis.clone() {
        use redis::AsyncCommands as _;
        let _: Result<(), _> = conn.del(format!("admin:login:fail:{email}")).await;
    }

    // Redis beforeCommit 语义:提交后回填 30s 缓存键(validate_admin 命中即免 DB)
    let validity = crate::service::session::admin_key(&jti);
    if let Some(mut conn) = state.redis.clone() {
        use redis::AsyncCommands as _;
        let _: Result<(), _> = conn.set_ex(validity, "1", 30).await;
    }

    // 权限 + 超管
    let permission_keys = permissions::resolve(state, admin_id).await?;
    let is_super = is_super_role(state, role_id).await?;

    Ok(AdminLoginOutcome {
        token,
        admin: AdminDto {
            id: admin_id,
            name,
            email,
            role_id,
            status: status as i32,
            last_login_at,
        },
        permission_keys,
        is_super,
    })
}

/// 锁定检查:账号锁定标记 或 IP 1h 失败熔断;Redis 缺席 fail-open(false=未锁)
async fn login_locked(state: &SharedState, email: &str, ip: &str) -> bool {
    let Some(mut conn) = state.redis.clone() else {
        return false;
    };
    use redis::AsyncCommands as _;
    let locked: Option<i64> = conn.get(format!("admin:login:lock:{email}")).await.ok().flatten();
    if locked.unwrap_or(0) == 1 {
        return true;
    }
    let ip_fails: Option<i64> = conn.get(format!("admin:login:ip:fail:{ip}:h")).await.ok().flatten();
    ip_fails.unwrap_or(0) > IP_FAIL_LIMIT_PER_HOUR
}

/// 失败计数与锁定(账号键计数窗口=锁定时长,达阈值设锁定标记并清零;
/// IP 键 1h 固定窗口)。Redis 缺席 no-op。
async fn record_login_failure(
    state: &SharedState,
    email: &str,
    ip: &str,
    max_attempts: i64,
    lock_seconds: u64,
) {
    let Some(mut conn) = state.redis.clone() else {
        return;
    };
    use redis::AsyncCommands as _;
    let fail_key = format!("admin:login:fail:{email}");
    let fails: i64 = conn.incr(&fail_key, 1).await.unwrap_or(0);
    let _: Result<(), _> = conn.expire(&fail_key, lock_seconds as i64).await;
    if fails >= max_attempts {
        let lock_key = format!("admin:login:lock:{email}");
        let _: Result<(), _> = conn.set_ex(lock_key, "1", lock_seconds).await;
        let _: Result<(), _> = conn.del(&fail_key).await;
        tracing::warn!("[admin-login] 账号锁定 email={email} 窗口={lock_seconds}s(连续失败 {fails} 次)");
    }
    let ip_key = format!("admin:login:ip:fail:{ip}:h");
    let ip_fails: i64 = conn.incr(&ip_key, 1).await.unwrap_or(0);
    if ip_fails == 1 {
        let _: Result<(), _> = conn.expire(&ip_key, 3600).await;
    }
    if ip_fails == IP_FAIL_LIMIT_PER_HOUR + 1 {
        tracing::warn!("[admin-login] IP 熔断 ip={ip}(1h 内失败达 {ip_fails} 次)");
    }
}

async fn is_super_role(state: &SharedState, role_id: i64) -> Result<bool, SvcError> {
    let row = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT is_locked FROM role WHERE id = ?"#,
            [role_id.into()],
        ))
        .await?;
    Ok(row
        .and_then(|r| r.try_get_by_index::<i8>(0).ok())
        .map(|v| v != 0)
        .unwrap_or(false))
}

/// adminLogout:撤销 admin_session + Redis DEL
pub async fn logout(state: &SharedState, token_id: &str) -> Result<(), SvcError> {
    state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"UPDATE admin_session SET status = 2, updated_at = NOW() WHERE token_id = ?"#,
            [token_id.into()],
        ))
        .await?;
    crate::service::session::revoke_admin(state, token_id).await;
    Ok(())
}

/// adminMe(FUNC-021):requireActiveAdmin 语义(valid+active 复核)
pub struct AdminMeData {
    pub admin: AdminDto,
    pub role_name: Option<String>,
    pub is_super: bool,
    pub permission_keys: Vec<String>,
}

pub async fn me_data(state: &SharedState, admin_id: i64) -> Result<AdminMeData, SvcError> {
    let row = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT a.id, a.name, a.email, a.role_id, a.status, a.last_login_at, r.name, r.is_locked
               FROM admin_user a LEFT JOIN role r ON r.id = a.role_id WHERE a.id = ?"#,
            [admin_id.into()],
        ))
        .await?;
    let Some(row) = row else {
        return Err(SvcError::code(40100));
    };
    let status = row
        .try_get_by_index::<i8>(4)
        .map_err(|_| SvcError::code(50000))?;
    if status != AdminStatus::Active.code() as i8 {
        return Err(SvcError::code(40302));
    }
    let role_id = row
        .try_get_by_index::<i64>(3)
        .map_err(|_| SvcError::code(50000))?;
    let is_super = row.try_get_by_index::<i8>(7).unwrap_or(0) != 0;
    let permission_keys = permissions::resolve(state, admin_id).await?;
    Ok(AdminMeData {
        admin: AdminDto {
            id: row
                .try_get_by_index::<u64>(0)
                .map_err(|_| SvcError::code(50000))? as i64,
            name: row.try_get_by_index::<Option<String>>(1).ok().flatten(),
            email: row
                .try_get_by_index::<String>(2)
                .map_err(|_| SvcError::code(50000))?,
            role_id,
            status: status as i32,
            last_login_at: row
                .try_get_by_index::<Option<chrono::NaiveDateTime>>(5)
                .ok()
                .flatten()
                .map(common::time::format_iso),
        },
        role_name: row.try_get_by_index::<Option<String>>(6).ok().flatten(),
        is_super,
        permission_keys,
    })
}
