//! banner REST 层(E-MKT-21~25;admin;marketing 域段 7 错误码)。

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::banner as svc;
use crate::service_category::CatalogError;

common::error_site!(SITE_ADMIN_BANNERS = "marketing/banner/admin");

fn err_resp(e: CatalogError) -> Response {
    let status = axum::http::StatusCode::from_u16(crate::service_category::cat_err::http_status(e.code))
        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
    (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

#[derive(Deserialize)]
pub struct PositionQuery {
    pub position: Option<i64>,
}

pub async fn list(State(state): State<SharedState>, _a: AuthedAdmin, Query(q): Query<PositionQuery>) -> Response {
    match svc::list(&state.biz_db, q.position).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn create(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Json(req): Json<svc::BannerUpsert>,
) -> Response {
    match svc::create(&state.biz_db, &state.db, req, &a.claims.sub).await {
        Ok(dto) => {
            (StatusCode::CREATED, Json(json!({"code": 0, "message": null, "service_id": null, "data": dto}))).into_response()
        }
        Err(e) => err_resp(e),
    }
}

pub async fn update(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<svc::BannerUpsert>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc_not_found());
    };
    match svc::update(&state.biz_db, &state.db, id, req, &a.claims.sub).await {
        Ok(dto) => ok_json(json!(dto)),
        Err(e) => err_resp(e),
    }
}

pub async fn delete(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc_not_found());
    };
    match svc::delete(&state.biz_db, &state.db, id, &a.claims.sub).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct ToggleRequest {
    pub status: Option<i64>,
}

pub async fn toggle_status(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<ToggleRequest>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc_not_found());
    };
    match svc::toggle_status(&state.biz_db, &state.db, id, req.status.unwrap_or(0), &a.claims.sub).await {
        Ok(dto) => ok_json(json!(dto)),
        Err(e) => err_resp(e),
    }
}

fn svc_not_found() -> CatalogError {
    CatalogError::new(404701)
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{get, post, put};
    axum::Router::new()
        .route("/api/admin/banners", get(list).post(create))
        .route("/api/admin/banners/{id}", put(update).delete(axum::routing::delete(delete)))
        .route("/api/admin/banners/{id}/status", axum::routing::patch(toggle_status))
        .with_state(BannerState { shared: state, jwt })
}

#[derive(Clone)]
pub struct BannerState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<BannerState> for SharedState {
    fn from_ref(s: &BannerState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<BannerState> for identity::security::JwtProvider {
    fn from_ref(s: &BannerState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
