//! admin 认证服务(FLOW-09/10):BCrypt 行锁登录、admin JWT 签发、会话生命周期。
//! 语义对齐 Java AdminService(行锁串行化、Redis beforeCommit 发布、无失败锁定)。

use common::state::SharedState;
use sea_orm::ConnectionTrait;
use sea_orm::Statement;
use sea_orm::TransactionTrait;

use crate::enums::AdminStatus;
use crate::security::{JwtProvider, ADMIN_ACCESS_TTL};
use crate::service::{permissions, SvcError};

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

/// FLOW-09 adminLogin:行锁 → BCrypt 校验(40103)→ 禁用拒登(40302)→ 会话签发。
/// 无失败次数锁定(对齐 Java 现状)。
pub async fn login(
    state: &SharedState,
    jwt: &JwtProvider,
    email: &str,
    password: &str,
    ip: &str,
    user_agent: Option<&str>,
) -> Result<AdminLoginOutcome, SvcError> {
    let email = email.trim().to_lowercase();

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
        return Err(SvcError::code(40103));
    }
    // 禁用拒登(40302)
    if status != AdminStatus::Active.code() as i8 {
        return Err(SvcError::code(40302));
    }

    // admin JWT 签发(jti)
    let jti = uuid_v4();
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

fn uuid_v4() -> String {
    let mut seed = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_nanos() as u64
        ^ (std::process::id() as u64) << 32;
    let mut hex = String::with_capacity(36);
    for i in 0..36 {
        if matches!(i, 8 | 13 | 18 | 23) {
            hex.push('-');
        } else {
            seed = seed
                .wrapping_mul(6364136223846793005)
                .wrapping_add(1442695040888963407);
            hex.push(char::from_digit((seed >> 33) as u32 & 0xF, 16).unwrap_or('0'));
        }
    }
    hex
}
