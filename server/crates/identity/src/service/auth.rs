//! 登录编排(FLOW-02/03/04):OTP 登录、OIDC 登录、令牌刷新。
//! 会话签发 = 冷备 INSERT 取回数字 id → Redis 主存写入 → login_history。

use chrono::NaiveDateTime;
use common::partition;
use common::state::SharedState;
use sea_orm::sea_query::Expr;
use sea_orm::{ActiveModelTrait, ColumnTrait, EntityTrait, QueryFilter, QueryTrait, Set};

use crate::entity::{login_history, user, user_session};
use crate::enums::{AuthProvider, LoginOutcome, SessionStatus, UserStatus};
use crate::security::{JwtProvider, StoreClaims, STORE_ACCESS_TTL, STORE_REFRESH_TTL};
use crate::service::{merge, otp, session, SvcError};

/// TTL 配置化(D2):auth_config 可调,读取失败回退编译期默认(仅影响新签发)
async fn store_ttls(state: &SharedState) -> (i64, i64) {
    match crate::service::authconfig::get(state).await {
        Ok(c) => (
            (c.store_access_ttl_minutes.max(1) as i64) * 60,
            (c.store_refresh_ttl_days.max(1) as i64) * 86_400,
        ),
        Err(e) => {
            tracing::warn!(error = %e, "[auth] auth_config 不可读,token TTL 回退默认(2h/30d)");
            (STORE_ACCESS_TTL, STORE_REFRESH_TTL)
        }
    }
}

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

    // 新设备判定:90 天窗口内无 (user_id, device, SUCCESS)
    // (窗口谓词兼作月分区裁剪:无时间下界会扫全部月分区,随历史增长线性变慢)
    let device = ctx
        .device_fingerprint
        .clone()
        .or_else(|| ctx.user_agent.clone())
        .unwrap_or_default();
    let new_device = is_new_device(state, user.id, &device).await;

    // STEP-06 会话签发:冷备 INSERT 取数字 id → Redis 主存
    let (access_ttl, refresh_ttl) = store_ttls(state).await;
    let tokens = open_session(
        state,
        jwt,
        &user,
        method,
        new_device,
        ctx,
        &device,
        access_ttl,
        refresh_ttl,
    )
    .await?;

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
    let cutoff = chrono::Local::now().naive_local() - chrono::Duration::days(90);
    let found = login_history::Entity::find()
        .filter(login_history::Column::UserId.eq(user_id))
        .filter(login_history::Column::Device.eq(device))
        .filter(login_history::Column::Result.eq(LoginOutcome::Success.code() as i8))
        .filter(login_history::Column::CreatedAt.gte(cutoff))
        .one(&state.db)
        .await;
    !matches!(found, Ok(Some(_)))
}

/// 会话签发:冷备(user_session INSERT + login_history INSERT)→ Redis(session:{jti} + user_sessions 集合)
#[allow(clippy::too_many_arguments)]
async fn open_session(
    state: &SharedState,
    jwt: &JwtProvider,
    user: &user::Model,
    method: AuthProvider,
    new_device: bool,
    ctx: &LoginContext,
    device: &str,
    access_ttl: i64,
    refresh_ttl: i64,
) -> Result<TokenPairDto, SvcError> {
    let now = chrono::Local::now().naive_local();
    let access_exp = now + chrono::Duration::seconds(access_ttl);
    let refresh_exp = now + chrono::Duration::seconds(refresh_ttl);

    let access_jti = uuid_v4();
    let refresh_jti = uuid_v4();

    let access_token = jwt
        .issue_store(
            user.id as i64,
            &access_jti,
            &method.code().to_string(),
            false,
            access_ttl,
        )
        .map_err(|_| SvcError::code(50000))?;
    let refresh_token = jwt
        .issue_store(
            user.id as i64,
            &refresh_jti,
            &method.code().to_string(),
            true,
            refresh_ttl,
        )
        .map_err(|_| SvcError::code(50000))?;

    // 冷备 INSERT(数字 session_id 来源;单自增主键 → insert 回填 id)
    let inserted = user_session::ActiveModel {
        user_id: Set(user.id),
        token_id: Set(access_jti.clone()),
        refresh_token_id: Set(Some(refresh_jti.clone())),
        access_expires_at: Set(Some(access_exp)),
        refresh_expires_at: Set(Some(refresh_exp)),
        device: Set(Some(device.to_string())),
        browser: Set(ctx.user_agent.clone()),
        ip: Set(Some(ctx.ip.clone())),
        is_new_device: Set(new_device as i8),
        method: Set(method.code() as i8),
        status: Set(SessionStatus::Active.code() as i8),
        version: Set(0),
        ..Default::default()
    }
    .insert(&state.db)
    .await?;
    let session_id = inserted.id as i64;

    // login_history(成功);v3 分区拦截:写入前确保当月分区 + 1526 自愈重试(失败不阻塞登录)
    let lh_now = chrono::Local::now().naive_local();
    let _ = partition::insert_self_heal(
        state,
        "login_history",
        &[lh_now],
        login_history::Entity::insert(login_history::ActiveModel {
            user_id: Set(Some(user.id)),
            email: Set(Some(user.email.clone())),
            method: Set(method.code() as i8),
            ip: Set(Some(ctx.ip.clone())),
            device: Set(Some(device.to_string())),
            result: Set(LoginOutcome::Success.code() as i8),
            is_new_device: Set(new_device as i8),
            notified: Set(0),
            created_at: Set(lh_now),
            ..Default::default()
        })
        .build(sea_orm::DatabaseBackend::MySql),
    )
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

/// FLOW-04 令牌刷新(滑动重签:旧 jti 失效 + 新对签发)。
/// 活性校验以冷备行为准(可撤销状态机:status/refresh_expires);Redis 主存承载 access 热校验。
/// V3 并发安全:UPDATE 带乐观锁(version 条件),同 refresh_token 并发刷仅一成功;
/// V4 重用检测:已旋转的旧 jti 再次出现 → 疑似盗用,整链撤销。
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
    let (access_ttl, refresh_ttl) = store_ttls(state).await;

    // 校验 refresh jti 活性:冷备 status=ACTIVE + refresh_expires 未过
    let row = user_session::Entity::find()
        .filter(user_session::Column::RefreshTokenId.eq(claims.jti.clone()))
        .filter(user_session::Column::Status.eq(SessionStatus::Active.code() as i8))
        .one(&state.db)
        .await?;
    let Some(row) = row else {
        // V4:查无 Active 行时,若该 jti 曾被旋转过 = 旧令牌被重用,疑似盗用 → 整链撤销
        if let Some(uid) = session::rotated_owner(state, &claims.jti).await {
            tracing::warn!(
                jti = %claims.jti,
                user_id = uid,
                "[security] refresh 重用:已旋转 jti 再次出现,疑似令牌盗用,整链撤销"
            );
            session::revoke_all_for_user(state, uid).await;
        }
        return Err(SvcError::code(40102));
    };

    // 用户存在性与禁用复核
    let user = user::Entity::find_by_id(row.user_id)
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
    let access_exp = now + chrono::Duration::seconds(access_ttl);
    let new_refresh_exp = now + chrono::Duration::seconds(refresh_ttl);
    let access_jti = uuid_v4();
    let new_refresh_jti = uuid_v4();
    let access_token = jwt
        .issue_store(
            user.id as i64,
            &access_jti,
            &claims.method,
            false,
            access_ttl,
        )
        .map_err(|_| SvcError::code(50000))?;
    let new_refresh_token = jwt
        .issue_store(
            user.id as i64,
            &new_refresh_jti,
            &claims.method,
            true,
            refresh_ttl,
        )
        .map_err(|_| SvcError::code(50000))?;

    // V4 旋转链证据:旧 refresh jti → user_id(TTL=新 refresh 有效期)
    session::mark_rotated(state, &claims.jti, row.user_id as i64, refresh_ttl).await;
    // 旧 access 主存撤销(软撤销:仅 Redis DEL,冷备行由下方乐观锁 UPDATE 原位续用;
    // 若走完整 revoke_store 会把行标 revoked,下一次刷新即 40102 断链)
    let old_access_jti = row.token_id.clone();
    if !old_access_jti.is_empty() {
        session::revoke_store_soft(state, &old_access_jti, row.user_id as i64).await;
    }
    // V3 乐观锁:version 条件防并发双活(并发刷同一 token 仅一成功)
    let updated = user_session::Entity::update_many()
        .col_expr(user_session::Column::TokenId, Expr::value(access_jti.clone()))
        .col_expr(
            user_session::Column::RefreshTokenId,
            Expr::value(new_refresh_jti.clone()),
        )
        .col_expr(user_session::Column::AccessExpiresAt, Expr::value(access_exp))
        .col_expr(
            user_session::Column::RefreshExpiresAt,
            Expr::value(new_refresh_exp),
        )
        .col_expr(
            user_session::Column::Version,
            Expr::col(user_session::Column::Version).add(1),
        )
        .filter(user_session::Column::Id.eq(row.id))
        .filter(user_session::Column::Version.eq(row.version))
        .exec(&state.db)
        .await?;
    if updated.rows_affected == 0 {
        // 并发输家:本请求签发的令牌对未登记即作废(冷备无行/Redis 无键,天然无效)
        return Err(SvcError::code(40102));
    }
    session::write_session(
        state,
        &access_jti,
        &session::SessionData {
            id: row.id as i64,
            user_id: row.user_id as i64,
            method: row.method as i8,
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
    // CSPRNG(uuid crate;对齐 admin 侧 2026-09-15 修复——旧 LCG jti 可预测)
    uuid::Uuid::new_v4().to_string()
}

/// 登录失败历史(FLOW 失败路径:OTP 校验失败时由 otp 层写;此处供编排层复用)
pub async fn record_failed_login(
    state: &SharedState,
    user_id: Option<u64>,
    email: &str,
    method: AuthProvider,
    ip: &str,
) {
    let now = chrono::Local::now().naive_local();
    let _ = partition::insert_self_heal(
        state,
        "login_history",
        &[now],
        login_history::Entity::insert(login_history::ActiveModel {
            user_id: Set(user_id),
            email: Set(Some(email.to_string())),
            method: Set(method.code() as i8),
            ip: Set(Some(ip.to_string())),
            result: Set(LoginOutcome::Failed.code() as i8),
            is_new_device: Set(0),
            notified: Set(0),
            created_at: Set(now),
            ..Default::default()
        })
        .build(sea_orm::DatabaseBackend::MySql),
    )
    .await;
}
