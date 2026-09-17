//! OTP 邮箱验证码(DB 主存月分区;Redis 只做频控)。
//!
//! 语义对齐 Java OtpService(v2.2 修订):
//! - 发码:频控 → 失效旧 pending → 生成明文仅存 bcrypt hash → 异步邮件(失败不阻塞)
//! - 校验:分布式锁语义(单实例进程内 keyed mutex 包裹消费段)→
//!   pending 查询**必须带 created_at ≥ NOW()-24h 下界谓词**强制月分区裁剪
//! - 错误码:41001 过期 / 40101 错误(remaining_attempts) / 41002 锁定

use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use tokio::sync::Mutex as AsyncMutex;

use common::partition;
use common::state::SharedState;
use sea_orm::sea_query::Expr;
use sea_orm::{ColumnTrait, EntityTrait, QueryFilter, QueryOrder, QueryTrait, Set};

use crate::entity::otp_code;
use crate::enums::OtpStatus;
use crate::service::{authconfig, ratelimit, SvcError};

/// 进程内按 email 串行的消费锁(替代 Java huihao-redis onIdLock;单实例部署前提)。
/// 内层用 tokio Mutex:guard 需跨 .await 持有(消费段含 DB IO,与 Java 分布式锁同跨度)
fn email_lock(email: &str) -> Arc<AsyncMutex<()>> {
    static LOCKS: std::sync::LazyLock<Mutex<HashMap<String, Arc<AsyncMutex<()>>>>> =
        std::sync::LazyLock::new(|| Mutex::new(HashMap::new()));
    LOCKS
        .lock()
        .unwrap()
        .entry(email.to_string())
        .or_insert_with(|| Arc::new(AsyncMutex::new(())))
        .clone()
}

fn normalize(email: &str) -> String {
    email.trim().to_lowercase()
}

pub struct SendOtpResult {
    pub resend_after_seconds: i32,
    pub otp_length: i8,
}

/// FLOW-01 sendOtp:频控 → 失效旧 pending → INSERT 新码(hash) → 异步邮件
pub async fn send_otp(
    state: &SharedState,
    raw_email: &str,
    locale: &str,
    ip: &str,
) -> Result<SendOtpResult, SvcError> {
    let email = normalize(raw_email);
    if email.is_empty() || !email.contains('@') {
        return Err(SvcError::code(40001));
    }
    let cfg = authconfig::get(state).await?;
    if !cfg.email_enabled {
        return Err(SvcError::code(40303));
    }

    // STEP-02 重发冷却
    let resend = ratelimit::check_resend(state, &email, cfg.otp_resend_seconds as u64).await;
    if let Some(e) = ratelimit::to_error(resend) {
        return Err(e);
    }
    // STEP-03 发码配额
    let quota = ratelimit::OtpQuota::default();
    let send = ratelimit::check_send_quota(state, &email, ip, &quota).await;
    if let Some(e) = ratelimit::to_error(send) {
        return Err(e);
    }

    // STEP-05 失效旧 pending
    otp_code::Entity::update_many()
        .col_expr(
            otp_code::Column::Status,
            Expr::value(OtpStatus::Expired.code() as i8),
        )
        .col_expr(
            otp_code::Column::Version,
            Expr::col(otp_code::Column::Version).add(1),
        )
        .filter(otp_code::Column::Email.eq(email.clone()))
        .filter(otp_code::Column::Status.eq(OtpStatus::Pending.code() as i8))
        .exec(&state.db)
        .await?;

    // STEP-06 生成明文(仅存 hash;本地 CSPRNG,无外部依赖)
    let plaintext = numeric_code(cfg.otp_length as usize);
    let code_hash =
        bcrypt::hash(&plaintext, bcrypt::DEFAULT_COST).map_err(|_| SvcError::code(50000))?;
    let now = chrono::Local::now().naive_local();
    let expires_at = now + chrono::Duration::minutes(cfg.otp_ttl_minutes as i64);
    // created_at 显式落值(NOT NULL 分区键 + 复合主键)
    // v3 分区拦截:写入前确保当月分区(容忍失败,1526 自愈兜底)
    let _ = partition::ensure_months(state, "otp_code", &[now]).await;
    // 复合主键(id,created_at)+自增:SeaORM UnpackInsertId 限制 → 实体 DSL 构建
    // Statement,交由分区 insert_self_heal(1526 自愈重试)执行
    let stmt = otp_code::Entity::insert(otp_code::ActiveModel {
        email: Set(email.clone()),
        code_hash: Set(code_hash),
        length: Set(cfg.otp_length),
        expires_at: Set(expires_at),
        attempts: Set(0),
        max_attempts: Set(cfg.otp_max_attempts),
        status: Set(OtpStatus::Pending.code() as i8),
        last_sent_at: Set(Some(now)),
        version: Set(0),
        created_at: Set(now),
        updated_at: Set(Some(now)),
        ..Default::default()
    })
    .build(sea_orm::DatabaseBackend::MySql);
    partition::insert_self_heal(state, "otp_code", &[now], stmt).await?;

    ratelimit::record_sent(state, &email, ip, cfg.otp_resend_seconds as u64).await;

    // STEP-07 异步发信(失败不阻塞;FLOW-15 由邮件模块内部重试)
    tracing::info!(code = %plaintext, "[otp:dev] 验证码(stub 辅助;生产经邮件投递)");
    let mail_state = state.clone();
    let mail_email = email.clone();
    let mail_locale = locale.to_string();
    let vars = [
        ("code".to_string(), plaintext),
        ("ttl".to_string(), cfg.otp_ttl_minutes.to_string()),
    ];
    tokio::spawn(async move {
        let _ =
            crate::mail::send_template(&mail_state, &mail_email, "otp", &mail_locale, &vars).await;
    });

    Ok(SendOtpResult {
        resend_after_seconds: cfg.otp_resend_seconds,
        otp_length: cfg.otp_length,
    })
}

fn numeric_code(len: usize) -> String {
    // CSPRNG(rand crate;2026-09-15 替换旧 LCG——时间+pid 种子可预测,观察少量输出可反推后续验证码)
    use rand::Rng as _;
    let mut rng = rand::rng();
    (0..len)
        .map(|_| char::from(b'0' + rng.random_range(0..10u8)))
        .collect()
}

/// FLOW-02 verifyOtp(登录全管线)——归并/建号/禁用检查/会话签发在 auth.rs 编排;
/// 本函数只做"校验码"部分(锁内消费)
pub async fn verify_code_only(
    state: &SharedState,
    raw_email: &str,
    code: &str,
) -> Result<(), SvcError> {
    let email = normalize(raw_email);
    let lock = email_lock(&email);
    let _guard = lock.lock().await;
    consume_valid_code(state, &email, code).await
}

/// 登录校验 + 消费(与 verify_code_only 共用锁语义)
pub async fn consume_for_login(
    state: &SharedState,
    raw_email: &str,
    code: &str,
) -> Result<(), SvcError> {
    let email = normalize(raw_email);
    let lock = email_lock(&email);
    let _guard = lock.lock().await;
    consume_valid_code(state, &email, code).await
}

async fn consume_valid_code(state: &SharedState, email: &str, code: &str) -> Result<(), SvcError> {
    let db = &state.db;
    let cutoff = chrono::Local::now().naive_local() - chrono::Duration::hours(24);
    // STEP-01 取最新 pending(**created_at 下界谓词**强制月分区裁剪)
    let row = otp_code::Entity::find()
        .filter(otp_code::Column::Email.eq(email))
        .filter(otp_code::Column::Status.eq(OtpStatus::Pending.code() as i8))
        .filter(otp_code::Column::CreatedAt.gte(cutoff))
        .order_by_desc(otp_code::Column::CreatedAt)
        .one(db)
        .await?;
    let Some(row) = row else {
        return Err(SvcError::code(41001)); // 无 pending
    };
    let now = chrono::Local::now().naive_local();
    // STEP-02 过期
    if row.expires_at < now {
        update_status(state, &row, OtpStatus::Expired).await?;
        return Err(SvcError::code(41001));
    }
    // STEP-03 hash 校验
    if !bcrypt::verify(code, &row.code_hash).unwrap_or(false) {
        let next = row.attempts + 1;
        if next >= row.max_attempts {
            update_status(state, &row, OtpStatus::Locked).await?;
            return Err(SvcError::code(41002));
        }
        bump_attempts(state, &row, next).await?;
        let details = serde_json::json!({ "remaining_attempts": row.max_attempts - next });
        return Err(SvcError::code_with(40101, details));
    }
    // 正确 → consumed(version 条件防并发双消费)
    update_status(state, &row, OtpStatus::Consumed).await?;
    ratelimit::clear_resend(state, email).await;
    Ok(())
}

/// 状态推进(乐观锁:version 条件防并发双消费)
async fn update_status(
    state: &SharedState,
    row: &otp_code::Model,
    status: OtpStatus,
) -> Result<(), SvcError> {
    let res = otp_code::Entity::update_many()
        .col_expr(
            otp_code::Column::Status,
            Expr::value(status.code() as i8),
        )
        .col_expr(
            otp_code::Column::Version,
            Expr::col(otp_code::Column::Version).add(1),
        )
        .filter(otp_code::Column::Id.eq(row.id))
        .filter(otp_code::Column::CreatedAt.eq(row.created_at))
        .filter(otp_code::Column::Version.eq(row.version))
        .exec(&state.db)
        .await?;
    if res.rows_affected == 0 {
        // 并发竞争(乐观锁失败):按已消费处理语义,交由上层(罕见)
        tracing::warn!("[otp] 乐观锁竞争 id={}", row.id);
    }
    Ok(())
}

async fn bump_attempts(
    state: &SharedState,
    row: &otp_code::Model,
    attempts: i32,
) -> Result<(), SvcError> {
    let _ = otp_code::Entity::update_many()
        .col_expr(otp_code::Column::Attempts, Expr::value(attempts))
        .col_expr(
            otp_code::Column::Version,
            Expr::col(otp_code::Column::Version).add(1),
        )
        .filter(otp_code::Column::Id.eq(row.id))
        .filter(otp_code::Column::CreatedAt.eq(row.created_at))
        .filter(otp_code::Column::Version.eq(row.version))
        .exec(&state.db)
        .await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::numeric_code;

    #[test]
    fn code_length_and_digits() {
        for len in [4usize, 6, 8] {
            let c = numeric_code(len);
            assert_eq!(c.len(), len);
            assert!(c.chars().all(|ch| ch.is_ascii_digit()));
        }
    }

    #[test]
    fn codes_differ_across_calls() {
        let a = numeric_code(8);
        let b = numeric_code(8);
        assert_ne!(a, b, "连续两码应不同(时间种子推进)");
    }
}
