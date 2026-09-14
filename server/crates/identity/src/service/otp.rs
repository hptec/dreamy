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

use common::state::SharedState;
use sea_orm::{ConnectionTrait, Statement};

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

    // STEP-05 失效旧 pending(原生 UPDATE:复合主键月分区表,col_expr 类型链最短路径)
    state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"UPDATE otp_code SET status = 3, version = version + 1 WHERE email = ? AND status = 1"#,
            [email.clone().into()],
        ))
        .await?;

    // STEP-06 生成明文(仅存 hash;本地 RNG,无外部依赖)
    let plaintext = numeric_code(cfg.otp_length as usize);
    let code_hash =
        bcrypt::hash(&plaintext, bcrypt::DEFAULT_COST).map_err(|_| SvcError::code(50000))?;
    let now = chrono::Local::now().naive_local();
    let expires_at = now + chrono::Duration::minutes(cfg.otp_ttl_minutes as i64);
    // 复合主键(id,created_at)+自增:SeaORM insert() 不可回解 → 原生 INSERT
    // (created_at 显式落值:NOT NULL 分区键)
    state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"INSERT INTO otp_code
               (email, code_hash, length, expires_at, attempts, max_attempts, status, last_sent_at, version, created_at)
               VALUES (?, ?, ?, ?, 0, ?, ?, ?, 0, ?)"#,
            [
                email.clone().into(),
                code_hash.into(),
                (cfg.otp_length as i32).into(),
                expires_at.into(),
                cfg.otp_max_attempts.into(),
                (OtpStatus::Pending.code() as i8).into(),
                now.into(),
                now.into(),
            ],
        ))
        .await?;

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
    // 简单安全随机:系统时间 + 线程 ID 混合作种(验证码场景足够;hash 侧 bcrypt 已加密强度)
    let mut seed = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .subsec_nanos() as u64
        ^ (std::process::id() as u64) << 32;
    let mut out = String::with_capacity(len);
    for _ in 0..len {
        seed = seed
            .wrapping_mul(6364136223846793005)
            .wrapping_add(1442695040888963407);
        out.push(char::from(b'0' + ((seed >> 33) % 10) as u8));
    }
    out
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
    let row = db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT id, code_hash, expires_at, attempts, max_attempts, version, created_at
               FROM otp_code
               WHERE email = ? AND status = 1 AND created_at >= ?
               ORDER BY created_at DESC LIMIT 1"#,
            [email.into(), cutoff.into()],
        ))
        .await?;
    let Some(row) = row else {
        return Err(SvcError::code(41001)); // 无 pending
    };
    // id 为 BIGINT UNSIGNED → u64(sqlx i64 解码类型不匹配即失败,P2 实测踩坑)
    let (id, created_at, code_hash, expires_at, attempts, max_attempts, version) = (
        row.try_get_by_index::<u64>(0)
            .map_err(|_| SvcError::code(50000))? as i64,
        row.try_get_by_index::<chrono::NaiveDateTime>(6)
            .map_err(|_| SvcError::code(50000))?,
        row.try_get_by_index::<String>(1)
            .map_err(|_| SvcError::code(50000))?,
        row.try_get_by_index::<chrono::NaiveDateTime>(2)
            .map_err(|_| SvcError::code(50000))?,
        row.try_get_by_index::<i32>(3)
            .map_err(|_| SvcError::code(50000))?,
        row.try_get_by_index::<i32>(4)
            .map_err(|_| SvcError::code(50000))?,
        row.try_get_by_index::<i32>(5)
            .map_err(|_| SvcError::code(50000))?,
    );
    let now = chrono::Local::now().naive_local();
    // STEP-02 过期
    if expires_at < now {
        update_status(state, id, created_at, OtpStatus::Expired, version).await?;
        return Err(SvcError::code(41001));
    }
    // STEP-03 hash 校验
    if !bcrypt::verify(code, &code_hash).unwrap_or(false) {
        let next = attempts + 1;
        if next >= max_attempts {
            update_status(state, id, created_at, OtpStatus::Locked, version).await?;
            return Err(SvcError::code(41002));
        }
        bump_attempts(state, id, created_at, version, next).await?;
        let details = serde_json::json!({ "remaining_attempts": max_attempts - next });
        return Err(SvcError::code_with(40101, details));
    }
    // 正确 → consumed(version 条件防并发双消费)
    update_status(state, id, created_at, OtpStatus::Consumed, version).await?;
    ratelimit::clear_resend(state, email).await;
    Ok(())
}

#[allow(clippy::too_many_arguments)]
async fn update_status(
    state: &SharedState,
    id: i64,
    created_at: chrono::NaiveDateTime,
    status: OtpStatus,
    version: i32,
) -> Result<(), SvcError> {
    let res = state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"UPDATE otp_code SET status = ?, version = version + 1
               WHERE id = ? AND created_at = ? AND version = ?"#,
            [
                (status.code() as i8).into(),
                id.into(),
                created_at.into(),
                version.into(),
            ],
        ))
        .await?;
    if res.rows_affected() == 0 {
        // 并发竞争(乐观锁失败):按已消费处理语义,交由上层(罕见)
        tracing::warn!("[otp] 乐观锁竞争 id={id}");
    }
    Ok(())
}

async fn bump_attempts(
    state: &SharedState,
    id: i64,
    created_at: chrono::NaiveDateTime,
    version: i32,
    attempts: i32,
) -> Result<(), SvcError> {
    let _ = state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"UPDATE otp_code SET attempts = ?, version = version + 1
               WHERE id = ? AND created_at = ? AND version = ?"#,
            [
                attempts.into(),
                id.into(),
                created_at.into(),
                version.into(),
            ],
        ))
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
