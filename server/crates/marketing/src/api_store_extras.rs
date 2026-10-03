//! store 收尾 REST 层:wishlist/browse-history/uploads presign。

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use sea_orm::{ConnectionTrait, DbBackend, Statement};
use identity::api::store_account::unauthorized_body;
use identity::api::store_account::AuthedUser;

use crate::service_category::CatalogError;
use crate::store_extras as svc;

fn err_resp(e: CatalogError) -> Response {
    let status = StatusCode::from_u16(crate::service_category::cat_err::http_status(e.code))
        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
    (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

fn uid(user: &AuthedUser) -> i64 {
    // extractor(AuthedUser)已保证 sub 为数字 customer_id 且会话有效;此处仅是便捷访问
    user.customer_id().unwrap_or(0)
}

pub async fn wishlist_list(State(state): State<SharedState>, user: AuthedUser) -> Response {
    match svc::wishlist_list(&state.biz_db, uid(&user)).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn wishlist_add(State(state): State<SharedState>, user: AuthedUser, Path(product_id): Path<i64>) -> Response {
    match svc::wishlist_add(&state.biz_db, uid(&user), product_id).await {
        Ok(_) => (StatusCode::CREATED, ok_json(json!({"added": true}))).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn wishlist_remove(State(state): State<SharedState>, user: AuthedUser, Path(product_id): Path<i64>) -> Response {
    match svc::wishlist_remove(&state.biz_db, uid(&user), product_id).await {
        Ok(true) => StatusCode::NO_CONTENT.into_response(),
        Ok(false) => (StatusCode::NOT_FOUND, Json(json!({"code": 404604, "message": null, "service_id": null, "data": null}))).into_response(),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct BrowseQuery {
    pub limit: Option<i64>,
}

pub async fn browse_list(State(state): State<SharedState>, user: AuthedUser, Query(q): Query<BrowseQuery>) -> Response {
    // Java 口径:limit 1..=50,越界 422601,缺省 20
    let limit = match q.limit {
        Some(v) if !(1..=50).contains(&v) => return err_resp(CatalogError::new(422601)),
        Some(v) => v,
        None => 20,
    };
    match svc::browse_list(&state.biz_db, uid(&user), limit).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct BrowseBody {
    #[serde(default, alias = "productId")]
    pub product_id: Option<i64>,
}

/// Java 契约为 POST /api/store/wishlists(body productId,camelCase);与 PUT {product_id} 路径版等价。
#[derive(Deserialize)]
pub struct WishlistBody {
    #[serde(default, alias = "productId")]
    pub product_id: Option<i64>,
}

pub async fn wishlist_add_body(State(state): State<SharedState>, user: AuthedUser, Json(req): Json<WishlistBody>) -> Response {
    let Some(pid) = req.product_id else {
        return err_resp(CatalogError::field_validation(&[("productId", "required")]));
    };
    // Java 契约:首次 201,已存在(幂等重放) 200
    match svc::wishlist_add(&state.biz_db, uid(&user), pid).await {
        Ok(true) => (StatusCode::CREATED, ok_json(json!({"added": true}))).into_response(),
        Ok(false) => (StatusCode::OK, ok_json(json!({"added": true}))).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn browse_record(State(state): State<SharedState>, user: AuthedUser, Json(req): Json<BrowseBody>) -> Response {
    let Some(pid) = req.product_id else {
        return err_resp(CatalogError::field_validation(&[("product_id", "required")]));
    };
    match svc::browse_record(&state.biz_db, uid(&user), pid).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct PresignBody {
    pub filename: Option<String>,
    pub content_type: Option<String>,
}

pub async fn presign(Json(req): Json<PresignBody>) -> Response {
    let Some(filename) = req.filename.as_deref().filter(|s| !s.is_empty()) else {
        return err_resp(CatalogError::field_validation(&[("filename", "required")]));
    };
    ok_json(svc::presign(filename, req.content_type.as_deref().unwrap_or("application/octet-stream")))
}

pub fn store_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{get, post, put};
    axum::Router::new()
        .route("/api/store/wishlists", get(wishlist_list).post(wishlist_add_body))
        .route("/api/store/wishlists/{product_id}", put(wishlist_add).delete(wishlist_remove))
        .route("/api/store/wishlists/{product_id}/move-to-cart", post(wishlist_move_to_cart))
        .route("/api/store/browse-history", get(browse_list).put(browse_record).post(browse_record))
        .route("/api/store/uploads/presign", post(presign))
        .route("/api/admin/uploads/presign", post(presign))
        .with_state(ExtraState { shared: state, jwt })
}

#[derive(Clone)]
pub struct ExtraState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<ExtraState> for SharedState {
    fn from_ref(s: &ExtraState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<ExtraState> for identity::security::JwtProvider {
    fn from_ref(s: &ExtraState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
// ══════════ move-to-cart(Java WishlistService.moveToCart;TX-TRD-006 语义:加车全链路 + 删收藏) ══════════

#[derive(Deserialize)]
pub struct MoveBody {
    #[serde(default, alias = "skuId")]
    pub sku_id: Option<i64>,
    #[serde(default, alias = "qty")]
    pub qty: Option<i64>,
    #[serde(default, alias = "customSizeData")]
    pub custom_size_data: Option<crate::cart::CustomSizeData>,
}

pub async fn wishlist_move_to_cart(
    State(state): State<SharedState>,
    user: AuthedUser,
    Path(product_id): Path<i64>,
    Json(req): Json<MoveBody>,
) -> Response {
    let cid = match user.customer_id() {
        Some(v) => v,
        None => return unauthorized_body(),
    };
    let qty = req.qty.unwrap_or(1);
    if qty < 1 {
        return err_resp(CatalogError::field_validation(&[("qty", "range_invalid")]));
    }
    // 收藏行必须存在(Java V-TRD-039:404604)
    let exists = state
        .biz_db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id FROM wishlist_item WHERE customer_id = ? AND product_id = ?",
            [cid.into(), product_id.into()],
        ))
        .await;
    let present = matches!(exists, Ok(Some(_)));
    if !present {
        return err_resp(CatalogError::new(404604));
    }
    // 加车复用全链路(双模式 guard/库存校验/同 sku 累加)
    let create = crate::cart::CartItemCreate {
        product_id: Some(product_id),
        sku_id: req.sku_id,
        qty: Some(qty),
        custom_size_data: req.custom_size_data,
    };
    match crate::cart::add_item(&state.biz_db, cid, create).await {
        Ok(v) => {
            // 加车成功后删收藏(Java TX-TRD-006 的后半段;失败窗口极小,行已存在可重试 DELETE)
            let _ = state.biz_db.execute(Statement::from_sql_and_values(
                DbBackend::MySql,
                "DELETE FROM wishlist_item WHERE customer_id = ? AND product_id = ?",
                [cid.into(), product_id.into()],
            ));
            ok_json(json!(v))
        }
        Err(e) => err_resp(e),
    }
}
