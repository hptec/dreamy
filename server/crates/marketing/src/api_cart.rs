//! cart REST 层(trading-api-detail §1):getCart/add/update(PATCH)/remove/merge。

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::store_account::AuthedUser;

use crate::cart as svc;
use crate::service_category::CatalogError;

common::error_site!(SITE_CART_GET = "trading/cart/get");
common::error_site!(SITE_CART_ADD = "trading/cart/add");
common::error_site!(SITE_CART_UPDATE = "trading/cart/update");
common::error_site!(SITE_CART_REMOVE = "trading/cart/remove");
common::error_site!(SITE_CART_MERGE = "trading/cart/merge");

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

pub async fn get_cart(State(state): State<SharedState>, user: AuthedUser) -> Response {
    match svc::get_cart(&state.biz_db, customer_id(&user)).await {
        Ok(v) => ok_json(json!(v)),
        Err(e) => err_resp(e),
    }
}

pub async fn add_item(
    State(state): State<SharedState>,
    user: AuthedUser,
    Json(req): Json<svc::CartItemCreate>,
) -> Response {
    match svc::add_item(&state.biz_db, customer_id(&user), req).await {
        Ok(v) => ok_json(json!(v)),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct QtyBody {
    pub qty: Option<i64>,
}

pub async fn update_item(
    State(state): State<SharedState>,
    user: AuthedUser,
    Path(id): Path<String>,
    Json(req): Json<QtyBody>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404603));
    };
    match svc::update_item(&state.biz_db, customer_id(&user), id, req.qty).await {
        Ok(v) => ok_json(json!(v)),
        Err(e) => err_resp(e),
    }
}

pub async fn remove_item(
    State(state): State<SharedState>,
    user: AuthedUser,
    Path(id): Path<String>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404603));
    };
    match svc::remove_item(&state.biz_db, customer_id(&user), id).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct MergeBody {
    pub anon_token: Option<String>,
    pub items: Option<Vec<svc::CartItemCreate>>,
}

pub async fn merge(
    State(state): State<SharedState>,
    user: AuthedUser,
    Json(req): Json<MergeBody>,
) -> Response {
    match svc::merge(&state.biz_db, customer_id(&user), req.anon_token, req.items).await {
        Ok(v) => ok_json(json!(v)),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct LocaleQ {
    pub locale: Option<String>,
}

pub fn router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{delete, get, post};
    axum::Router::new()
        .route("/api/store/cart", get(get_cart))
        .route("/api/store/cart/items", post(add_item))
        .route("/api/store/cart/items/{id}", axum::routing::patch(update_item).delete(axum::routing::delete(remove_item)))
        .route("/api/store/cart/merge", post(merge))
        .with_state(CartState { shared: state, jwt })
}

#[derive(Clone)]
pub struct CartState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<CartState> for SharedState {
    fn from_ref(s: &CartState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<CartState> for identity::security::JwtProvider {
    fn from_ref(s: &CartState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
