//! payment/refund REST 层:确认/申请/审批/列表。

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;
use identity::api::store_account::AuthedUser;

use crate::payment as svc;
use sea_orm::ConnectionTrait;

use crate::service_category::CatalogError;

common::error_site!(SITE_PAYMENT_CONFIRM = "trading/payment/confirm");
common::error_site!(SITE_REFUND_APPLY = "trading/refund/apply");
common::error_site!(SITE_REFUND_ADMIN = "trading/refund/admin");

fn err_resp(e: CatalogError) -> Response {
    let status = axum::http::StatusCode::from_u16(crate::service_category::cat_err::http_status(e.code))
        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
    (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

pub async fn confirm_payment(
    State(state): State<SharedState>,
    user: AuthedUser,
    Path(id): Path<String>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404601));
    };
    match svc::confirm_stub_payment(&state.biz_db, &state.db, user.claims.sub.parse().unwrap_or(0), id).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct RefundQuery {
    pub status: Option<i64>,
}

pub async fn admin_refund_list(
    State(state): State<SharedState>,
    _a: AuthedAdmin,
    Query(q): Query<RefundQuery>,
) -> Response {
    match svc::admin_refund_list(&state.biz_db, q.status).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_approve(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404906));
    };
    match svc::admin_approve_refund(&state.biz_db, &state.db, id, &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_reject(
    State(state): State<SharedState>,
    _a: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    // 拒绝:状态机 1→3(简化实现;完整 reject_reason 随下批)
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404906));
    };
    match sqlx_reject(&state.biz_db, id).await {
        Ok(()) => axum::http::StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

async fn sqlx_reject(db: &sea_orm::DatabaseConnection, id: i64) -> Result<(), CatalogError> {
    let res = db
        .execute(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!("UPDATE refund SET status = 3, updated_at = NOW(3) WHERE id = {id} AND status = 1"),
        ))
        .await
        .map_err(|e| CatalogError::new(500601))?;
    if res.rows_affected() == 0 {
        return Err(CatalogError::new(404906));
    }
    Ok(())
}

#[derive(Deserialize)]
pub struct RefundApplyBody {
    pub amount: Option<f64>,
    pub reason: Option<String>,
}

pub async fn apply_refund(
    State(state): State<SharedState>,
    user: AuthedUser,
    Path(id): Path<String>,
    Json(req): Json<RefundApplyBody>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404601));
    };
    let req = svc::RefundApply { amount: req.amount, reason: req.reason };
    match svc::apply_refund(&state.biz_db, &state.db, user.claims.sub.parse().unwrap_or(0), id, req).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub fn store_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    axum::Router::new()
        .route("/api/store/orders/{id}/payment/confirm", axum::routing::post(confirm_payment))
        .route("/api/store/orders/{id}/refunds", axum::routing::post(apply_refund))
        .with_state(PayState { shared: state, jwt })
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{get, post};
    axum::Router::new()
        .route("/api/admin/refunds", get(admin_refund_list))
        .route("/api/admin/refunds/{id}/approve", post(admin_approve))
        .route("/api/admin/refunds/{id}/reject", post(admin_reject))
        .with_state(PayState { shared: state, jwt })
}

#[derive(Clone)]
pub struct PayState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<PayState> for SharedState {
    fn from_ref(s: &PayState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<PayState> for identity::security::JwtProvider {
    fn from_ref(s: &PayState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
