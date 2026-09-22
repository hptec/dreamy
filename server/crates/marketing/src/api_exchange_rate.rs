//! exchangerate REST 层。

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::exchange_rate as svc;
use crate::service_category::CatalogError;

fn err_resp(e: CatalogError) -> Response {
    let status = if e.code == 404501 { StatusCode::NOT_FOUND } else { StatusCode::INTERNAL_SERVER_ERROR };
    (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

pub async fn list(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match svc::list(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct RateBody {
    pub rate: Option<f64>,
}

pub async fn put_rate(State(state): State<SharedState>, _a: AuthedAdmin, Path(currency): Path<String>, Json(req): Json<RateBody>) -> Response {
    let Some(rate) = req.rate else { return err_resp(CatalogError::field_validation(&[("rate", "required")])); };
    match svc::put_rate(&state.biz_db, &currency, rate, 0).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn history(State(state): State<SharedState>, _a: AuthedAdmin, Path(currency): Path<String>) -> Response {
    match svc::history(&state.biz_db, &currency).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn refresh(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match svc::refresh(&state.biz_db, 0).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{get, post};
    axum::Router::new()
        .route("/api/admin/exchange-rates", get(list))
        .route("/api/admin/exchange-rates/refresh", post(refresh))
        .route("/api/admin/exchange-rates/{currency}", axum::routing::put(put_rate))
        .route("/api/admin/exchange-rates/{currency}/history", get(history))
        .with_state(FxState { shared: state, jwt })
}

#[derive(Clone)]
pub struct FxState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<FxState> for SharedState {
    fn from_ref(s: &FxState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<FxState> for identity::security::JwtProvider {
    fn from_ref(s: &FxState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
