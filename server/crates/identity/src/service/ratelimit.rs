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

pub async fn check_resend(state: &SharedState, email: &str, resend_seconds: u64) -> RateDecision {
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
