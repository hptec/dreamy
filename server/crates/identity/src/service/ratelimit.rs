//! OTP 频控(Redis,键格式与 Java OtpRateLimiter 一字不差):
//! - `otp:resend:{email}`(值"1",TTL=resend_seconds)→ 命中 42901,details.remaining_resend_seconds
//! - `otp:count:email:{email}:h`(1h)/ `:d`(1d)/ `otp:count:ip:{ip}:h`(1h)
//! - 配额:email 5/h、5/d;IP 20/h(auth_config 可覆盖 email 上限)

use common::state::SharedState;
use redis::AsyncCommands;

use crate::service::SvcError;

pub struct RateDecision {
    pub permitted: bool,
    pub error_code: Option<i32>,
    pub remaining_resend_seconds: Option<u64>,
}

pub struct OtpQuota {
    pub email_hourly: u32,
    pub email_daily: u32,
    pub ip_hourly: u32,
}

impl Default for OtpQuota {
    fn default() -> Self {
        OtpQuota {
            email_hourly: 5,
            email_daily: 5,
            ip_hourly: 20,
        }
    }
}

fn resend_key(email: &str) -> String {
    format!("otp:resend:{email}")
}

pub async fn check_resend(state: &SharedState, email: &str, _resend_seconds: u64) -> RateDecision {
    let Some(mut conn) = state.redis.clone() else {
        return RateDecision {
            permitted: true,
            error_code: None,
            remaining_resend_seconds: None,
        };
    };
    match conn.ttl::<_, i64>(resend_key(email)).await {
        Ok(ttl) if ttl > 0 => RateDecision {
            permitted: false,
            error_code: Some(42901),
            remaining_resend_seconds: Some(ttl as u64),
        },
        _ => RateDecision {
            permitted: true,
            error_code: None,
            remaining_resend_seconds: None,
        },
    }
}

pub async fn check_send_quota(
    state: &SharedState,
    email: &str,
    ip: &str,
    quota: &OtpQuota,
) -> RateDecision {
    let Some(mut conn) = state.redis.clone() else {
        return RateDecision {
            permitted: true,
            error_code: None,
            remaining_resend_seconds: None,
        };
    };
    let eh: i64 = conn
        .get(format!("otp:count:email:{email}:h"))
        .await
        .unwrap_or(0);
    if eh >= quota.email_hourly as i64 {
        return RateDecision {
            permitted: false,
            error_code: Some(42902),
            remaining_resend_seconds: None,
        };
    }
    let ed: i64 = conn
        .get(format!("otp:count:email:{email}:d"))
        .await
        .unwrap_or(0);
    if ed >= quota.email_daily as i64 {
        return RateDecision {
            permitted: false,
            error_code: Some(42902),
            remaining_resend_seconds: None,
        };
    }
    let ih: i64 = conn.get(format!("otp:count:ip:{ip}:h")).await.unwrap_or(0);
    if ih >= quota.ip_hourly as i64 {
        return RateDecision {
            permitted: false,
            error_code: Some(42902),
            remaining_resend_seconds: None,
        };
    }
    RateDecision {
        permitted: true,
        error_code: None,
        remaining_resend_seconds: None,
    }
}

/// 发码成功后计数(INCR + 首次设 TTL)+ 重发冷却键
pub async fn record_sent(state: &SharedState, email: &str, ip: &str, resend_seconds: u64) {
    let Some(mut conn) = state.redis.clone() else {
        return;
    };
    let _: Result<i64, _> = conn.set_ex(resend_key(email), "1", resend_seconds).await;
    for (key, ttl) in [
        (format!("otp:count:email:{email}:h"), 3600),
        (format!("otp:count:email:{email}:d"), 86400),
        (format!("otp:count:ip:{ip}:h"), 3600),
    ] {
        let count: i64 = conn.incr(&key, 1).await.unwrap_or(1);
        if count == 1 {
            let _: Result<(), _> = conn.expire(&key, ttl).await;
        }
    }
}

/// 消费成功后清除重发冷却(允许立即重发下一枚)
pub async fn clear_resend(state: &SharedState, email: &str) {
    if let Some(mut conn) = state.redis.clone() {
        let _: Result<(), _> = conn.del(resend_key(email)).await;
    }
}

/// 便捷:频控拒绝 → SvcError 带 details(对齐 Java 42901 details.remaining_resend_seconds)
pub fn to_error(decision: RateDecision) -> Option<SvcError> {
    match decision.error_code {
        Some(42901) => {
            let mut details = serde_json::Map::new();
            if let Some(remain) = decision.remaining_resend_seconds {
                details.insert("remaining_resend_seconds".into(), remain.into());
            }
            Some(SvcError::code_with(
                42901,
                serde_json::Value::Object(details),
            ))
        }
        Some(42902) => Some(SvcError::code(42902)),
        _ => None,
    }
}

// ══════════ verify OTP 防御(V7 IP 频控 / L2 双键递进退避 / L5 攻击告警) ══════════

fn verify_ip_key(ip: &str) -> String {
    format!("otp:verify:ip:{ip}:m")
}

fn backoff_email_key(email: &str) -> String {
    format!("otp:backoff:email:{email}")
}

fn backoff_ip_key(ip: &str) -> String {
    format!("otp:backoff:ip:{ip}")
}

fn mask_email(email: &str) -> String {
    match email.split_once('@') {
        Some((local, domain)) => {
            let head = local.chars().take(1).collect::<String>();
            format!("{head}***@{domain}")
        }
        None => "[REDACTED]".into(),
    }
}

/// V7 verify IP 频控:60s 固定窗口 INCR;Some(剩余秒)=拒绝。
/// Redis 缺席/故障 fail-open(对齐既有频控语义:限流是增强,不因基础设施故障放大)
pub async fn check_verify_rate(
    state: &SharedState,
    ip: &str,
    limit_per_minute: u64,
) -> Option<u64> {
    let Some(mut conn) = state.redis.clone() else {
        return None;
    };
    let key = verify_ip_key(ip);
    let count: i64 = match conn.incr(&key, 1).await {
        Ok(c) => c,
        Err(_) => return None,
    };
    if count == 1 {
        let _: Result<(), _> = conn.expire(&key, 60).await;
    }
    if count > limit_per_minute as i64 {
        let remain: i64 = conn.ttl(&key).await.unwrap_or(60);
        Some(remain.max(1) as u64)
    } else {
        None
    }
}

/// L2 退避检查:键存活(TTL>0)即冷却中;两键 pipeline 一次往返,取较长冷却
pub async fn check_backoff(state: &SharedState, email: &str, ip: &str) -> Option<u64> {
    let Some(mut conn) = state.redis.clone() else {
        return None;
    };
    let (email_ttl, ip_ttl): (i64, i64) = redis::pipe()
        .atomic()
        .ttl(backoff_email_key(email))
        .ttl(backoff_ip_key(ip))
        .query_async(&mut conn)
        .await
        .unwrap_or((-2, -2));
    let worst = [email_ttl, ip_ttl].into_iter().filter(|t| *t > 0).max()?;
    Some(worst as u64)
}

/// L2 失败计数 + 档位冷却:连续失败 1-9 次→60s、10-19→300s、≥20→1800s(TTL 即冷却期,
/// 停手到期自然归零)。越过告警阈值瞬间触发 L5(双键 SET NX 1h 去重,防同源反复轰炸)
pub async fn record_verify_failure(
    state: &SharedState,
    email: &str,
    ip: &str,
    alert_threshold: i64,
    alert_email: Option<&str>,
) {
    let Some(mut conn) = state.redis.clone() else {
        return;
    };
    for key in [backoff_email_key(email), backoff_ip_key(ip)] {
        let count: i64 = match conn.incr(&key, 1).await {
            Ok(c) => c,
            Err(_) => continue,
        };
        let tier: i64 = if count < 10 {
            60
        } else if count < 20 {
            300
        } else {
            1800
        };
        let _: Result<(), _> = conn.expire(&key, tier).await;
        if count == alert_threshold {
            notify_attack(&mut conn, state, email, ip, count, alert_email).await;
        }
    }
}

/// 成功登录后清 email 维度退避(IP 键保留:共享出口下受害者成功不代表攻击者该被豁免)
pub async fn clear_backoff(state: &SharedState, email: &str) {
    if let Some(mut conn) = state.redis.clone() {
        let _: Result<(), _> = conn.del(backoff_email_key(email)).await;
    }
}

async fn notify_attack(
    conn: &mut redis::aio::ConnectionManager,
    state: &SharedState,
    email: &str,
    ip: &str,
    count: i64,
    alert_email: Option<&str>,
) {
    tracing::warn!(
        email = %mask_email(email),
        ip = %ip,
        count = count,
        "[security] verify OTP 连续失败达告警阈值,疑似持续攻击"
    );
    let Some(to) = alert_email.filter(|e| !e.is_empty()) else {
        return;
    };
    // 双键 SET NX:email 或 ip 任一首次越过阈值即发,1h 内同源去重
    let claimed: Result<(Option<String>, Option<String>), _> = redis::pipe()
        .atomic()
        .cmd("SET")
        .arg(format!("otp:alert:dedup:email:{email}"))
        .arg("1")
        .arg("EX")
        .arg(3600)
        .arg("NX")
        .cmd("SET")
        .arg(format!("otp:alert:dedup:ip:{ip}"))
        .arg("1")
        .arg("EX")
        .arg(3600)
        .arg("NX")
        .query_async(conn)
        .await;
    let should_send = matches!(claimed, Ok((Some(_), _))) || matches!(claimed, Ok((_, Some(_))));
    if !should_send {
        return;
    }
    let mail_state = state.clone();
    let to = to.to_string();
    let vars = [
        ("email".to_string(), email.to_string()),
        ("ip".to_string(), ip.to_string()),
        ("count".to_string(), count.to_string()),
    ];
    tokio::spawn(async move {
        let _ = crate::mail::send_template(&mail_state, &to, "security_alert", "zh", &vars).await;
    });
}
