//! product admin REST 层。

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::product::admin as svc;
use crate::service_category::CatalogError;

fn err_resp(e: CatalogError) -> Response {
    let status = if e.code == 404501 { StatusCode::NOT_FOUND } else { StatusCode::INTERNAL_SERVER_ERROR };
    (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

#[derive(Deserialize)]
pub struct ListQuery {
    pub status: Option<i64>,
    pub keyword: Option<String>,
    pub page: Option<i64>,
    #[serde(rename = "page_size")]
    pub page_size: Option<i64>,
}

pub async fn list(State(state): State<SharedState>, _a: AuthedAdmin, Query(q): Query<ListQuery>) -> Response {
    let page = q.page.unwrap_or(1).max(1);
    let page_size = match q.page_size {
        Some(p) if (1..=100).contains(&p) => p,
        _ => 20,
    };
    match svc::page_list(&state.biz_db, q.status, q.keyword.as_deref(), page, page_size).await {
        Ok((items, total)) => ok_json(json!({
            "data": items, "total_elements": total, "page_number": page, "page_size": page_size,
        })),
        Err(e) => err_resp(e),
    }
}

pub async fn get_product(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::get(&state.biz_db, id).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct StatusBody {
    pub status: Option<i64>,
}

pub async fn toggle_status(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>, Json(req): Json<StatusBody>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    let Some(status) = req.status else { return err_resp(CatalogError::field_validation(&[("status", "required")])); };
    match svc::toggle_status(&state.biz_db, id, status).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct FlagsBody {
    pub is_new: Option<bool>,
    pub is_best: Option<bool>,
    pub recommend: Option<bool>,
    pub sort: Option<i64>,
}

pub async fn patch_flags(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>, Json(req): Json<FlagsBody>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::patch_flags(&state.biz_db, id, req.is_new, req.is_best, req.recommend, req.sort).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn remove(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::delete(&state.biz_db, id).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::get;
    axum::Router::new()
        .route("/api/admin/products", get(list))
        .route("/api/admin/products/{id}", get(get_product).delete(remove))
        .route("/api/admin/products/{id}/status", axum::routing::patch(toggle_status))
        .route("/api/admin/products/{id}/flags", axum::routing::patch(patch_flags))
        .with_state(PAdminState { shared: state, jwt })
}

#[derive(Clone)]
pub struct PAdminState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<PAdminState> for SharedState {
    fn from_ref(s: &PAdminState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<PAdminState> for identity::security::JwtProvider {
    fn from_ref(s: &PAdminState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
