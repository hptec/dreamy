//! checkout REST 层:E-quoteCheckout(store 鉴权)。

use axum::extract::State;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::store_account::AuthedUser;

use crate::checkout as svc;
use crate::service_category::CatalogError;

common::error_site!(SITE_CHECKOUT_QUOTE = "trading/checkout/quote");

fn err_resp(e: CatalogError) -> Response {
    let status = axum::http::StatusCode::from_u16(crate::service_category::cat_err::http_status(e.code))
        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
    (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

pub async fn quote(
    State(state): State<SharedState>,
    user: AuthedUser,
    Json(req): Json<svc::QuoteRequest>,
) -> Response {
    let customer_id: i64 = user.claims.sub.parse().unwrap_or(0);
    match svc::quote(&state.biz_db, customer_id, &req).await {
        Ok(v) => ok_json(json!(v)),
        Err(e) => err_resp(e),
    }
}

pub fn router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::post;
    axum::Router::new()
        .route("/api/store/checkout/quote", post(quote))
        .with_state(QuoteState { shared: state, jwt })
}

#[derive(Clone)]
pub struct QuoteState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<QuoteState> for SharedState {
    fn from_ref(s: &QuoteState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<QuoteState> for identity::security::JwtProvider {
    fn from_ref(s: &QuoteState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
