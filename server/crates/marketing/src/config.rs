//! marketing 域配置(对齐 Java MarketingProperties,DEC-MKT-3):
//! - 退订 token HMAC 密钥 NEWSLETTER_UNSUBSCRIBE_SECRET(启动 fail-fast:非空且 UTF-8 ≥32 字节);
//! - token 有效期 unsubscribe_token_ttl_seconds 缺省 30 天,合法区间 (0, 315360000]。

#[derive(Clone, Debug)]
pub struct MarketingConfig {
    pub unsubscribe_secret: String,
    pub unsubscribe_token_ttl_seconds: i64,
}

impl MarketingConfig {
    pub fn from_env() -> Result<Self, String> {
        let cfg = MarketingConfig {
            unsubscribe_secret: std::env::var("NEWSLETTER_UNSUBSCRIBE_SECRET").unwrap_or_default(),
            unsubscribe_token_ttl_seconds: std::env::var("MARKETING_UNSUBSCRIBE_TOKEN_TTL_SECONDS")
                .ok()
                .and_then(|v| v.parse().ok())
                .unwrap_or(30 * 24 * 3600),
        };
        cfg.validate()?;
        Ok(cfg)
    }

    /// 启动期 fail-fast(对齐 Java @PostConstruct validateUnsubscribeSecret):
    /// 拒绝 blank(纯空白不可)且按 UTF-8 字节数 ≥32。
    fn validate(&self) -> Result<(), String> {
        if self.unsubscribe_secret.trim().is_empty()
            || self.unsubscribe_secret.as_bytes().len() < 32
        {
            return Err(
                "NEWSLETTER_UNSUBSCRIBE_SECRET must be configured with at least 32 UTF-8 bytes (non-blank, high entropy)"
                    .into(),
            );
        }
        if self.unsubscribe_token_ttl_seconds <= 0
            || self.unsubscribe_token_ttl_seconds > 10 * 365 * 24 * 3600
        {
            return Err(
                "MARKETING_UNSUBSCRIBE_TOKEN_TTL_SECONDS must be within (0, 315360000]".into(),
            );
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_blank_secret() {
        let cfg = MarketingConfig {
            unsubscribe_secret: "                                ".into(),
            unsubscribe_token_ttl_seconds: 100,
        };
        assert!(cfg.validate().is_err());
    }

    #[test]
    fn rejects_short_secret() {
        let cfg = MarketingConfig {
            unsubscribe_secret: "short".into(),
            unsubscribe_token_ttl_seconds: 100,
        };
        assert!(cfg.validate().is_err());
    }

    #[test]
    fn accepts_32byte_secret_and_rejects_bad_ttl() {
        let ok = MarketingConfig {
            unsubscribe_secret: "a".repeat(32),
            unsubscribe_token_ttl_seconds: 30 * 24 * 3600,
        };
        assert!(ok.validate().is_ok());
        let bad_ttl = MarketingConfig {
            unsubscribe_secret: "a".repeat(32),
            unsubscribe_token_ttl_seconds: 0,
        };
        assert!(bad_ttl.validate().is_err());
        let over_ttl = MarketingConfig {
            unsubscribe_secret: "a".repeat(32),
            unsubscribe_token_ttl_seconds: 10 * 365 * 24 * 3600 + 1,
        };
        assert!(over_ttl.validate().is_err());
    }
}
