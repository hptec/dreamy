//! dashboard/analytics/cache REST 层。

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::cache_admin as cache_svc;
use crate::dashboard as dash;
use crate::service_category::CatalogError;

fn err_resp(e: CatalogError) -> Response {
    (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

pub async fn dashboard(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match dash::dashboard(&state.biz_db).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

pub async fn overview(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match dash::overview(&state.biz_db).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

pub async fn traffic(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match dash::traffic(&state.biz_db).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

#[derive(Deserialize)]
pub struct TaskQuery {
    pub status: Option<i64>,
    pub page: Option<i64>,
    #[serde(rename = "page_size")]
    pub page_size: Option<i64>,
}

pub async fn cache_tasks(State(state): State<SharedState>, _a: AuthedAdmin, Query(q): Query<TaskQuery>) -> Response {
    let page = q.page.unwrap_or(1).max(1);
    let page_size = match q.page_size {
        Some(p) if (1..=100).contains(&p) => p,
        _ => 20,
    };
    match cache_svc::list_tasks(&state.biz_db, q.status, page, page_size).await {
        Ok((items, total)) => ok_json(json!({
            "data": items, "total_elements": total, "page_number": page, "page_size": page_size,
        })),
        Err(e) => err_resp(e),
    }
}

pub async fn cache_summary(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match cache_svc::summary(&state.biz_db).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

pub async fn cache_targets(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    ok_json(json!({ "items": cache_svc::targets() }))
}

pub async fn cache_create(State(state): State<SharedState>, _a: AuthedAdmin, Json(req): Json<cache_svc::ManualTaskRequest>) -> Response {
    let targets = req.targets.unwrap_or_default();
    let reason = req.reason.as_deref().unwrap_or("manual");
    match cache_svc::create_manual_task(&state.biz_db, state.redis.as_ref(), &targets, reason).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn cache_retry(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(cache_svc::not_found()); };
    match cache_svc::retry(&state.biz_db, state.redis.as_ref(), id).await {
        Ok(retried) => ok_json(json!({ "retried": retried })),
        Err(e) => err_resp(e),
    }
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{get, post};
    axum::Router::new()
        .route("/api/admin/dashboard", get(dashboard))
        .route("/api/admin/analytics/overview", get(overview))
        .route("/api/admin/analytics/traffic", get(traffic))
        .route("/api/admin/cache/tasks", get(cache_tasks).post(cache_create))
        .route("/api/admin/cache/summary", get(cache_summary))
        .route("/api/admin/cache/targets", get(cache_targets))
        .route("/api/admin/cache/tasks/{id}/retry", post(cache_retry))
        .with_state(DcState { shared: state, jwt })
}

#[derive(Clone)]
pub struct DcState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<DcState> for SharedState {
    fn from_ref(s: &DcState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<DcState> for identity::security::JwtProvider {
    fn from_ref(s: &DcState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
