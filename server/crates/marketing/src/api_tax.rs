//! tax REST 层:税率规则 CRUD(order-flow-complete F)。

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::service_category::CatalogError;
use crate::tax as svc;

common::error_site!(SITE_ADMIN_TAX_RULES = "trading/tax/admin_rules");

fn err_resp(e: CatalogError) -> Response {
    let status = axum::http::StatusCode::from_u16(crate::service_category::cat_err::http_status(e.code))
        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
    (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

#[derive(Deserialize)]
pub struct CountryQuery {
    pub country_code: Option<String>,
}

pub async fn list(State(state): State<SharedState>, _a: AuthedAdmin, Query(q): Query<CountryQuery>) -> Response {
    match svc::list(&state.biz_db, q.country_code).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn create(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Json(req): Json<svc::RuleUpsert>,
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
    Json(req): Json<svc::RuleUpsert>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404906));
    };
    match svc::update(&state.biz_db, &state.db, id, req, &a.claims.sub).await {
        Ok(dto) => ok_json(json!(dto)),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct EnabledPatch {
    pub enabled: Option<bool>,
}

pub async fn set_enabled(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<EnabledPatch>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404906));
    };
    match svc::set_enabled(&state.biz_db, &state.db, id, req.enabled, &a.claims.sub).await {
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
        return err_resp(CatalogError::new(404906));
    };
    match svc::delete(&state.biz_db, &state.db, id, &a.claims.sub).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{get, post, put};
    axum::Router::new()
        .route("/api/admin/tax-rules", get(list).post(create))
        .route("/api/admin/tax-rules/{id}", put(update).delete(axum::routing::delete(delete)))
        .route("/api/admin/tax-rules/{id}/enabled", axum::routing::patch(set_enabled))
        .with_state(TaxState { shared: state, jwt })
}

#[derive(Clone)]
pub struct TaxState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<TaxState> for SharedState {
    fn from_ref(s: &TaxState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<TaxState> for identity::security::JwtProvider {
    fn from_ref(s: &TaxState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
