//! coupon REST 层:admin 列表 + validate(试算)。

use axum::extract::State;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::coupon as svc;
use crate::service_category::CatalogError;

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

pub async fn admin_list(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match svc::admin_list(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => {
            let status = axum::http::StatusCode::from_u16(svc::http_status_coupon(e.code))
                .unwrap_or(axum::http::StatusCode::INTERNAL_SERVER_ERROR);
            (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
        }
    }
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    axum::Router::new()
        .route("/api/admin/promotions/coupons", axum::routing::get(admin_list))
        .with_state(CouponState { shared: state, jwt })
}

#[derive(Clone)]
pub struct CouponState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<CouponState> for SharedState {
    fn from_ref(s: &CouponState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<CouponState> for identity::security::JwtProvider {
    fn from_ref(s: &CouponState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
