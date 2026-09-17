//! /api/store/auth/* 端点(公开):otp/send、otp/verify、oidc/{provider}/callback、refresh、config。
//! 契约复刻 StoreAuthController:R 包络 snake_case、错误码表、config 带 no-store。

use axum::extract::State;
use axum::http::HeaderMap;
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post};
use axum::{Json, Router};
use common::error::{svc_to_biz, BizError, ErrorCode, R};
use common::i18n::Locale;
use common::state::SharedState;
use serde_json::json;

use super::StoreState;

use crate::security::JwtProvider;
use crate::service::{auth, authconfig, otp, ratelimit, SvcError};

pub fn router(state: SharedState, jwt: JwtProvider) -> Router {
    Router::new()
        .route("/otp/send", post(send_otp))
        .route("/otp/verify", post(verify_otp))
        .route("/oidc/{provider}/callback", post(oidc_callback))
        .route("/refresh", post(refresh))
        .route("/config", get(get_config))
        .with_state(StoreState { shared: state, jwt })
}

fn locale_of(headers: &HeaderMap) -> Locale {
    Locale::from_accept_language(headers.get("accept-language").and_then(|v| v.to_str().ok()))
}

fn client_ip(headers: &HeaderMap) -> String {
    // 信任链:X-Real-IP(网关注入,可信)优先;XFF 只信最右一跳——最左值是客户端可伪造位,
    // 取最左会绕过 IP 频控/IP 熔断并污染 login_history 取证
    headers
        .get("x-real-ip")
        .and_then(|v| v.to_str().ok())
        .map(|v| v.trim().to_string())
        .filter(|v| !v.is_empty())
        .or_else(|| {
            headers
                .get("x-forwarded-for")
                .and_then(|v| v.to_str().ok())
                .and_then(|v| v.rsplit(',').next())
                .map(|v| v.trim().to_string())
                .filter(|v| !v.is_empty())
        })
        .unwrap_or_else(|| "127.0.0.1".into())
}

fn user_agent(headers: &HeaderMap) -> Option<String> {
    headers
        .get("user-agent")
        .and_then(|v| v.to_str().ok())
        .map(|v| v.to_string())
}

/// 1.1 sendOtp
async fn send_otp(
    State(st): State<StoreState>,
    headers: HeaderMap,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    let locale = locale_of(&headers);
    // 手工解析(宽松对齐 Java @Valid:email 必填)
    let Some(Json(v)) = body else {
        return BizError::new("identity/auth/send_otp", ErrorCode::BadRequestBody)
            .with_locale(locale.code())
            .into_response();
    };
    let email = v
        .get("email")
        .and_then(|e| e.as_str())
        .unwrap_or("")
        .trim()
        .to_lowercase();
    if email.is_empty() {
        let mut b = BizError::new("identity/auth/send_otp", ErrorCode::Validation);
        b.locale = locale.code();
        return b
            .with_details(json!({"email": "must not be blank"}))
            .into_response();
    }
    let req_locale = v
        .get("locale")
        .and_then(|l| l.as_str())
        .unwrap_or("en")
        .to_string();

    match otp::send_otp(&st.shared, &email, &req_locale, &client_ip(&headers)).await {
        Ok(r) => Json(R::ok(Some(json!({
            "resend_after_seconds": r.resend_after_seconds,
            "otp_length": r.otp_length,
        }))))
        .into_response(),
        Err(e) => svc_to_biz("identity/auth/send_otp", locale, e).into_response(),
    }
}

/// 1.2 verifyOtp
async fn verify_otp(
    State(st): State<StoreState>,
    headers: HeaderMap,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    let locale = locale_of(&headers);
    let Some(Json(v)) = body else {
        return BizError::new("identity/auth/verify_otp", ErrorCode::BadRequestBody)
            .with_locale(locale.code())
            .into_response();
    };
    let email = v
        .get("email")
        .and_then(|e| e.as_str())
        .unwrap_or("")
        .trim()
        .to_lowercase();
    let code = v
        .get("code")
        .and_then(|c| c.as_str())
        .unwrap_or("")
        .to_string();
    if email.is_empty() || code.is_empty() {
        let mut details = serde_json::Map::new();
        if email.is_empty() {
            details.insert("email".into(), json!("must not be blank"));
        }
        if code.is_empty() {
            details.insert("code".into(), json!("must not be blank"));
        }
        let mut b = BizError::new("identity/auth/verify_otp", ErrorCode::Validation);
        b.locale = locale.code();
        return b.with_details(json!(details)).into_response();
    }

    // 防御参数(auth_config 可调;读取失败回退默认,对齐 admin 锁定参数回退模式)
    let ip = client_ip(&headers);
    let (rate_per_min, alert_threshold, alert_email) = match authconfig::get(&st.shared).await {
        Ok(c) => (
            c.verify_ip_rate_per_minute.max(1) as u64,
            c.attack_alert_threshold.max(1) as i64,
            c.admin_alert_email,
        ),
        Err(e) => {
            tracing::warn!(error = %e, "[verify-otp] auth_config 不可读,防御参数回退默认(30/min,阈值20)");
            (30, 20, None)
        }
    };
    // V7 IP 级频控(42904)
    if let Some(remain) = ratelimit::check_verify_rate(&st.shared, &ip, rate_per_min).await {
        return svc_to_biz(
            "identity/auth/verify_otp",
            locale,
            SvcError::code_with(42904, json!({ "remaining_seconds": remain })),
        )
        .into_response();
    }
    // L2 双键递进退避(42905)
    if let Some(remain) = ratelimit::check_backoff(&st.shared, &email, &ip).await {
        return svc_to_biz(
            "identity/auth/verify_otp",
            locale,
            SvcError::code_with(42905, json!({ "remaining_seconds": remain })),
        )
        .into_response();
    }

    let ctx = auth::LoginContext {
        ip,
        user_agent: user_agent(&headers),
        device_fingerprint: None,
    };
    match auth::login_with_otp(&st.shared, &st.jwt, &email, &code, &ctx).await {
        Ok(result) => {
            ratelimit::clear_backoff(&st.shared, &email).await;
            Json(R::ok(Some(login_response(&result)))).into_response()
        }
        Err(e) => {
            // L2:仅验证码类失败计数(40101 码错/41001 无码或过期/41002 锁定);频控与系统错误不计
            if let Some(c) = e.biz_code() {
                if [40101, 41001, 41002].contains(&c) {
                    ratelimit::record_verify_failure(
                        &st.shared,
                        &email,
                        &ctx.ip,
                        alert_threshold,
                        alert_email.as_deref(),
                    )
                    .await;
                }
            }
            svc_to_biz("identity/auth/verify_otp", locale, e).into_response()
        }
    }
}

/// 1.3 oidcCallback
async fn oidc_callback(
    State(st): State<StoreState>,
    headers: HeaderMap,
    axum::extract::Path(provider): axum::extract::Path<String>,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    let locale = locale_of(&headers);
    if provider != "google" && provider != "apple" {
        let mut b = BizError::new("identity/auth/oidc_callback", ErrorCode::Validation);
        b.locale = locale.code();
        return b
            .with_details(
                json!({"provider": format!("must match \"google|apple\", got {provider}")}),
            )
            .into_response();
    }
    let Some(Json(v)) = body else {
        return BizError::new("identity/auth/oidc_callback", ErrorCode::BadRequestBody)
            .with_locale(locale.code())
            .into_response();
    };
    let id_token = v
        .get("id_token")
        .and_then(|t| t.as_str())
        .unwrap_or("")
        .to_string();
    let nonce = v
        .get("nonce")
        .and_then(|n| n.as_str())
        .map(|s| s.to_string());
    if id_token.is_empty() {
        let mut b = BizError::new("identity/auth/oidc_callback", ErrorCode::Validation);
        b.locale = locale.code();
        return b
            .with_details(json!({"idToken": "must not be blank"}))
            .into_response();
    }

    // provider 开关(40303)
    let cfg = match authconfig::get(&st.shared).await {
        Ok(c) => c,
        Err(e) => return svc_to_biz("identity/auth/oidc_callback", locale, e).into_response(),
    };
    let enabled = if provider == "google" {
        cfg.google_enabled
    } else {
        cfg.apple_enabled
    };
    if !enabled {
        return svc_to_biz("identity/auth/oidc_callback", locale, SvcError::code(40303))
            .into_response();
    }
    let client_id = if provider == "google" {
        cfg.google_client_id.clone().unwrap_or_default()
    } else {
        cfg.apple_service_id.clone().unwrap_or_default()
    };

    let oidc_result = match crate::oidc::verify(
        &st.shared,
        &provider,
        &id_token,
        nonce.as_deref(),
        &client_id,
    )
    .await
    {
        Ok(r) => r,
        Err(e) => return svc_to_biz("identity/auth/oidc_callback", locale, e).into_response(),
    };
    let provider_enum = if provider == "google" {
        crate::enums::AuthProvider::Google
    } else {
        crate::enums::AuthProvider::Apple
    };

    let ctx = auth::LoginContext {
        ip: client_ip(&headers),
        user_agent: user_agent(&headers),
        device_fingerprint: None,
    };
    match auth::login_with_oidc(&st.shared, &st.jwt, provider_enum, &oidc_result, &ctx).await {
        Ok(result) => Json(R::ok(Some(login_response(&result)))).into_response(),
        Err(e) => svc_to_biz("identity/auth/oidc_callback", locale, e).into_response(),
    }
}

/// 1.4 refreshToken
async fn refresh(
    State(st): State<StoreState>,
    headers: HeaderMap,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    let locale = locale_of(&headers);
    let Some(Json(v)) = body else {
        return BizError::new("identity/auth/refresh", ErrorCode::BadRequestBody)
            .with_locale(locale.code())
            .into_response();
    };
    let refresh_token = v
        .get("refresh_token")
        .and_then(|t| t.as_str())
        .unwrap_or("")
        .to_string();
    if refresh_token.is_empty() {
        let mut b = BizError::new("identity/auth/refresh", ErrorCode::Validation);
        b.locale = locale.code();
        return b
            .with_details(json!({"refreshToken": "must not be blank"}))
            .into_response();
    }
    match auth::refresh(&st.shared, &st.jwt, &refresh_token).await {
        Ok(tokens) => Json(R::ok(Some(json!({ "tokens": token_map(&tokens) })))).into_response(),
        Err(e) => svc_to_biz("identity/auth/refresh", locale, e).into_response(),
    }
}

/// 1.5 getStoreAuthConfig(Cache-Control: no-store 硬契约)
async fn get_config(State(st): State<StoreState>) -> Response {
    match authconfig::get(&st.shared).await {
        Ok(cfg) => {
            let body = json!({
                "email_enabled": cfg.email_enabled,
                "google_enabled": cfg.google_enabled,
                "apple_enabled": cfg.apple_enabled,
                "otp_length": cfg.otp_length,
                "google_client_id": cfg.google_client_id,
                "apple_service_id": cfg.apple_service_id,
            });
            let mut resp = Json(R::ok(Some(body))).into_response();
            resp.headers_mut().insert(
                axum::http::header::CACHE_CONTROL,
                axum::http::HeaderValue::from_static("private, no-store"),
            );
            resp
        }
        Err(e) => svc_to_biz("identity/auth/config", Locale::En, e).into_response(),
    }
}

fn login_response(result: &auth::LoginResult) -> serde_json::Value {
    json!({
        "tokens": token_map(&result.tokens),
        "user": profile_view(&result.user),
        "is_new_account": result.new_account,
    })
}

fn token_map(t: &auth::TokenPairDto) -> serde_json::Value {
    json!({
        "access_token": t.access_token,
        "refresh_token": t.refresh_token,
        "access_expires_at": common::time::format_iso(t.access_expires_at),
        "refresh_expires_at": common::time::format_iso(t.refresh_expires_at),
    })
}

/// UserProfileDTO 同构(与 Java 字段集/命名一字不差)
pub fn profile_view(u: &crate::entity::user::Model) -> serde_json::Value {
    json!({
        "id": u.id,
        "email": u.email,
        "email_verified": u.email_verified != 0,
        "name": u.name,
        "phone": u.phone,
        "tier": u.tier,
        "avatar": u.avatar,
        "joined_at": u.joined_at.map(common::time::format_iso),
        "status": u.status,
        "locale_pref": u.locale_pref,
    })
}
