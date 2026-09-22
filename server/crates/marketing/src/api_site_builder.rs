//! site_builder REST 层:admin CRUD + store 公开 layout。

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::service_category::CatalogError;
use crate::site_builder as svc;

fn err_resp(e: CatalogError) -> Response {
    let status = if e.code == 404501 { StatusCode::NOT_FOUND } else { StatusCode::INTERNAL_SERVER_ERROR };
    (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

// announcements
pub async fn ann_list(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match svc::announcement_list(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn ann_get(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::announcement_get(&state.biz_db, id).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

#[derive(Deserialize)]
pub struct AnnBody {
    pub content: Option<String>,
    pub priority: Option<i64>,
    pub start_at: Option<String>,
    pub end_at: Option<String>,
    pub enabled: Option<bool>,
}

pub async fn ann_create(State(state): State<SharedState>, _a: AuthedAdmin, Json(req): Json<AnnBody>) -> Response {
    let r = svc::AnnouncementUpsert { content: req.content, priority: req.priority, start_at: req.start_at, end_at: req.end_at, enabled: req.enabled };
    match svc::announcement_create(&state.biz_db, &r).await {
        Ok(v) => (StatusCode::CREATED, ok_json(v)).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn ann_update(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>, Json(req): Json<AnnBody>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    let r = svc::AnnouncementUpsert { content: req.content, priority: req.priority, start_at: req.start_at, end_at: req.end_at, enabled: req.enabled };
    match svc::announcement_update(&state.biz_db, id, &r).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

pub async fn ann_toggle(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::announcement_toggle(&state.biz_db, id).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

pub async fn ann_delete(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::announcement_delete(&state.biz_db, id).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

// home_sections
pub async fn sec_list(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match svc::section_list(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn sec_get(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::section_get(&state.biz_db, id).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

#[derive(Deserialize)]
pub struct SecBody {
    pub section_type: Option<String>,
    pub label: Option<String>,
    pub data: Option<Value>,
    pub enabled: Option<bool>,
    pub sort_order: Option<i64>,
}

pub async fn sec_create(State(state): State<SharedState>, _a: AuthedAdmin, Json(req): Json<SecBody>) -> Response {
    let r = svc::SectionUpsert { section_type: req.section_type, label: req.label, data: req.data, enabled: req.enabled, sort_order: req.sort_order };
    match svc::section_create(&state.biz_db, &r).await {
        Ok(v) => (StatusCode::CREATED, ok_json(v)).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn sec_update(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>, Json(req): Json<SecBody>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    let r = svc::SectionUpsert { section_type: req.section_type, label: req.label, data: req.data, enabled: req.enabled, sort_order: req.sort_order };
    match svc::section_update(&state.biz_db, id, &r).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

pub async fn sec_toggle(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::section_toggle(&state.biz_db, id).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

#[derive(Deserialize)]
pub struct SortBody {
    pub ids: Option<Vec<i64>>,
}

pub async fn sec_sort(State(state): State<SharedState>, _a: AuthedAdmin, Json(req): Json<SortBody>) -> Response {
    let ids = req.ids.unwrap_or_default();
    if ids.is_empty() { return err_resp(CatalogError::field_validation(&[("ids", "required")])); }
    match svc::section_sort(&state.biz_db, &ids).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn sec_delete(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::section_delete(&state.biz_db, id).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

// footer / navigation
pub async fn footer_get(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match svc::footer_get(&state.biz_db).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

#[derive(Deserialize)]
pub struct FooterPutBody {
    pub columns: Option<Vec<svc::FooterColumnIn>>,
}

pub async fn footer_put(State(state): State<SharedState>, _a: AuthedAdmin, Json(req): Json<FooterPutBody>) -> Response {
    let cols = req.columns.unwrap_or_default();
    match svc::footer_put(&state.biz_db, &cols).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

pub async fn nav_get(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match svc::navigation_get(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct NavPutBody {
    pub items: Option<Vec<Value>>,
}

pub async fn nav_put(State(state): State<SharedState>, _a: AuthedAdmin, Json(req): Json<NavPutBody>) -> Response {
    let items = req.items.unwrap_or_default();
    match svc::navigation_put(&state.biz_db, &items).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

// store 公开 layout
pub async fn store_layout(State(state): State<SharedState>) -> Response {
    match svc::store_layout(&state.biz_db).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{get, post, put};
    axum::Router::new()
        .route("/api/admin/site-builder/announcements", get(ann_list).post(ann_create))
        .route("/api/admin/site-builder/announcements/{id}", get(ann_get).put(ann_update).delete(ann_delete))
        .route("/api/admin/site-builder/announcements/{id}/toggle", axum::routing::patch(ann_toggle))
        .route("/api/admin/site-builder/home-sections", get(sec_list).post(sec_create))
        .route("/api/admin/site-builder/home-sections/sort", put(sec_sort))
        .route("/api/admin/site-builder/home-sections/{id}", get(sec_get).put(sec_update).delete(sec_delete))
        .route("/api/admin/site-builder/home-sections/{id}/toggle", axum::routing::patch(sec_toggle))
        .route("/api/admin/site-builder/footer", get(footer_get).put(footer_put))
        .route("/api/admin/site-builder/navigation", get(nav_get).put(nav_put))
        .with_state(SbState { shared: state, jwt })
}

pub fn store_router() -> axum::Router<SharedState> {
    axum::Router::new().route("/api/store/layout", axum::routing::get(store_layout))
}

#[derive(Clone)]
pub struct SbState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<SbState> for SharedState {
    fn from_ref(s: &SbState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<SbState> for identity::security::JwtProvider {
    fn from_ref(s: &SbState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
