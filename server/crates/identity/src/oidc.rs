//! Google/Apple OIDC id_token 验签(JWKS 缓存 + kid 轮换)。
//!
//! 语义对齐 Java RealOidcVerifier:
//! - 验签(RS256)→ iss 精确匹配 → aud=配置 client_id → exp → nonce(传入时必须一致)
//! - 超时(5s,重试 1 次)→ 50401;不可达/验签失败/claim 不符 → 50201
//! - Apple is_private_email=true → hidden=true,relay=email

use std::collections::HashMap;
use std::sync::Arc;
use std::time::{Duration, Instant};

use common::state::SharedState;
use jsonwebtoken::{Algorithm, DecodingKey, Validation};
use serde::Deserialize;
use tokio::sync::RwLock;

use crate::service::SvcError;

const JWKS_URLS: [(&str, &str); 2] = [
    ("google", "https://www.googleapis.com/oauth2/v3/certs"),
    ("apple", "https://appleid.apple.com/auth/keys"),
];
const ISSUERS: [(&str, &str); 2] = [
    ("google", "https://accounts.google.com"),
    ("apple", "https://appleid.apple.com"),
];

#[derive(Debug, Clone)]
pub struct OidcResult {
    pub sub: String,
    pub email: Option<String>,
    pub email_verified: bool,
    pub hidden_email: bool,
    pub relay_email: Option<String>,
    pub name: Option<String>,
    pub picture: Option<String>,
}

#[derive(Debug, Deserialize)]
struct IdTokenClaims {
    sub: String,
    email: Option<String>,
    email_verified: Option<bool>,
    is_private_email: Option<bool>,
    name: Option<String>,
    picture: Option<String>,
    nonce: Option<String>,
}

#[derive(Debug, Deserialize)]
struct Jwks {
    keys: Vec<JwkKey>,
}

#[derive(Debug, Deserialize)]
struct JwkKey {
    kid: String,
    kty: String,
    n: String,
    e: String,
}

struct JwksCache {
    keys: HashMap<String, DecodingKey>,
    fetched_at: Instant,
}

/// 进程级 JWKS 缓存(6h 定期刷新 + 验签失败即回源重拉)
async fn jwks_for(provider: &str, force_refresh: bool) -> Result<Arc<JwksCache>, SvcError> {
    static CACHES: once_cell::sync::Lazy<RwLock<HashMap<String, Arc<JwksCache>>>> =
        once_cell::sync::Lazy::new(|| RwLock::new(HashMap::new()));
    const TTL: Duration = Duration::from_secs(6 * 3600);

    if !force_refresh {
        let cached = CACHES.read().await.get(provider).cloned();
        if let Some(c) = cached {
            if c.fetched_at.elapsed() < TTL {
                return Ok(c);
            }
        }
    }
    let url = JWKS_URLS
        .iter()
        .find(|(p, _)| *p == provider)
        .map(|(_, u)| *u)
        .ok_or_else(|| SvcError::code(50201))?;
    let jwks: Jwks = fetch_json(url).await?;
    let mut keys = HashMap::new();
    for k in jwks.keys {
        if k.kty == "RSA" {
            if let Ok(key) = DecodingKey::from_rsa_components(&k.n, &k.e) {
                keys.insert(k.kid, key);
            }
        }
    }
    if keys.is_empty() {
        return Err(SvcError::code(50201));
    }
    let cache = Arc::new(JwksCache {
        keys,
        fetched_at: Instant::now(),
    });
    CACHES
        .write()
        .await
        .insert(provider.to_string(), cache.clone());
    Ok(cache)
}

async fn fetch_json(url: &str) -> Result<Jwks, SvcError> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(5))
        .build()
        .map_err(|_| SvcError::code(50201))?;
    // 重试 1 次(对齐 Java 超时重试语义)
    let mut last_err: Option<String> = None;
    for _ in 0..2 {
        match client.get(url).send().await {
            Ok(resp) if resp.status().is_success() => match resp.json::<Jwks>().await {
                Ok(j) => return Ok(j),
                Err(e) => last_err = Some(e.to_string()),
            },
            Ok(resp) => last_err = Some(format!("http {}", resp.status())),
            Err(e) if e.is_timeout() => return Err(SvcError::code(50401)),
            Err(e) => last_err = Some(e.to_string()),
        }
    }
    let _ = last_err;
    Err(SvcError::code(50201))
}

/// 拉取 header(kid)不做完整解码——手动解 JWT 三段式的第二段前先看第一段
fn decode_segments(token: &str) -> Result<(String, String), SvcError> {
    let parts: Vec<&str> = token.split('.').collect();
    if parts.len() != 3 {
        return Err(SvcError::code(50201));
    }
    Ok((parts[0].to_string(), parts[1].to_string()))
}

fn b64url_decode(input: &str) -> Result<Vec<u8>, SvcError> {
    use base64::Engine;
    base64::engine::general_purpose::URL_SAFE_NO_PAD
        .decode(input)
        .map_err(|_| SvcError::code(50201))
}

#[derive(Debug, Deserialize)]
struct HeaderOnly {
    kid: String,
}

/// FLOW-03 OIDC 验证主入口
pub async fn verify(
    _state: &SharedState,
    provider: &str,
    id_token: &str,
    expected_nonce: Option<&str>,
    client_id: &str,
) -> Result<OidcResult, SvcError> {
    let issuer = ISSUERS
        .iter()
        .find(|(p, _)| *p == provider)
        .map(|(_, i)| *i)
        .ok_or_else(|| SvcError::code(50201))?;

    let (header_b64, payload_b64) = decode_segments(id_token)?;
    let header: HeaderOnly =
        serde_json::from_slice(&b64url_decode(&header_b64)?).map_err(|_| SvcError::code(50201))?;

    // 两轮:缓存验签 → 失败强制回源重拉再验(kid 轮换)
    let mut force = false;
    let claims = loop {
        let cache = jwks_for(provider, force).await?;
        let Some(key) = cache.keys.get(&header.kid) else {
            if force {
                return Err(SvcError::code(50201)); // 两次都无此 kid
            }
            force = true;
            continue;
        };
        let mut validation = Validation::new(Algorithm::RS256);
        validation.set_issuer(&[issuer]);
        validation.set_audience(&[client_id]);
        validation.leeway = 60;
        match jsonwebtoken::decode::<IdTokenClaims>(id_token, key, &validation) {
            Ok(data) => break data.claims,
            Err(e) if force => {
                tracing::warn!(error = %e, "[oidc] {provider} 验签失败(已回源重拉)");
                return Err(SvcError::code(50201));
            }
            Err(_) => {
                force = true; // 第一轮失败 → 重拉 JWKS 再试
            }
        }
    };

    // nonce(传入时必须一致,防重放)
    if let Some(expected) = expected_nonce {
        if claims.nonce.as_deref() != Some(expected) {
            return Err(SvcError::code(50201));
        }
    }

    let hidden = claims.is_private_email.unwrap_or(false);
    let email = claims.email.clone();
    Ok(OidcResult {
        sub: claims.sub,
        email: claims.email,
        email_verified: claims.email_verified.unwrap_or(false),
        hidden_email: hidden,
        relay_email: if hidden { email } else { None },
        name: claims.name,
        picture: claims.picture,
    })
}
