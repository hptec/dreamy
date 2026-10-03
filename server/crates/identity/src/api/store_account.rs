//! /api/store/account/* 端点(需 store JWT):profile GET/PUT、identities GET/bind/unbind、
//! email/change-primary、delete。鉴权 = axum extractor(本地验签 + Redis 主存会话校验)。

use axum::extract::{Path, State};
use axum::http::HeaderMap;
use axum::response::{IntoResponse, Response};
use axum::routing::{delete, get, post, put};
use axum::{Json, Router};
use common::error::{BizError, ErrorCode, R};
use common::i18n::Locale;
use common::state::SharedState;
use serde_json::{json, Value};
use sea_orm::{ConnectionTrait, TransactionTrait};

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
        .route("/guide-progress", get(guide_progress_list))
        .route("/guide-progress/{guide_id}", put(guide_progress_replace))
        .route("/delete", post(delete_account))
        .with_state(StoreState { shared: state, jwt })
}

/// 鉴权 extractor:Bearer JWT 本地验签 + Redis 主存会话校验(40100 短路体对齐 Java 过滤器)
pub struct AuthedUser {
    pub claims: StoreClaims,
}

impl AuthedUser {
    /// subject 即 customer_id;非数字视为非法令牌(调用方应回 401,而非静默置 0)
    pub fn customer_id(&self) -> Option<i64> {
        self.claims.sub.parse().ok()
    }
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
        // subject 不变量:必须是数字 customer_id。非法令牌在此短路,
        // 防止下游 handlers 静默挂到 customer 0(Java StoreAuth 语义对齐)
        if claims.sub.parse::<i64>().is_err() {
            return Err(unauthorized_body());
        }
        match session::validate_store(&st, &claims.jti).await {
            Ok(true) => Ok(AuthedUser { claims }),
            _ => Err(unauthorized_body()),
        }
    }
}

/// 过滤器级 401 短路体(硬编码英文 message,无 data 字段——Java 契约原样)
pub fn unauthorized_body() -> Response {
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

// ══════════ guide-progress(Java StoreGuideProgressController + UserGuideTaskService 移植) ══════════

fn validation_body(field: &str, reason: &str) -> Response {
    let body = json!({
        "code": 422501,
        "message": null,
        "service_id": null,
        "data": {"fields": {field: reason}}
    });
    let mut resp = Json(body).into_response();
    *resp.status_mut() = axum::http::StatusCode::UNPROCESSABLE_ENTITY;
    resp
}

fn db_error_body() -> Response {
    let body = json!({"code": 50000, "message": "Something went wrong, please try again"});
    Json(body).into_response()
}

async fn guide_progress_map(biz_db: &sea_orm::DatabaseConnection, uid: i64) -> Result<serde_json::Map<String, Value>, sea_orm::DbErr> {
    let rows = biz_db
        .query_all(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "SELECT guide_id, task_id FROM user_guide_task WHERE user_id = ? ORDER BY guide_id, task_id",
            [uid.into()],
        ))
        .await?;
    let mut map = serde_json::Map::new();
    for r in rows.iter() {
        let gid = r.try_get::<u64>("", "guide_id").map(|v| v as i64).unwrap_or(0);
        let tid = r.try_get::<u64>("", "task_id").map(|v| v as i64).unwrap_or(0);
        let entry = map.entry(gid.to_string()).or_insert_with(|| Value::Array(Vec::new()));
        if let Some(arr) = entry.as_array_mut() {
            arr.push(json!(tid));
        }
    }
    Ok(map)
}

/// GET /api/store/account/guide-progress → Map<guideId, [taskId]>
async fn guide_progress_list(authed: AuthedUser, State(st): State<StoreState>) -> Response {
    let uid = user_id(&authed.claims);
    match guide_progress_map(&st.shared.biz_db, uid).await {
        Ok(map) => Json(R::ok(Some(Value::Object(map)))).into_response(),
        Err(e) => {
            tracing::error!("[account] guide progress list failed:{e}");
            db_error_body()
        }
    }
}

#[derive(serde::Deserialize)]
pub struct ProgressBody {
    #[serde(default, alias = "taskIds")]
    pub task_ids: Option<Vec<i64>>,
}

/// PUT /api/store/account/guide-progress/{guideId}(整体替换该指南的勾选;task 须为已发布指南任务)
async fn guide_progress_replace(
    authed: AuthedUser,
    State(st): State<StoreState>,
    Path(guide_id): Path<i64>,
    Json(req): Json<ProgressBody>,
) -> Response {
    use std::collections::BTreeSet;
    let uid = user_id(&authed.claims);
    // Java:distinct + limit 50(静默截断)
    let tasks: Vec<i64> = req
        .task_ids
        .unwrap_or_default()
        .into_iter()
        .filter(|v| *v > 0)
        .collect::<BTreeSet<_>>()
        .into_iter()
        .take(50)
        .collect();
    // 校验:全部 task 必须属于该已发布指南的 guide_task 集合
    let id_list = tasks.iter().map(|t| t.to_string()).collect::<Vec<_>>().join(",");
    let valid = if tasks.is_empty() {
        Some(true)
    } else {
        let expected = tasks.len() as u64;
        st.shared
            .biz_db
            .query_one(sea_orm::Statement::from_string(
                sea_orm::DatabaseBackend::MySql,
                format!(
                    "SELECT COUNT(*) AS n FROM guide_task WHERE guide_id = {guide_id} AND id IN ({id_list})"
                ),
            ))
            .await
            .ok()
            .flatten()
            .and_then(|r| r.try_get::<u64>("", "n").ok())
            .map(|n| n == expected)
    };
    match valid {
        None => {
            tracing::error!("[account] guide task validate failed:db error");
            db_error_body()
        }
        Some(false) => validation_body("task_ids", "invalid_task_id"),
        Some(true) => {
            let tx = match st.shared.biz_db.begin().await {
                Ok(tx) => tx,
                Err(e) => {
                    tracing::error!("[account] guide replace tx failed:{e}");
                    return db_error_body();
                }
            };
            if let Err(e) = tx
                .execute(sea_orm::Statement::from_sql_and_values(
                    sea_orm::DatabaseBackend::MySql,
                    "DELETE FROM user_guide_task WHERE user_id = ? AND guide_id = ?",
                    [uid.into(), guide_id.into()],
                ))
                .await
            {
                tracing::error!("[account] guide replace delete failed:{e}");
                return db_error_body();
            }
            for (idx, tid) in tasks.iter().enumerate() {
                if let Err(e) = tx
                    .execute(sea_orm::Statement::from_sql_and_values(
                        sea_orm::DatabaseBackend::MySql,
                        "INSERT INTO user_guide_task(user_id, guide_id, task_id) VALUES (?,?,?)",
                        [uid.into(), guide_id.into(), (*tid).into()],
                    ))
                    .await
                {
                    tracing::error!("[account] guide replace insert failed idx={idx}:{e}");
                    return db_error_body();
                }
            }
            match tx.commit().await {
                Ok(()) => {}
                Err(e) => {
                    tracing::error!("[account] guide replace commit failed:{e}");
                    return db_error_body();
                }
            }
            match guide_progress_map(&st.shared.biz_db, uid).await {
                Ok(map) => Json(R::ok(Some(Value::Object(map)))).into_response(),
                Err(e) => {
                    tracing::error!("[account] guide progress reload failed:{e}");
                    db_error_body()
                }
            }
        }
    }
}
