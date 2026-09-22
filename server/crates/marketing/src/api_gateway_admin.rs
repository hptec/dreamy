//! gateway REST 层。

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::gateway_admin as svc;
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

pub async fn get_config(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::get(&state.biz_db, id).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

#[derive(Deserialize)]
pub struct UpsertBody {
    pub name: Option<String>,
    pub provider: Option<String>,
    pub base_url: Option<String>,
    pub model: Option<String>,
    pub enabled: Option<bool>,
}

pub async fn create(State(state): State<SharedState>, _a: AuthedAdmin, Json(req): Json<UpsertBody>) -> Response {
    let r = svc::GatewayUpsert { name: req.name, provider: req.provider, base_url: req.base_url, model: req.model, enabled: req.enabled };
    match svc::create(&state.biz_db, &r).await {
        Ok(v) => (StatusCode::CREATED, ok_json(v)).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn update(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>, Json(req): Json<UpsertBody>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    let r = svc::GatewayUpsert { name: req.name, provider: req.provider, base_url: req.base_url, model: req.model, enabled: req.enabled };
    match svc::update(&state.biz_db, id, &r).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

pub async fn remove(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::delete(&state.biz_db, id).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn sync_models(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::sync_models(&state.biz_db, id).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

pub async fn test(State(state): State<SharedState>, _a: AuthedAdmin, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::test(&state.biz_db, id).await { Ok(v) => ok_json(v), Err(e) => err_resp(e) }
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{get, post};
    axum::Router::new()
        .route("/api/admin/gateway/configs", get(list).post(create))
        .route("/api/admin/gateway/configs/{id}", get(get_config).put(update).delete(remove))
        .route("/api/admin/gateway/configs/{id}/sync-models", post(sync_models))
        .route("/api/admin/gateway/configs/{id}/test", post(test))
        .with_state(GwState { shared: state, jwt })
}

#[derive(Clone)]
pub struct GwState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<GwState> for SharedState {
    fn from_ref(s: &GwState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<GwState> for identity::security::JwtProvider {
    fn from_ref(s: &GwState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
