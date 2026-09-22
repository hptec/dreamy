//! shippingrate/carrier admin REST 层。

use axum::extract::{Query, State};
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::service_category::CatalogError;
use crate::shipping_admin as svc;

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

pub async fn carrier_list(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match svc::carrier_list(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => ok_json(json!({ "items": [] })),
    }
}

pub async fn option_list(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match svc::option_list(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => ok_json(json!({ "items": [] })),
    }
}

pub async fn rate_list(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match svc::rate_list(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => ok_json(json!({ "items": [] })),
    }
}

pub async fn countries(State(state): State<SharedState>) -> Response {
    match svc::countries(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => ok_json(json!({ "items": [] })),
    }
}

#[derive(Deserialize)]
pub struct PreviewQuery {
    pub country: Option<String>,
    pub subtotal_usd: Option<f64>,
}

pub async fn quote_preview(
    State(state): State<SharedState>,
    _a: AuthedAdmin,
    Query(q): Query<PreviewQuery>,
) -> Response {
    let country = q.country.as_deref().unwrap_or("United States");
    let subtotal = q.subtotal_usd.unwrap_or(100.0);
    match svc::quote_preview(&state.biz_db, country, subtotal).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => ok_json(json!({ "items": [] })),
    }
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::get;
    axum::Router::new()
        .route("/api/admin/shipping/carriers", get(carrier_list))
        .route("/api/admin/shipping/options", get(option_list))
        .route("/api/admin/shipping/rates", get(rate_list))
        .route("/api/admin/shipping/quote-preview", get(quote_preview))
        .with_state(ShipAdminState { shared: state, jwt })
}

pub fn countries_router() -> axum::Router<SharedState> {
    axum::Router::new().route("/api/store/shipping/countries", axum::routing::get(countries_public))
}

async fn countries_public(State(state): State<SharedState>) -> Response {
    match svc::countries(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => ok_json(json!({ "items": [] })),
    }
}

#[derive(Clone)]
pub struct ShipAdminState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<ShipAdminState> for SharedState {
    fn from_ref(s: &ShipAdminState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<ShipAdminState> for identity::security::JwtProvider {
    fn from_ref(s: &ShipAdminState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
