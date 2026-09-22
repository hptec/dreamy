//! store 收尾 REST 层:wishlist/browse-history/uploads presign。

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::store_account::AuthedUser;

use crate::service_category::CatalogError;
use crate::store_extras as svc;

fn err_resp(e: CatalogError) -> Response {
    (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

fn uid(user: &AuthedUser) -> i64 {
    user.claims.sub.parse().unwrap_or(0)
}

pub async fn wishlist_list(State(state): State<SharedState>, user: AuthedUser) -> Response {
    match svc::wishlist_list(&state.biz_db, uid(&user)).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn wishlist_add(State(state): State<SharedState>, user: AuthedUser, Path(product_id): Path<i64>) -> Response {
    match svc::wishlist_add(&state.biz_db, uid(&user), product_id).await {
        Ok(()) => (StatusCode::CREATED, ok_json(json!({"added": true}))).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn wishlist_remove(State(state): State<SharedState>, user: AuthedUser, Path(product_id): Path<i64>) -> Response {
    match svc::wishlist_remove(&state.biz_db, uid(&user), product_id).await {
        Ok(true) => StatusCode::NO_CONTENT.into_response(),
        Ok(false) => (StatusCode::NOT_FOUND, Json(json!({"code": 404501, "message": null, "service_id": null, "data": null}))).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn browse_list(State(state): State<SharedState>, user: AuthedUser) -> Response {
    match svc::browse_list(&state.biz_db, uid(&user), 20).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct BrowseBody {
    pub product_id: Option<i64>,
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
        .route("/api/store/wishlists", get(wishlist_list))
        .route("/api/store/wishlists/{product_id}", put(wishlist_add).delete(wishlist_remove))
        .route("/api/store/browse-history", get(browse_list).put(browse_record))
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
