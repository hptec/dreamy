//! trading 底座 REST 层:地址簿(对齐 StoreAddressController)。
//! wishlist/browse 依赖 catalog 快照(product 域迁移后接线,本批先不挂载路由)。
//! 鉴权:复用 identity::api::AuthedUser(customer_id = store JWT subject)。

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde_json::json;

use common::error::ErrorCode;
use common::state::SharedState;
use identity::api::store_account::AuthedUser;

use crate::service_address::{self, AddressUpsert};
use crate::trading_error::TradingError;

common::error_site!(SITE_ADDRESS_LIST = "trading/address/list");
common::error_site!(SITE_ADDRESS_CREATE = "trading/address/create");
common::error_site!(SITE_ADDRESS_UPDATE = "trading/address/update");
common::error_site!(SITE_ADDRESS_DELETE = "trading/address/delete");

/// TradingError → R 包络(对齐 Java TradingExceptionHandler:code=i32,details→data)
impl IntoResponse for TradingError {
    fn into_response(self) -> Response {
        let status = axum::http::StatusCode::from_u16(crate::trading_error::http_status(self.code))
            .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
        let body = json!({
            "code": self.code,
            "message": null,
            "service_id": null,
            "data": self.details,
        });
        (status, Json(body)).into_response()
    }
}

fn ok_json(v: serde_json::Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

fn internal(site: &'static str) -> Response {
    common::error::BizError::new(site, ErrorCode::Internal).into_response()
}

pub async fn list_addresses(
    State(state): State<SharedState>,
    user: AuthedUser,
) -> Response {
    let customer_id: i64 = user.claims.sub.parse().unwrap_or(0);
    match service_address::list(&state.biz_db, customer_id).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => {
            if crate::trading_error::http_status(e.code) >= 500 {
                return internal(SITE_ADDRESS_LIST);
            }
            e.into_response()
        }
    }
}

pub async fn create_address(
    State(state): State<SharedState>,
    user: AuthedUser,
    Json(req): Json<AddressUpsert>,
) -> Response {
    let customer_id: i64 = user.claims.sub.parse().unwrap_or(0);
    match service_address::create(&state.biz_db, customer_id, req).await {
        Ok(dto) => {
            let body = json!({"code": 0, "message": null, "service_id": null, "data": dto});
            (StatusCode::CREATED, Json(body)).into_response()
        }
        Err(e) => {
            if crate::trading_error::http_status(e.code) >= 500 {
                return internal(SITE_ADDRESS_CREATE);
            }
            e.into_response()
        }
    }
}

pub async fn update_address(
    State(state): State<SharedState>,
    user: AuthedUser,
    Path(id): Path<u64>,
    Json(req): Json<AddressUpsert>,
) -> Response {
    let customer_id: i64 = user.claims.sub.parse().unwrap_or(0);
    match service_address::update(&state.biz_db, customer_id, id, req).await {
        Ok(dto) => ok_json(json!(dto)),
        Err(e) => {
            if crate::trading_error::http_status(e.code) >= 500 {
                return internal(SITE_ADDRESS_UPDATE);
            }
            e.into_response()
        }
    }
}

pub async fn delete_address(
    State(state): State<SharedState>,
    user: AuthedUser,
    Path(id): Path<u64>,
) -> Response {
    let customer_id: i64 = user.claims.sub.parse().unwrap_or(0);
    match service_address::delete(&state.biz_db, customer_id, id).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => {
            if crate::trading_error::http_status(e.code) >= 500 {
                return internal(SITE_ADDRESS_DELETE);
            }
            e.into_response()
        }
    }
}

/// 地址簿路由(挂 /api/store 下;路径与 Java 逐字一致)
pub fn address_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{delete, get, post, put};
    axum::Router::new()
        .route("/api/store/addresses", get(list_addresses).post(create_address))
        .route("/api/store/addresses/{id}", put(update_address).delete(delete_address))
        .with_state(StoreState { shared: state, jwt })
}

/// 与 identity::api::store_account 同构的状态(复用 AuthedUser extractor 的 FromRef)
#[derive(Clone)]
pub struct StoreState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<StoreState> for SharedState {
    fn from_ref(s: &StoreState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<StoreState> for identity::security::JwtProvider {
    fn from_ref(s: &StoreState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
