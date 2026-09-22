//! flashsale REST 层:admin CRUD。

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::flashsale as svc;
use crate::service_category::CatalogError;

fn err_resp(e: CatalogError) -> Response {
    let status = if e.code == 404702 { StatusCode::NOT_FOUND } else { StatusCode::INTERNAL_SERVER_ERROR };
    (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

pub async fn list(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match svc::admin_list(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct UpsertBody {
    pub name: Option<String>,
    pub discount: Option<String>,
    pub start_at: Option<String>,
    pub end_at: Option<String>,
}

pub async fn create(State(state): State<SharedState>, _a: AuthedAdmin, Json(req): Json<UpsertBody>) -> Response {
    let r = svc::FlashSaleUpsert { name: req.name, discount: req.discount, start_at: req.start_at, end_at: req.end_at };
    match svc::create(&state.biz_db, &r).await {
        Ok(v) => (StatusCode::CREATED, ok_json(v)).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn update(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>, Json(req): Json<UpsertBody>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    let r = svc::FlashSaleUpsert { name: req.name, discount: req.discount, start_at: req.start_at, end_at: req.end_at };
    match svc::update(&state.biz_db, id, &r).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
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
    use axum::routing::{get, post};
    axum::Router::new()
        .route("/api/admin/promotions/flash-sales", get(list).post(create))
        .route("/api/admin/promotions/flash-sales/{id}", axum::routing::put(update).delete(remove))
        .with_state(FlashState { shared: state, jwt })
}

#[derive(Clone)]
pub struct FlashState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<FlashState> for SharedState {
    fn from_ref(s: &FlashState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<FlashState> for identity::security::JwtProvider {
    fn from_ref(s: &FlashState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
