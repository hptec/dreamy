//! 登录编排(FLOW-02/03/04):OTP 登录、OIDC 登录、令牌刷新。
//! 会话签发 = 冷备 INSERT 取回数字 id → Redis 主存写入 → login_history。

use chrono::NaiveDateTime;
use common::state::SharedState;
use sea_orm::{ConnectionTrait, EntityTrait, Statement};

use crate::entity::user;
use crate::enums::{AuthProvider, LoginOutcome, UserStatus};
use crate::security::{JwtProvider, StoreClaims, STORE_ACCESS_TTL, STORE_REFRESH_TTL};
use crate::service::{merge, otp, session, SvcError};

pub struct LoginContext {
    pub ip: String,
    pub user_agent: Option<String>,
    pub device_fingerprint: Option<String>,
}

pub struct LoginResult {
    pub tokens: TokenPairDto,
    pub user: user::Model,
    pub new_account: bool,
    pub new_device: bool,
}

pub struct TokenPairDto {
    pub access_token: String,
    pub refresh_token: String,
    pub access_expires_at: NaiveDateTime,
    pub refresh_expires_at: NaiveDateTime,
}

/// FLOW-02 OTP 邮箱登录
pub async fn login_with_otp(
    state: &SharedState,
    jwt: &JwtProvider,
    email: &str,
    code: &str,
    ctx: &LoginContext,
) -> Result<LoginResult, SvcError> {
    // STEP-01~03 消费验证码(锁内)
    otp::consume_for_login(state, email, code).await?;
    // STEP-04 归并/建号(EMAIL 渠道)
    let outcome = merge::resolve_or_merge(
        state,
        AuthProvider::Email,
        &email.trim().to_lowercase(),
        Some(email),
        true, // OTP 登录视为已验证邮箱
        false,
        None,
    )
    .await?;
    complete_login(
        state,
        jwt,
        outcome.user,
        outcome.new_account,
        AuthProvider::Email,
        ctx,
    )
    .await
}

/// FLOW-03 OIDC 登录
pub async fn login_with_oidc(
    state: &SharedState,
    jwt: &JwtProvider,
    provider: AuthProvider,
    oidc: &crate::oidc::OidcResult,
    ctx: &LoginContext,
) -> Result<LoginResult, SvcError> {
    let outcome = merge::resolve_or_merge(
        state,
        provider,
        &oidc.sub,
        oidc.email.as_deref(),
        oidc.email_verified,
        oidc.hidden_email,
        oidc.relay_email.as_deref(),
    )
    .await?;
    // 禁用拒签在 complete_login 内统一处理
    complete_login(state, jwt, outcome.user, outcome.new_account, provider, ctx).await
}

async fn complete_login(
    state: &SharedState,
    jwt: &JwtProvider,
    user: user::Model,
    new_account: bool,
    method: AuthProvider,
    ctx: &LoginContext,
) -> Result<LoginResult, SvcError> {
    // STEP-05 禁用拒签(40301)
    if UserStatus::from_code(user.status as i32) != Some(UserStatus::Active) {
        return Err(SvcError::code(40301));
    }

    // 新设备判定:login_history 无 (user_id, device, SUCCESS)
    let device = ctx
        .device_fingerprint
        .clone()
        .or_else(|| ctx.user_agent.clone())
        .unwrap_or_default();
    let new_device = is_new_device(state, user.id, &device).await;

    // STEP-06 会话签发:冷备 INSERT 取数字 id → Redis 主存
    let tokens = open_session(state, jwt, &user, method, new_device, ctx, &device).await?;

    Ok(LoginResult {
        tokens,
        user,
        new_account,
        new_device,
    })
}

async fn is_new_device(state: &SharedState, user_id: u64, device: &str) -> bool {
    if device.is_empty() {
        return true;
    }
    let found = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT 1 FROM login_history
               WHERE user_id = ? AND device = ? AND result = 1 LIMIT 1"#,
            [(user_id as i64).into(), device.into()],
        ))
        .await;
    !matches!(found, Ok(Some(_)))
}

/// 会话签发:冷备(user_session INSERT + login_history INSERT)→ Redis(session:{jti} + user_sessions 集合)
async fn open_session(
    state: &SharedState,
    jwt: &JwtProvider,
    user: &user::Model,
    method: AuthProvider,
    new_device: bool,
    ctx: &LoginContext,
    device: &str,
) -> Result<TokenPairDto, SvcError> {
    let now = chrono::Local::now().naive_local();
    let access_exp = now + chrono::Duration::seconds(STORE_ACCESS_TTL);
    let refresh_exp = now + chrono::Duration::seconds(STORE_REFRESH_TTL);

    let access_jti = uuid_v4();
    let refresh_jti = uuid_v4();

    let access_token = jwt
        .issue_store(
            user.id as i64,
            &access_jti,
            &method.code().to_string(),
            false,
            STORE_ACCESS_TTL,
        )
        .map_err(|_| SvcError::code(50000))?;
    let refresh_token = jwt
        .issue_store(
            user.id as i64,
            &refresh_jti,
            &method.code().to_string(),
            true,
            STORE_REFRESH_TTL,
        )
        .map_err(|_| SvcError::code(50000))?;

    // 冷备 INSERT(数字 session_id 来源)
    let inserted = state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"INSERT INTO user_session
               (user_id, token_id, refresh_token_id, access_expires_at, refresh_expires_at,
                device, browser, ip, is_new_device, method, status, version, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, NOW(), NOW())"#,
            [
                (user.id as i64).into(),
                access_jti.clone().into(),
                refresh_jti.clone().into(),
                access_exp.into(),
                refresh_exp.into(),
                device.into(),
                ctx.user_agent.clone().into(),
                ctx.ip.clone().into(),
                (new_device as i8).into(),
                (method.code() as i32).into(),
            ],
        ))
        .await?;
    let session_id = inserted.last_insert_id() as i64;

    // login_history(成功)
    let _ = state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"INSERT INTO login_history
               (user_id, email, method, ip, device, result, is_new_device, notified, created_at)
               VALUES (?, ?, ?, ?, ?, 1, ?, 0, NOW())"#,
            [
                (user.id as i64).into(),
                user.email.clone().into(),
                (method.code() as i32).into(),
                ctx.ip.clone().into(),
                device.into(),
                (new_device as i8).into(),
            ],
        ))
        .await;

    // Redis 主存(session:{access_jti} + 集合索引)
    session::write_session(
        state,
        &access_jti,
        &session::SessionData {
            id: session_id,
            user_id: user.id as i64,
            method: method.code() as i8,
            refresh_token_id: Some(refresh_jti.clone()),
            access_exp: access_exp.and_utc().timestamp(),
            refresh_exp: refresh_exp.and_utc().timestamp(),
            device: if device.is_empty() {
                None
            } else {
                Some(device.to_string())
            },
            browser: ctx.user_agent.clone(),
            ip: Some(ctx.ip.clone()),
            location: None,
        },
    )
    .await;

    // 新设备通知(不阻塞主流程)
    if new_device {
        let st = state.clone();
        let email = user.email.clone();
        let ip = ctx.ip.clone();
        let dev = device.to_string();
        tokio::spawn(async move {
            let vars = [
                (
                    "device".to_string(),
                    if dev.is_empty() {
                        "Unknown".into()
                    } else {
                        dev
                    },
                ),
                (
                    "ip".to_string(),
                    if ip.is_empty() { "Unknown".into() } else { ip },
                ),
                ("location".to_string(), "Unknown".to_string()),
            ];
            let _ = crate::mail::send_template(&st, &email, "new_device", "en", &vars).await;
        });
    }

    Ok(TokenPairDto {
        access_token,
        refresh_token,
        access_expires_at: access_exp,
        refresh_expires_at: refresh_exp,
    })
}

/// FLOW-04 令牌刷新(滑动重签:旧 jti 失效 + 新对签发)
pub async fn refresh(
    state: &SharedState,
    jwt: &JwtProvider,
    refresh_token: &str,
) -> Result<TokenPairDto, SvcError> {
    // 验签(refresh 令牌)
    let claims: StoreClaims = jwt
        .parse_store(refresh_token)
        .map_err(|_| SvcError::code(40102))?;
    if !claims.refresh {
        return Err(SvcError::code(40102)); // access 令牌冒充 refresh
    }
    // 旧会话链:session:{access_jti} 主存里 refresh_token_id 应匹配
    // (冷备为审计;主存为准——refresh 旋转后旧 access 失效)
    // 校验 refresh jti 活性:冷备 status=ACTIVE + refresh_expires 未过
    let row = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT id, user_id, token_id, refresh_expires_at, device, browser, ip, method
               FROM user_session WHERE refresh_token_id = ? AND status = 1"#,
            [claims.jti.clone().into()],
        ))
        .await?
        .ok_or(SvcError::code(40102))?;

    use sea_orm::TryGetable;
    macro_rules! col {
        ($t:ty, $i:expr) => {
            row.try_get_by_index::<$t>($i)
                .map_err(|_| SvcError::code(5001))?
        };
    }
    let row_data = RowData {
        id: col!(i64, 0),
        user_id: col!(i64, 1),
        old_access_jti: row
            .try_get_by_index::<Option<String>>(2)
            .ok()
            .flatten()
            .unwrap_or_default(),
        refresh_expires_at: row
            .try_get_by_index::<Option<NaiveDateTime>>(3)
            .ok()
            .flatten(),
        device: row.try_get_by_index::<Option<String>>(4).ok().flatten(),
        browser: row.try_get_by_index::<Option<String>>(5).ok().flatten(),
        ip: row.try_get_by_index::<Option<String>>(6).ok().flatten(),
        method: col!(i8, 7),
    };
    struct RowData {
        id: i64,
        user_id: i64,
        old_access_jti: String,
        refresh_expires_at: Option<NaiveDateTime>,
        device: Option<String>,
        browser: Option<String>,
        ip: Option<String>,
        method: i8,
    }
    let row = row_data;

    // 用户存在性与禁用复核
    let user = crate::entity::user::Entity::find_by_id(row.user_id as u64)
        .one(&state.db)
        .await?
        .ok_or(SvcError::code(40102))?;
    if UserStatus::from_code(user.status as i32) != Some(UserStatus::Active) {
        return Err(SvcError::code(40301));
    }
    let Some(refresh_exp) = row.refresh_expires_at else {
        return Err(SvcError::code(40102));
    };
    if refresh_exp < chrono::Local::now().naive_local() {
        return Err(SvcError::code(40102));
    }

    let now = chrono::Local::now().naive_local();
    let access_exp = now + chrono::Duration::seconds(STORE_ACCESS_TTL);
    let new_refresh_exp = now + chrono::Duration::seconds(STORE_REFRESH_TTL);
    let access_jti = uuid_v4();
    let new_refresh_jti = uuid_v4();
    let access_token = jwt
        .issue_store(
            user.id as i64,
            &access_jti,
            &claims.method,
            false,
            STORE_ACCESS_TTL,
        )
        .map_err(|_| SvcError::code(50000))?;
    let new_refresh_token = jwt
        .issue_store(
            user.id as i64,
            &new_refresh_jti,
            &claims.method,
            true,
            STORE_REFRESH_TTL,
        )
        .map_err(|_| SvcError::code(50000))?;

    // 旧链失效:撤销旧 access 主存 + 冷备整行 UPDATE(新 jti 对)
    if !row.old_access_jti.is_empty() {
        session::revoke_store(state, &row.old_access_jti, row.user_id).await;
    }
    let _ = state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"UPDATE user_session
               SET token_id = ?, refresh_token_id = ?, access_expires_at = ?, refresh_expires_at = ?, version = version + 1
               WHERE id = ?"#,
            [
                access_jti.clone().into(),
                new_refresh_jti.clone().into(),
                access_exp.into(),
                new_refresh_exp.into(),
                row.id.into(),
            ],
        ))
        .await;
    session::write_session(
        state,
        &access_jti,
        &session::SessionData {
            id: row.id,
            user_id: row.user_id,
            method: row.method,
            refresh_token_id: Some(new_refresh_jti),
            access_exp: access_exp.and_utc().timestamp(),
            refresh_exp: new_refresh_exp.and_utc().timestamp(),
            device: row.device,
            browser: row.browser,
            ip: row.ip,
            location: None,
        },
    )
    .await;

    Ok(TokenPairDto {
        access_token,
        refresh_token: new_refresh_token,
        access_expires_at: access_exp,
        refresh_expires_at: new_refresh_exp,
    })
}

fn uuid_v4() -> String {
    // UUIDv4(系统熵;格式 8-4-4-4-12)
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

/// 登录失败历史(FLOW 失败路径:OTP 校验失败时由 otp 层写;此处供编排层复用)
pub async fn record_failed_login(
    state: &SharedState,
    user_id: Option<u64>,
    email: &str,
    method: AuthProvider,
    ip: &str,
) {
    let _ = state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"INSERT INTO login_history
               (user_id, email, method, ip, result, is_new_device, notified, created_at)
               VALUES (?, ?, ?, ?, 2, 0, 0, NOW())"#,
            [
                user_id
                    .map(|u| (u as i64).into())
                    .unwrap_or_else(|| sea_orm::sea_query::Value::BigInt(None)),
                email.into(),
                (method.code() as i32).into(),
                ip.into(),
            ],
        ))
        .await;
    let _ = LoginOutcome::Failed.code(); // 枚举值即 SQL 常量 2(上方字面量)
}
