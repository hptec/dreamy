//! order REST 层:createOrder / 详情 / 列表 / 取消。

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::store_account::AuthedUser;

use crate::order as svc;
use crate::service_category::CatalogError;

common::error_site!(SITE_ORDER_CREATE = "trading/order/create");
common::error_site!(SITE_ORDER_DETAIL = "trading/order/detail");
common::error_site!(SITE_ORDER_CANCEL = "trading/order/cancel");

fn err_resp(e: CatalogError) -> Response {
    let status = axum::http::StatusCode::from_u16(crate::service_category::cat_err::http_status(e.code))
        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
    (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

fn customer_id(user: &AuthedUser) -> i64 {
    user.claims.sub.parse().unwrap_or(0)
}

pub async fn create_order(
    State(state): State<SharedState>,
    user: AuthedUser,
    Json(req): Json<svc::OrderCreateRequest>,
) -> Response {
    match svc::create_order(&state.biz_db, &state.db, state.redis.as_ref(), customer_id(&user), &req).await {
        Ok(v) => {
            (StatusCode::CREATED, Json(json!({"code": 0, "message": null, "service_id": null, "data": v}))).into_response()
        }
        Err(e) => err_resp(e),
    }
}

pub async fn order_detail(State(state): State<SharedState>, user: AuthedUser, Path(id): Path<String>) -> Response {
    let (Ok(id), cid) = (id.parse::<i64>(), customer_id(&user)) else {
        return err_resp(CatalogError::new(404906));
    };
    match svc::order_detail(&state.biz_db, &state.db, cid, id).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

async fn order_page_resp(state: SharedState, user: AuthedUser, page: i64, page_size: i64) -> Response {
    match svc::order_page(&state.biz_db, customer_id(&user), page, page_size).await {
        Ok((items, total)) => ok_json(json!({
            "data": items, "total_elements": total, "page_number": page, "page_size": page_size,
            "number_of_elements": items.len() as i64,
            "total_pages": if page_size > 0 { (total as f64 / page_size as f64).ceil() as i64 } else { 0 },
        })),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct PageQuery {
    pub page: Option<i64>,
    #[serde(rename = "page_size")]
    pub page_size: Option<i64>,
}

pub async fn list_orders(
    State(state): State<SharedState>,
    user: AuthedUser,
    axum::extract::Query(q): axum::extract::Query<PageQuery>,
) -> Response {
    let page = q.page.unwrap_or(1).max(1);
    let page_size = match q.page_size {
        Some(p) if (1..=100).contains(&p) => p,
        _ => 20,
    };
    order_page_resp(state, user, page, page_size).await
}

pub async fn cancel_order(State(state): State<SharedState>, user: AuthedUser, Path(id): Path<String>) -> Response {
    let (Ok(id), cid) = (id.parse::<i64>(), customer_id(&user)) else {
        return err_resp(CatalogError::new(404906));
    };
    match svc::cancel_order(&state.biz_db, &state.db, cid, id).await {
        Ok(()) => axum::http::StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub fn router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{get, post};
    axum::Router::new()
        .route("/api/store/orders", get(list_orders).post(create_order))
        .route("/api/store/orders/{id}/cancel", post(cancel_order))
        .route("/api/store/orders/{id}", get(order_detail))
        .with_state(OrderState { shared: state, jwt })
}

#[derive(Clone)]
pub struct OrderState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<OrderState> for SharedState {
    fn from_ref(s: &OrderState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<OrderState> for identity::security::JwtProvider {
    fn from_ref(s: &OrderState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
