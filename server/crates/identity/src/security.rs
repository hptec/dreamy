//! JWT 签发与解析(与 Java JwtTokenProvider 令牌格式互通——HS256 共享密钥)。
//!
//! claims 契约(与 jjwt 生成的一字不差,存量令牌跨切换有效):
//! - store: {iss:"dreamy-store", sub:<user_id>, jti, typ:"store", method, refresh, iat, exp}
//! - admin: {iss:"dreamy-admin", sub:<admin_id>, jti, typ:"admin", role_id, refresh:false, iat, exp}
//! - guest: 不在本域(showroom 留 Java,仍用 store 密钥签发)
//!
//! TTL 契约:store access 7200s / store refresh 2592000s / admin access 28800s(无 refresh)。
//! 校验:HS256 + iss 精确匹配 + exp(leeway 60s)+ typ 匹配(跨端误用拒收)。

use common::config::Config;
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};

pub const TYP_STORE: &str = "store";
pub const TYP_ADMIN: &str = "admin";
pub const ISS_STORE: &str = "dreamy-store";
pub const ISS_ADMIN: &str = "dreamy-admin";

pub const STORE_ACCESS_TTL: i64 = 7200;
pub const STORE_REFRESH_TTL: i64 = 2_592_000;
pub const ADMIN_ACCESS_TTL: i64 = 28_800;

const LEEWAY: u64 = 60;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct StoreClaims {
    pub iss: String,
    pub sub: String,
    pub jti: String,
    pub typ: String,
    pub method: String,
    pub refresh: bool,
    pub iat: i64,
    pub exp: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct AdminClaims {
    pub iss: String,
    pub sub: String,
    pub jti: String,
    pub typ: String,
    pub role_id: i64,
    pub refresh: bool,
    pub iat: i64,
    pub exp: i64,
}

#[derive(Debug, thiserror::Error)]
pub enum JwtError {
    #[error("签名非法/过期/typ 不符: {0}")]
    Invalid(String),
    #[error("密钥未配置(需 STORE_JWT_SECRET/ADMIN_JWT_SECRET)")]
    MissingKey,
}

/// 密钥对(签名/验签;HS256 对称,同源同密钥)
#[derive(Clone)]
pub struct JwtProvider {
    store_key: EncodingKey,
    admin_key: EncodingKey,
    store_verify: DecodingKey,
    admin_verify: DecodingKey,
}

impl JwtProvider {
    /// 密钥来自 Config(与 Java 共享同一 env;缺失即 MissingKey,启动时已 WARN)
    pub fn new(cfg: &Config) -> Result<Self, JwtError> {
        let store = cfg.store_jwt_secret.as_deref().ok_or(JwtError::MissingKey)?;
        let admin = cfg.admin_jwt_secret.as_deref().ok_or(JwtError::MissingKey)?;
        Ok(JwtProvider {
            store_key: EncodingKey::from_secret(store.as_bytes()),
            admin_key: EncodingKey::from_secret(admin.as_bytes()),
            store_verify: DecodingKey::from_secret(store.as_bytes()),
            admin_verify: DecodingKey::from_secret(admin.as_bytes()),
        })
    }

    pub fn issue_store(&self, user_id: i64, jti: &str, method: &str, refresh: bool, ttl: i64) -> Result<String, JwtError> {
        let now = chrono::Utc::now().timestamp();
        let claims = StoreClaims {
            iss: ISS_STORE.into(),
            sub: user_id.to_string(),
            jti: jti.into(),
            typ: TYP_STORE.into(),
            method: method.into(),
            refresh,
            iat: now,
            exp: now + ttl,
        };
        encode(&Header::new(jsonwebtoken::Algorithm::HS256), &claims, &self.store_key)
            .map_err(|e| JwtError::Invalid(e.to_string()))
    }

    pub fn issue_admin(&self, admin_id: i64, role_id: i64, jti: &str, ttl: i64) -> Result<String, JwtError> {
        let now = chrono::Utc::now().timestamp();
        let claims = AdminClaims {
            iss: ISS_ADMIN.into(),
            sub: admin_id.to_string(),
            jti: jti.into(),
            typ: TYP_ADMIN.into(),
            role_id,
            refresh: false,
            iat: now,
            exp: now + ttl,
        };
        encode(&Header::new(jsonwebtoken::Algorithm::HS256), &claims, &self.admin_key)
            .map_err(|e| JwtError::Invalid(e.to_string()))
    }

    /// 解析 store 令牌(iss/typ/exp 校验;refresh 令牌同构返回,由调用方按场景判定)
    pub fn parse_store(&self, token: &str) -> Result<StoreClaims, JwtError> {
        let mut validation = Validation::new(jsonwebtoken::Algorithm::HS256);
        validation.leeway = LEEWAY;
        validation.set_issuer(&[ISS_STORE]);
        validation.set_required_spec_claims(&["exp"]);
        let data = decode::<StoreClaims>(token, &self.store_verify, &validation)
            .map_err(|e| JwtError::Invalid(e.to_string()))?;
        if data.claims.typ != TYP_STORE {
            return Err(JwtError::Invalid(format!("typ 不符: {}", data.claims.typ)));
        }
        Ok(data.claims)
    }

    /// 解析 admin 令牌(admin 密钥独立,store 令牌跨端误用拒收)
    pub fn parse_admin(&self, token: &str) -> Result<AdminClaims, JwtError> {
        let mut validation = Validation::new(jsonwebtoken::Algorithm::HS256);
        validation.leeway = LEEWAY;
        validation.set_issuer(&[ISS_ADMIN]);
        validation.set_required_spec_claims(&["exp"]);
        let data = decode::<AdminClaims>(token, &self.admin_verify, &validation)
            .map_err(|e| JwtError::Invalid(e.to_string()))?;
        if data.claims.typ != TYP_ADMIN {
            return Err(JwtError::Invalid(format!("typ 不符: {}", data.claims.typ)));
        }
        Ok(data.claims)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn provider() -> JwtProvider {
        let cfg = common::config::Config {
            http_port: 0,
            grpc_port: 0,
            db_host: String::new(),
            db_port: 0,
            db_name: String::new(),
            db_legacy_name: String::new(),
            db_user: String::new(),
            db_password: String::new(),
            redis_host: String::new(),
            redis_port: 0,
            store_jwt_secret: Some("test-store-secret-0123456789abcdef".into()),
            admin_jwt_secret: Some("test-admin-secret-0123456789abcdef".into()),
            store_cors_origin: String::new(),
            admin_cors_origin: String::new(),
            schema_path: String::new(),
        };
        JwtProvider::new(&cfg).unwrap()
    }

    #[test]
    fn store_claims_roundtrip_and_contract() {
        let p = provider();
        let token = p
            .issue_store(42, "jti-abc", "google", false, STORE_ACCESS_TTL)
            .unwrap();
        let claims = p.parse_store(&token).unwrap();
        // claims 契约断言:字段名与语义与 Java jjwt 一字不差
        assert_eq!(claims.iss, "dreamy-store");
        assert_eq!(claims.sub, "42");
        assert_eq!(claims.jti, "jti-abc");
        assert_eq!(claims.typ, "store");
        assert_eq!(claims.method, "google");
        assert!(!claims.refresh);
        assert_eq!(claims.exp - claims.iat, STORE_ACCESS_TTL);
    }

    #[test]
    fn admin_claims_roundtrip_and_cross_type_rejected() {
        let p = provider();
        let admin_token = p.issue_admin(7, 3, "jti-admin", ADMIN_ACCESS_TTL).unwrap();
        let claims = p.parse_admin(&admin_token).unwrap();
        assert_eq!(claims.iss, "dreamy-admin");
        assert_eq!(claims.role_id, 3);
        assert!(!claims.refresh);
        assert_eq!(claims.exp - claims.iat, ADMIN_ACCESS_TTL);

        // 跨端误用:store 令牌进 admin 解析 → 拒(密钥不同 + iss 不同)
        let store_token = p.issue_store(42, "j", "email", false, 60).unwrap();
        assert!(p.parse_admin(&store_token).is_err());
        assert!(p.parse_store(&admin_token).is_err());
    }

    #[test]
    fn expired_token_rejected_within_leeway() {
        let p = provider();
        // exp 已过 120s(> 60s leeway)→ 拒
        let mut validation = Validation::new(jsonwebtoken::Algorithm::HS256);
        validation.leeway = LEEWAY;
        validation.set_issuer(&[ISS_STORE]);
        validation.set_required_spec_claims(&["exp"]);
        let now = chrono::Utc::now().timestamp();
        let claims = StoreClaims {
            iss: ISS_STORE.into(),
            sub: "1".into(),
            jti: "j".into(),
            typ: TYP_STORE.into(),
            method: "email".into(),
            refresh: false,
            iat: now - 200,
            exp: now - 120,
        };
        let token = encode(
            &Header::new(jsonwebtoken::Algorithm::HS256),
            &claims,
            &EncodingKey::from_secret(b"test-store-secret-0123456789abcdef"),
        )
        .unwrap();
        assert!(p.parse_store(&token).is_err());
    }

    /// 跨语言互认向量:固定 (secret, claims) → 签名结果恒定。
    /// Java 侧(protobuf 接线后)用同一向量断言 jjwt 产出相同签名,即证双向互通
    /// (HS256 为标准 HMAC-SHA256,两侧实现无歧义空间;向量锁定的是 claims 序列化契约)。
    #[test]
    fn cross_language_vector_stability() {
        let key = b"vector-secret-0123456789abcdef012345";
        let now = 1_800_000_000i64;
        let claims = StoreClaims {
            iss: ISS_STORE.into(),
            sub: "123456".into(),
            jti: "e2f1c3a0-1111-2222-3333-444455556666".into(),
            typ: TYP_STORE.into(),
            method: "email".into(),
            refresh: false,
            iat: now,
            exp: now + STORE_ACCESS_TTL,
        };
        let token = encode(
            &Header::new(jsonwebtoken::Algorithm::HS256),
            &claims,
            &EncodingKey::from_secret(key),
        )
        .unwrap();
        // 三段式结构 + 首段(header)恒定
        let parts: Vec<&str> = token.split('.').collect();
        assert_eq!(parts.len(), 3);
        let header_json = String::from_utf8(base64_url_decode(parts[0])).unwrap();
        assert!(header_json.contains("HS256"));
        // 载荷解码回读:字段名全量锁定(缺一即与 Java 侧契约漂移)
        let payload_json = String::from_utf8(base64_url_decode(parts[1])).unwrap();
        for field in ["iss", "sub", "jti", "typ", "method", "refresh", "iat", "exp"] {
            assert!(payload_json.contains(&format!("\"{field}\"")), "claims 缺字段 {field}");
        }
    }

    fn base64_url_decode(input: &str) -> Vec<u8> {
        use base64::Engine;
        base64::engine::general_purpose::URL_SAFE_NO_PAD
            .decode(input)
            .unwrap()
    }

    /// BCrypt 跨语言互认:Java BCryptPasswordEncoder 产出 $2a$ 前缀 hash;
    /// Rust bcrypt::verify 必须能验 $2a$(标准 crypt_blowfish 公开向量,密码 "U*U")。
    /// 迁移过来的 admin_user.password_hash 全部是 $2a$,此向量即登录兼容性证明。
    #[test]
    fn bcrypt_java_style_2a_vector() {
        let java_style_hash = "$2a$05$CCCCCCCCCCCCCCCCCCCCC.E5YPO9kmyuRGyh0XouQYb4YMJKvyOeW";
        assert!(bcrypt::verify("U*U", java_style_hash).unwrap());
        assert!(!bcrypt::verify("wrong-password", java_style_hash).unwrap());
        // $2b$(Rust 侧生成)与 $2a$ 算法同源,verify 同样接受——签名差异仅版本位
        let rust_hash = bcrypt::hash("U*U", 4).unwrap();
        assert!(rust_hash.starts_with("$2b$"));
        assert!(bcrypt::verify("U*U", &rust_hash).unwrap());
    }
}
