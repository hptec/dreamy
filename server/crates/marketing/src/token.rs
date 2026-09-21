//! 退订 token(方案 A 邮件退订链接;逐条对齐 Java UnsubscribeTokenService)。
//!
//! 格式 5 段:`u1.<b64url(email)>.<b64url(expEpochMillis)>.<b64url(genEpochMillis)>.<b64url(sig)>`。
//! 签名输入 = "newsletter-unsubscribe\n" + 前 4 段原文(UTF-8),HMAC-SHA256,域分离防跨用途重放。
//! gen = 持久化 subscribed_at 的 epoch 毫秒(UTC,对齐 DATETIME(3)):复活后 subscribed_at 更新,
//! 旧 token 的 gen 落后 → SQL 代际谓词原子失效,防"旧链接退订新订阅"竞态。
//! 一切畸形/过期/签名不符统一 InvalidToken(不区分原因,防侧信道)。

use hmac::{Hmac, Mac};
use sha2::Sha256;

const PURPOSE: &str = "newsletter-unsubscribe";
const VERSION: &str = "u1";
/// 解析防御:token 总长上限
const MAX_TOKEN_LENGTH: usize = 1024;
const MAX_EMAIL_LENGTH: usize = 255;

type HmacSha256 = Hmac<Sha256>;

/// 统一无效 token(畸形/过期/签名不符不分原因)
#[derive(Debug, thiserror::Error)]
#[error("invalid unsubscribe token")]
pub struct InvalidToken;

fn sign(secret: &str, payload: &str) -> Vec<u8> {
    let mut mac = HmacSha256::new_from_slice(secret.as_bytes()).expect("hmac key");
    mac.update((PURPOSE.to_owned() + "\n").as_bytes());
    mac.update(payload.as_bytes());
    mac.finalize().into_bytes().to_vec()
}

fn b64(bytes: &[u8]) -> String {
    use base64::Engine;
    base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(bytes)
}

fn unb64(segment: &str) -> Result<Vec<u8>, InvalidToken> {
    use base64::Engine;
    base64::engine::general_purpose::URL_SAFE_NO_PAD
        .decode(segment)
        .map_err(|_| InvalidToken)
}

fn parse_digits(bytes: &[u8]) -> Result<i64, InvalidToken> {
    if bytes.is_empty() || bytes.len() > 19 {
        return Err(InvalidToken);
    }
    let mut value: i64 = 0;
    for b in bytes {
        if !b.is_ascii_digit() {
            return Err(InvalidToken);
        }
        value = value * 10 + (b - b'0') as i64;
    }
    Ok(value)
}

/// 生成退订 token(gen 取持久化 subscribed_at 的 epoch 毫秒)
pub fn generate(secret: &str, email: &str, gen_epoch_millis: i64, now_millis: i64, ttl_seconds: i64) -> String {
    let exp = now_millis + ttl_seconds * 1000;
    let seg_email = b64(email.as_bytes());
    let seg_exp = b64(exp.to_string().as_bytes());
    let seg_gen = b64(gen_epoch_millis.to_string().as_bytes());
    let payload = format!("{VERSION}.{seg_email}.{seg_exp}.{seg_gen}");
    format!("{payload}.{}", b64(&sign(secret, &payload)))
}

/// 解析结果(email, expEpochMillis, genEpochMillis)
#[derive(Debug, Clone, PartialEq)]
pub struct ParsedToken {
    pub email: String,
    pub exp_epoch_millis: i64,
    pub gen_epoch_millis: i64,
}

/// 解析并校验 token;任何问题一律 Err(InvalidToken)
pub fn parse(secret: &str, token: &str, now_millis: i64) -> Result<ParsedToken, InvalidToken> {
    if token.is_empty() || token.len() > MAX_TOKEN_LENGTH {
        return Err(InvalidToken);
    }
    let parts: Vec<&str> = token.split('.').collect();
    if parts.len() != 5 || parts[0] != VERSION {
        return Err(InvalidToken);
    }
    let email_bytes = unb64(parts[1])?;
    if email_bytes.is_empty() || email_bytes.len() > MAX_EMAIL_LENGTH {
        return Err(InvalidToken);
    }
    let exp = parse_digits(&unb64(parts[2])?)?;
    let gen = parse_digits(&unb64(parts[3])?)?;
    let sig = unb64(parts[4])?;
    let payload = format!("{}.{}.{}.{}", parts[0], parts[1], parts[2], parts[3]);
    // 常量时间比较(对齐 MessageDigest.isEqual)
    if sig.len() != sign(secret, &payload).len()
        || !constant_time_eq(&sig, &sign(secret, &payload))
    {
        return Err(InvalidToken);
    }
    if exp <= now_millis {
        return Err(InvalidToken);
    }
    Ok(ParsedToken {
        email: String::from_utf8(email_bytes).map_err(|_| InvalidToken)?,
        exp_epoch_millis: exp,
        gen_epoch_millis: gen,
    })
}

fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    a.iter()
        .zip(b.iter())
        .fold(0u8, |acc, (x, y)| acc | (x ^ y))
        == 0
}

/// DB 口径 LocalDateTime(UTC)→ epoch 毫秒(对齐 toEpochMillis;DATETIME(3) 毫秒精度)
pub fn db_time_to_epoch_millis(t: chrono::NaiveDateTime) -> i64 {
    t.and_utc().timestamp_millis()
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::NaiveDate;

    const SECRET: &str = "test-secret-0123456789abcdef0123456789abcdef";

    #[test]
    fn generate_parse_roundtrip() {
        let now = 1_700_000_000_000i64;
        let tok = generate(SECRET, "user@example.com", 1_699_999_000_000, now, 3600);
        let parsed = parse(SECRET, &tok, now).unwrap();
        assert_eq!(parsed.email, "user@example.com");
        assert_eq!(parsed.exp_epoch_millis, now + 3600 * 1000);
        assert_eq!(parsed.gen_epoch_millis, 1_699_999_000_000);
    }

    #[test]
    fn tampered_email_rejected() {
        let now = 1_700_000_000_000i64;
        let tok = generate(SECRET, "user@example.com", 1000, now, 3600);
        let mut parts: Vec<String> = tok.split('.').map(String::from).collect();
        parts[1] = b64(b"attacker@evil.com");
        let tampered = parts.join(".");
        assert!(parse(SECRET, &tampered, now).is_err(), "签名必须不符");
    }

    #[test]
    fn expired_rejected() {
        let now = 1_700_000_000_000i64;
        let tok = generate(SECRET, "a@b.co", 1000, now, 3600);
        assert!(parse(SECRET, &tok, now + 3600 * 1000 + 1).is_err());
        assert!(parse(SECRET, &tok, now + 3600 * 1000 - 1).is_ok(), "边界:exp 前一毫秒仍有效");
        assert!(parse(SECRET, &tok, now + 3600 * 1000).is_err(), "边界:exp 当刻已过期(exp<=now)");
    }

    #[test]
    fn malformed_all_rejected_same_error() {
        let now = 1_700_000_000_000i64;
        for bad in ["", "u1", "u1.a.b.c", "u2.a.b.c.d", "u1.!!!.bbb.ccc.ddd"] {
            assert!(parse(SECRET, bad, now).is_err(), "{bad}");
        }
        // 超长 token
        let long = "u1.aaaa.".to_string() + &"a".repeat(MAX_TOKEN_LENGTH);
        assert!(parse(SECRET, &long, now).is_err());
    }

    #[test]
    fn cross_purpose_secret_isolated() {
        // 域分离:同密钥下不同 PURPOSE 不产出相同签名(此处仅验证 PURPOSE 前缀生效——
        // 用另一个前缀手工签名应无法通过 parse)
        let now = 1_700_000_000_000i64;
        let payload = format!("u1.{}.{}.{}", b64(b"a@b.co"), b64(now.to_string().as_bytes()), b64(b"1000"));
        // 错误域签名
        let mut mac = HmacSha256::new_from_slice(SECRET.as_bytes()).unwrap();
        mac.update(b"other-purpose\n");
        mac.update(payload.as_bytes());
        let tok = format!("{payload}.{}", b64(&mac.finalize().into_bytes()));
        assert!(parse(SECRET, &tok, now).is_err());
    }

    #[test]
    fn db_time_conversion_utc() {
        let t = NaiveDate::from_ymd_opt(2026, 9, 22).unwrap().and_hms_milli_opt(12, 0, 0, 123).unwrap();
        assert_eq!(db_time_to_epoch_millis(t) % 1000, 123, "毫秒精度保留");
    }
}
