//! order REST 层:createOrder / 详情 / 列表 / 取消。

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;
use identity::api::store_account::AuthedUser;

use crate::order as svc;
use crate::service_category::CatalogError;

common::error_site!(SITE_ORDER_CREATE = "trading/order/create");
common::error_site!(SITE_ORDER_DETAIL = "trading/order/detail");
common::error_site!(SITE_ORDER_CANCEL = "trading/order/cancel");

fn err_resp(e: CatalogError) -> Response {
    let status = axum::http::StatusCode::from_u16(crate::service_category::cat_err::http_status(e.code))
        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
    (status, Json(json!({"code": e.code, "message": common::i18n::message(e.code, common::i18n::Locale::Zh), "service_id": null, "data": e.details}))).into_response()
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

/// POST /api/store/orders/{id}/confirm-delivery(SHIPPED/DELIVERED → COMPLETED;非法态 409602)
pub async fn confirm_delivery(State(state): State<SharedState>, user: AuthedUser, Path(id): Path<String>) -> Response {
    let (Ok(id), cid) = (id.parse::<i64>(), customer_id(&user)) else {
        return err_resp(CatalogError::new(404601));
    };
    match svc::confirm_delivery(&state.biz_db, &state.db, cid, id).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

/// POST /api/store/orders/{id}/reorder(行商品回购物车;added_count + skipped[reason_code])
pub async fn reorder(State(state): State<SharedState>, user: AuthedUser, Path(id): Path<String>) -> Response {
    let (Ok(id), cid) = (id.parse::<i64>(), customer_id(&user)) else {
        return err_resp(CatalogError::new(404601));
    };
    match svc::reorder(&state.biz_db, cid, id).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub fn router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{get, post};
    axum::Router::new()
        .route("/api/store/orders", get(list_orders).post(create_order))
        // Java 契约别名:前端 checkout 流调 /api/store/checkout/orders(同一 create_order handler)
        .route("/api/store/checkout/orders", post(create_order))
        .route("/api/store/orders/{id}/cancel", post(cancel_order))
        .route("/api/store/orders/{id}/payment-intent", post(crate::api_payment::payment_intent))
        .route("/api/store/orders/{id}/confirm-delivery", post(confirm_delivery))
        .route("/api/store/orders/{id}/reorder", post(reorder))
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

// ══════════ admin 订单列表(Java AdminOrderService.pageList 移植) ══════════

#[derive(Clone)]
pub struct AdminOrderState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<AdminOrderState> for SharedState {
    fn from_ref(s: &AdminOrderState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<AdminOrderState> for identity::security::JwtProvider {
    fn from_ref(s: &AdminOrderState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct AdminOrderQuery {
    pub page: Option<i64>,
    pub page_size: Option<i64>,
    pub status: Option<i64>,
    pub search: Option<String>,
    pub currency: Option<String>,
    pub from: Option<String>,
    pub to: Option<String>,
    pub production_stage: Option<i64>,
    pub wedding_before: Option<String>,
    pub has_shipment: Option<bool>,
}

/// GET /api/admin/orders(RBAC:/orders;Java AdminOrderService.pageList 多筛选+Paginated 信封)
pub async fn admin_list(
    State(state): State<AdminOrderState>,
    a: AuthedAdmin,
    Query(q): Query<AdminOrderQuery>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state.shared, &a, "/orders").await {
        return *r;
    }
    let page = q.page.unwrap_or(1).max(1);
    let page_size = match q.page_size {
        Some(p) if (1..=100).contains(&p) => p,
        _ => 20,
    };
    match svc::order_page_admin(
        &state.shared.biz_db,
        page,
        page_size,
        q.status,
        q.search.as_deref(),
        q.currency.as_deref(),
        q.from.as_deref(),
        q.to.as_deref(),
        q.production_stage,
        q.wedding_before.as_deref(),
        q.has_shipment,
    )
    .await
    {
        Ok((items, total)) => ok_json(json!({
            "data": items, "total_elements": total, "page_number": page, "page_size": page_size,
        })),
        Err(e) => err_resp(e),
    }
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    axum::Router::new()
        .route("/api/admin/orders", axum::routing::get(admin_list))
        .route("/api/admin/orders/export", axum::routing::get(admin_order_export))
        .route(
            "/api/admin/orders/{id}",
            axum::routing::get(admin_order_detail).patch(admin_order_toggle_status),
        )
        .route("/api/admin/orders/{id}/notes", axum::routing::post(admin_order_add_note))
        .route("/api/admin/orders/{id}/status", axum::routing::patch(admin_order_toggle_status))
        .route("/api/admin/orders/{id}/production-stage", axum::routing::patch(admin_order_production_stage))
        .route("/api/admin/orders/{id}/ship", axum::routing::post(admin_order_ship))
        .route("/api/admin/orders/{id}/refunds", axum::routing::post(admin_order_refund_create))
        .with_state(AdminOrderState { shared: state, jwt })
}

// ─────────────── admin 订单详情/备注/状态/制作阶段(RBAC /orders)───────────────

#[derive(Deserialize)]
pub struct AdminNoteBody {
    pub content: String,
    #[serde(rename = "customerVisible")]
    pub customer_visible: Option<bool>,
}

#[derive(Deserialize)]
pub struct AdminStatusBody {
    pub status: i64,
}

#[derive(Deserialize)]
pub struct AdminStageBody {
    pub stage: i64,
}

pub async fn admin_order_detail(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/orders").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404601));
    };
    match svc::order_admin_detail(&state.biz_db, &state.db, id).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_order_add_note(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<AdminNoteBody>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/orders").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404601));
    };
    match svc::order_admin_add_note(&state.biz_db, id, &req.content, req.customer_visible.unwrap_or(false), &a.claims.sub).await {
        Ok(v) => (StatusCode::CREATED, Json(json!({"code": 0, "message": null, "service_id": null, "data": v}))).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_order_toggle_status(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<AdminStatusBody>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/orders").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404601));
    };
    match svc::order_admin_toggle_status(&state.biz_db, &state.db, id, req.status, &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_order_production_stage(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<AdminStageBody>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/orders").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404601));
    };
    match svc::order_admin_production_stage(&state.biz_db, &state.db, id, req.stage, &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

// ─────────────── admin ship/refund-create/export(RBAC /orders)───────────────

#[derive(Deserialize)]
pub struct AdminShipBody {
    pub carrier: String,
    #[serde(rename = "trackingNo")]
    pub tracking_no: Option<String>,
}

#[derive(Deserialize)]
pub struct AdminRefundBody {
    pub amount: Option<f64>,
    pub reason: Option<String>,
}

pub async fn admin_order_ship(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<AdminShipBody>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/orders").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404601));
    };
    match svc::order_admin_ship(&state.biz_db, &state.db, id, &req.carrier, req.tracking_no.as_deref(), &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_order_refund_create(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<AdminRefundBody>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/orders").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404601));
    };
    match svc::order_admin_refund_create(&state.biz_db, &state.db, id, req.amount, req.reason.as_deref()).await {
        Ok(v) => (StatusCode::CREATED, Json(json!({"code": 0, "message": null, "service_id": null, "data": v}))).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_order_export(State(state): State<SharedState>, a: AuthedAdmin) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/orders").await {
        return *r;
    }
    match svc::order_admin_export(&state.biz_db).await {
        Ok((content, _truncated, filename, _rows)) => {
            let mut resp = (StatusCode::OK, content).into_response();
            let headers = resp.headers_mut();
            headers.insert(
                axum::http::header::CONTENT_TYPE,
                axum::http::HeaderValue::from_static("text/csv; charset=UTF-8"),
            );
            if let Ok(hv) = axum::http::HeaderValue::from_str(&format!("attachment; filename=\"{}\"", filename)) {
                headers.insert(axum::http::header::CONTENT_DISPOSITION, hv);
            }
            resp
        }
        Err(e) => err_resp(e),
    }
}
