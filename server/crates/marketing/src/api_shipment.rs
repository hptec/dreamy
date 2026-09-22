//! shipment REST 层:admin 发货全操作 + 游客查单。

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::service_category::CatalogError;
use crate::shipment as svc;

common::error_site!(SITE_ADMIN_SHIPMENT = "trading/shipment/admin");

fn err_resp(e: CatalogError) -> Response {
    let status = axum::http::StatusCode::from_u16(crate::service_category::cat_err::http_status(e.code))
        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
    (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

#[derive(Deserialize)]
pub struct ShipmentCreateBody {
    pub carrier_code: Option<String>,
    pub tracking_no: Option<String>,
    pub lines: Option<Vec<svc::ShipmentLineIn>>,
}

pub async fn create(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(order_id): Path<String>,
    Json(req): Json<ShipmentCreateBody>,
) -> Response {
    let Ok(order_id) = order_id.parse::<i64>() else {
        return err_resp(svc::not_found());
    };
    let r = svc::ShipmentCreate { carrier_code: req.carrier_code, tracking_no: req.tracking_no, lines: req.lines };
    match svc::create(&state.biz_db, &state.db, order_id, r, &a.claims.sub).await {
        Ok(v) => {
            (StatusCode::CREATED, Json(json!({"code": 0, "message": null, "service_id": null, "data": v}))).into_response()
        }
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct PatchBody {
    pub carrier_code: Option<String>,
    pub tracking_no: Option<String>,
}

pub async fn patch(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<PatchBody>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc::not_found());
    };
    match svc::patch(&state.biz_db, &state.db, id, req.carrier_code, req.tracking_no, &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct EventBody {
    pub status: Option<i64>,
    pub occurred_at: Option<String>,
    pub location: Option<String>,
    pub description: Option<String>,
}

pub async fn add_event(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<EventBody>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc::not_found());
    };
    match svc::add_event(&state.biz_db, &state.db, id, req.status, req.occurred_at, req.location, req.description, &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn deliver(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc::not_found());
    };
    match svc::deliver(&state.biz_db, &state.db, id, &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn cancel(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc::not_found());
    };
    match svc::cancel(&state.biz_db, &state.db, id, &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

/// 游客查单(order-flow-complete D;订单号+邮箱)
#[derive(Deserialize)]
pub struct TrackBody {
    pub order_no: Option<String>,
    pub email: Option<String>,
}

pub async fn track(State(state): State<SharedState>, Json(req): Json<TrackBody>) -> Response {
    let Some(order_no) = req.order_no.as_deref().map(str::trim).filter(|s| !s.is_empty()) else {
        return err_resp(CatalogError::field_validation(&[("order_no", "required")]));
    };
    let Some(email) = req.email.as_deref().map(str::trim).filter(|s| !s.is_empty()) else {
        return err_resp(CatalogError::field_validation(&[("email", "required")]));
    };
    match svc::track(&state.biz_db, &state.db, order_no, email).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::post;
    axum::Router::new()
        .route("/api/admin/orders/{id}/shipments", post(create))
        .route("/api/admin/shipments/{id}", axum::routing::patch(patch))
        .route("/api/admin/shipments/{id}/cancel", axum::routing::post(cancel))
        .route("/api/admin/shipments/{id}/events", post(add_event))
        .route("/api/admin/shipments/{id}/deliver", post(deliver))
        .with_state(ShipState { shared: state, jwt })
}

pub fn track_router() -> axum::Router<SharedState> {
    axum::Router::new().route("/api/store/orders/track", axum::routing::post(track))
}

#[derive(Clone)]
pub struct ShipState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<ShipState> for SharedState {
    fn from_ref(s: &ShipState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<ShipState> for identity::security::JwtProvider {
    fn from_ref(s: &ShipState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
