//! coupon REST 层:admin 列表 + validate(试算)。

use axum::extract::State;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;
use identity::api::store_account::AuthedUser;

use crate::coupon as svc;
use crate::service_category::CatalogError;

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

fn err_resp(e: CatalogError) -> Response {
    let status = axum::http::StatusCode::from_u16(crate::service_category::cat_err::http_status(e.code))
        .unwrap_or(axum::http::StatusCode::INTERNAL_SERVER_ERROR);
    (status, Json(json!({"code": e.code, "message": common::i18n::message(e.code, common::i18n::Locale::Zh), "service_id": null, "data": e.details}))).into_response()
}

/// E-MKT-10 请求体(V-MKT-007/008;前端 body {code, subtotal},subtotal USD 基准)
#[derive(Deserialize)]
pub struct CouponValidateBody {
    pub code: Option<String>,
    pub subtotal: Option<f64>,
}

/// V-MKT-007 code trim 大写归一后 ^[A-Z0-9]{1,32}$
fn valid_code(code: &str) -> bool {
    !code.is_empty()
        && code.len() <= 32
        && code.chars().all(|c| c.is_ascii_uppercase() || c.is_ascii_digit())
}

/// POST /api/store/promotions/coupons/validate(StoreBearerAuth;
/// 券不可用一律 200 + valid=false + reason_code(422701/422702/422703)不抛错;
/// 仅请求格式校验失败抛 422704。核销不在本端点(trading 下单事务内直调 redeem))
pub async fn validate_coupon(
    State(state): State<SharedState>,
    _u: AuthedUser,
    Json(req): Json<CouponValidateBody>,
) -> Response {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let code = req.code.as_deref().unwrap_or("").trim().to_uppercase();
    if code.is_empty() {
        fields.push(("code", "required"));
    } else if !valid_code(&code) {
        fields.push(("code", "pattern_invalid"));
    }
    if req.subtotal.is_none() {
        fields.push(("subtotal", "required"));
    } else if req.subtotal.unwrap_or(0.0) < 0.0 {
        fields.push(("subtotal", "range_invalid"));
    }
    if !fields.is_empty() {
        return err_resp(CatalogError::field_validation(&fields)); // 422704
    }
    match svc::store_validate(&state.biz_db, &code, req.subtotal.unwrap_or(0.0)).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

/// store 路由(E-MKT-10;Java StoreBearerAuth 同款不入白名单鉴权,不缓存)
pub fn store_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    axum::Router::new()
        .route(
            "/api/store/promotions/coupons/validate",
            axum::routing::post(validate_coupon),
        )
        .with_state(CouponState { shared: state, jwt })
}

pub async fn admin_list(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match svc::admin_list(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => {
            let status = axum::http::StatusCode::from_u16(svc::http_status_coupon(e.code))
                .unwrap_or(axum::http::StatusCode::INTERNAL_SERVER_ERROR);
            (status, Json(json!({"code": e.code, "message": common::i18n::message(e.code, common::i18n::Locale::Zh), "service_id": null, "data": e.details}))).into_response()
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
