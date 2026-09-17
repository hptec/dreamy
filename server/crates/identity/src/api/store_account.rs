//! /api/store/account/* 端点(需 store JWT):profile GET/PUT、identities GET/bind/unbind、
//! email/change-primary、delete。鉴权 = axum extractor(本地验签 + Redis 主存会话校验)。

use axum::extract::{Path, State};
use axum::http::HeaderMap;
use axum::response::{IntoResponse, Response};
use axum::routing::{delete, get, post};
use axum::{Json, Router};
use common::error::{BizError, ErrorCode, R};
use common::i18n::Locale;
use common::state::SharedState;
use serde_json::json;

use super::StoreState;
use crate::security::{JwtProvider, StoreClaims};
use crate::service::{session, SvcError};
use axum::extract::FromRequestParts;

pub fn router(state: SharedState, jwt: JwtProvider) -> Router {
    Router::new()
        .route("/profile", get(get_profile).put(update_profile))
        .route("/identities", get(list_identities))
        .route("/identities/bind", post(bind_identity))
        .route("/identities/{identity_id}", delete(unbind_identity))
        .route("/email/change-primary", post(change_primary_email))
        .route("/delete", post(delete_account))
        .with_state(StoreState { shared: state, jwt })
}

/// 鉴权 extractor:Bearer JWT 本地验签 + Redis 主存会话校验(40100 短路体对齐 Java 过滤器)
pub struct AuthedUser {
    pub claims: StoreClaims,
}

impl<S> FromRequestParts<S> for AuthedUser
where
    S: Send + Sync,
    SharedState: axum::extract::FromRef<S>,
    JwtProvider: axum::extract::FromRef<S>,
{
    type Rejection = Response;

    async fn from_request_parts(
        parts: &mut axum::http::request::Parts,
        state: &S,
    ) -> Result<Self, Self::Rejection> {
        let st = <SharedState as axum::extract::FromRef<S>>::from_ref(state);
        let jwt = <JwtProvider as axum::extract::FromRef<S>>::from_ref(state);
        let _ = &jwt;
        let token = parts
            .headers
            .get(axum::http::header::AUTHORIZATION)
            .and_then(|v| v.to_str().ok())
            .and_then(|v| v.strip_prefix("Bearer "))
            .map(|v| v.trim().to_string());
        let Some(token) = token else {
            return Err(unauthorized_body());
        };
        let claims = match jwt.parse_store(&token) {
            Ok(c) => c,
            Err(_) => return Err(unauthorized_body()),
        };
        // 会话校验(仅 access 令牌;主存 GET)
        if claims.refresh {
            return Err(unauthorized_body());
        }
        match session::validate_store(&st, &claims.jti).await {
            Ok(true) => Ok(AuthedUser { claims }),
            _ => Err(unauthorized_body()),
        }
    }
}

/// 过滤器级 401 短路体(硬编码英文 message,无 data 字段——Java 契约原样)
fn unauthorized_body() -> Response {
    let body = json!({"code": 40100, "message": "Authentication required"});
    let mut resp = Json(body).into_response();
    *resp.status_mut() = axum::http::StatusCode::UNAUTHORIZED;
    resp
}

fn locale_of(headers: &HeaderMap) -> Locale {
    Locale::from_accept_language(headers.get("accept-language").and_then(|v| v.to_str().ok()))
}

fn user_id(claims: &StoreClaims) -> i64 {
    claims.sub.parse().unwrap_or(0)
}

fn to_resp(site: &'static str, locale: Locale, err: SvcError) -> Response {
    common::error::svc_to_biz(site, locale, err).into_response()
}

/// 2.1 getProfile
async fn get_profile(
    authed: AuthedUser,
    State(st): State<StoreState>,
    headers: HeaderMap,
) -> Response {
    let locale = locale_of(&headers);
    match crate::service::user_query::get_user(
        &st.shared,
        &crate::service::Lookup::Id(user_id(&authed.claims)),
    )
    .await
    {
        Ok(v) => {
            // 直接以 UserView 拼 UserProfileDTO 形状(与 store_auth::profile_view 同构)
            Json(R::ok(Some(json!({
                "id": v.id,
                "email": v.email,
                "email_verified": v.email_verified,
                "name": v.name,
                "phone": v.phone,
                "tier": v.tier,
                "avatar": v.avatar,
                "joined_at": v.joined_at,
                "status": v.status,
                "locale_pref": v.locale_pref,
            }))))
            .into_response()
        }
        Err(e) => to_resp("identity/account/get_profile", locale, e),
    }
}

/// 2.1b updateProfile(决策 13:仅更新提供字段)
async fn update_profile(
    authed: AuthedUser,
    State(st): State<StoreState>,
    headers: HeaderMap,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    let locale = locale_of(&headers);
    let Some(Json(v)) = body else {
        let mut b = BizError::new("identity/account/update_profile", ErrorCode::BadRequestBody);
        b.locale = locale.code();
        return b.into_response();
    };
    let display_name = v.get("display_name").and_then(|n| n.as_str());
    let locale_pref = v.get("locale_pref").and_then(|l| l.as_str());
    // 校验:display_name ≤64;locale_pref ∈ {en,es,fr}
    let mut details = serde_json::Map::new();
    if let Some(n) = display_name {
        if n.chars().count() > 64 {
            details.insert("displayName".into(), json!("size must be between 0 and 64"));
        }
    }
    if let Some(l) = locale_pref {
        if !["en", "es", "fr"].contains(&l) {
            details.insert("localePref".into(), json!("must match \"en|es|fr\""));
        }
    }
    if !details.is_empty() {
        let mut b = BizError::new("identity/account/update_profile", ErrorCode::Validation);
        b.locale = locale.code();
        return b.with_details(json!(details)).into_response();
    }

    match crate::service::account::update_profile(
        &st.shared,
        user_id(&authed.claims),
        display_name,
        locale_pref,
    )
    .await
    {
        Ok(updated) => Json(R::ok(Some(super::store_auth::profile_view(&updated)))).into_response(),
        Err(e) => to_resp("identity/account/update_profile", locale, e),
    }
}

/// 2.2 listIdentities(仅 connected=true;MAP-002 不暴露 provider_uid)
async fn list_identities(
    authed: AuthedUser,
    State(st): State<StoreState>,
    headers: HeaderMap,
) -> Response {
    let locale = locale_of(&headers);
    match crate::service::account::list_identities(&st.shared, user_id(&authed.claims)).await {
        Ok(list) => Json(R::ok(Some(serde_json::Value::Array(
            list.iter().map(|i| i.to_json()).collect(),
        ))))
        .into_response(),
        Err(e) => to_resp("identity/account/list_identities", locale, e),
    }
}

/// 2.3 bindIdentity
async fn bind_identity(
    authed: AuthedUser,
    State(st): State<StoreState>,
    headers: HeaderMap,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    let locale = locale_of(&headers);
    let Some(Json(v)) = body else {
        let mut b = BizError::new("identity/account/bind_identity", ErrorCode::BadRequestBody);
        b.locale = locale.code();
        return b.into_response();
    };
    let provider = v.get("provider").and_then(|p| p.as_i64()).unwrap_or(0);
    let id_token = v
        .get("id_token")
        .and_then(|t| t.as_str())
        .unwrap_or("")
        .to_string();
    let email = v
        .get("email")
        .and_then(|e| e.as_str())
        .unwrap_or("")
        .to_string();
    let code = v
        .get("code")
        .and_then(|c| c.as_str())
        .unwrap_or("")
        .to_string();
    if !(1..=3).contains(&provider) {
        let mut b = BizError::new("identity/account/bind_identity", ErrorCode::Validation);
        b.locale = locale.code();
        return b
            .with_details(json!({"provider": "must be 1(EMAIL)/2(GOOGLE)/3(APPLE)"}))
            .into_response();
    }
    let ip = headers
        .get("x-forwarded-for")
        .and_then(|h| h.to_str().ok())
        .unwrap_or("127.0.0.1")
        .to_string();

    match crate::service::account::bind_identity(
        &st.shared,
        user_id(&authed.claims),
        provider as i32,
        &id_token,
        &email,
        &code,
        &ip,
    )
    .await
    {
        Ok(list) => Json(R::ok(Some(serde_json::Value::Array(
            list.iter().map(|i| i.to_json()).collect(),
        ))))
        .into_response(),
        Err(e) => to_resp("identity/account/bind_identity", locale, e),
    }
}

/// 2.4 unbindIdentity
async fn unbind_identity(
    authed: AuthedUser,
    State(st): State<StoreState>,
    headers: HeaderMap,
    Path(identity_id): Path<i64>,
) -> Response {
    let locale = locale_of(&headers);
    match crate::service::account::unbind_identity(&st.shared, user_id(&authed.claims), identity_id)
        .await
    {
        Ok(()) => Json(R::<serde_json::Value>::ok(None)).into_response(),
        Err(e) => to_resp("identity/account/unbind_identity", locale, e),
    }
}

/// 2.5 changePrimaryEmail
async fn change_primary_email(
    authed: AuthedUser,
    State(st): State<StoreState>,
    headers: HeaderMap,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    let locale = locale_of(&headers);
    let Some(Json(v)) = body else {
        let mut b = BizError::new("identity/account/change_primary", ErrorCode::BadRequestBody);
        b.locale = locale.code();
        return b.into_response();
    };
    let new_email = v
        .get("new_email")
        .and_then(|e| e.as_str())
        .unwrap_or("")
        .trim()
        .to_lowercase();
    let code = v
        .get("code")
        .and_then(|c| c.as_str())
        .unwrap_or("")
        .to_string();
    let mut details = serde_json::Map::new();
    if new_email.is_empty() {
        details.insert("newEmail".into(), json!("must not be blank"));
    }
    if code.is_empty() {
        details.insert("code".into(), json!("must not be blank"));
    }
    if !details.is_empty() {
        let mut b = BizError::new("identity/account/change_primary", ErrorCode::Validation);
        b.locale = locale.code();
        return b.with_details(json!(details)).into_response();
    }
    match crate::service::account::change_primary_email(
        &st.shared,
        user_id(&authed.claims),
        &new_email,
        &code,
    )
    .await
    {
        Ok(list) => Json(R::ok(Some(serde_json::Value::Array(
            list.iter().map(|i| i.to_json()).collect(),
        ))))
        .into_response(),
        Err(e) => to_resp("identity/account/change_primary", locale, e),
    }
}

/// 2.9 deleteAccount
async fn delete_account(
    authed: AuthedUser,
    State(st): State<StoreState>,
    headers: HeaderMap,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    let locale = locale_of(&headers);
    let Some(Json(v)) = body else {
        let mut b = BizError::new("identity/account/delete", ErrorCode::BadRequestBody);
        b.locale = locale.code();
        return b.into_response();
    };
    let confirmed = v.get("confirm").and_then(|c| c.as_bool()).unwrap_or(false);
    if !confirmed {
        let mut b = BizError::new("identity/account/delete", ErrorCode::Validation);
        b.locale = locale.code();
        return b.into_response();
    }
    match crate::service::account::delete_account(&st.shared, user_id(&authed.claims)).await {
        Ok(()) => Json(R::<serde_json::Value>::ok(None)).into_response(),
        Err(e) => to_resp("identity/account/delete", locale, e),
    }
}
