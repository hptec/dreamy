//! collection REST 层:E-CAT-28~37(admin)+ store /api/store/collections 接线。

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::product::collection as svc;
use crate::service_category::CatalogError;

common::error_site!(SITE_ADMIN_GROUPS = "catalog/collection/admin_groups");
common::error_site!(SITE_ADMIN_COLLECTIONS = "catalog/collection/admin_collections");
common::error_site!(SITE_ADMIN_COL_PRODUCTS = "catalog/collection/admin_products");
common::error_site!(SITE_STORE_COLLECTIONS = "catalog/collection/store_nav");

fn err_resp(e: CatalogError) -> Response {
    let status = axum::http::StatusCode::from_u16(crate::service_category::cat_err::http_status(e.code))
        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
    (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

fn with_no_store(resp: Response) -> Response {
    let mut r = resp;
    r.headers_mut()
        .insert("cache-control", axum::http::HeaderValue::from_static("no-store"));
    r
}

// ─────────────── store(E-CAT-07)───────────────

#[derive(Deserialize)]
pub struct StoreCollQuery {
    pub locale: Option<String>,
    pub group_id: Option<i64>,
}

pub async fn store_collections(
    State(state): State<SharedState>,
    Query(q): Query<StoreCollQuery>,
) -> Response {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let locale = match q.locale.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
        None => "en".to_string(),
        Some(l @ ("en" | "es" | "fr")) => l.to_string(),
        Some(_) => {
            fields.push(("locale", "invalid_enum"));
            "en".to_string()
        }
    };
    let group_id = match q.group_id {
        None => None,
        Some(x) if x <= 0 => {
            fields.push(("group_id", "invalid_id"));
            None
        }
        Some(x) => Some(x),
    };
    if !fields.is_empty() {
        return err_resp(CatalogError::field_validation(&fields));
    }
    match svc::store_groups(&state.biz_db, group_id, &locale).await {
        Ok(items) => with_no_store(ok_json(json!({ "items": items }))),
        Err(e) => with_no_store(err_resp(e)),
    }
}

// ─────────────── admin(E-CAT-28~37)───────────────

pub async fn admin_list_groups(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match svc::admin_list_groups(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_create_group(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Json(req): Json<svc::GroupUpsert>,
) -> Response {
    match svc::admin_create_group(&state.biz_db, &state.db, req, &a.claims.sub).await {
        Ok(dto) => {
            (StatusCode::CREATED, Json(json!({"code": 0, "message": null, "service_id": null, "data": dto}))).into_response()
        }
        Err(e) => err_resp(e),
    }
}

pub async fn admin_update_group(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<svc::GroupUpsert>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404505));
    };
    match svc::admin_update_group(&state.biz_db, &state.db, id, req, &a.claims.sub).await {
        Ok(dto) => ok_json(json!(dto)),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_delete_group(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404505));
    };
    match svc::admin_delete_group(&state.biz_db, &state.db, id, &a.claims.sub).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct GroupIdQuery {
    pub group_id: Option<i64>,
}

pub async fn admin_list_collections(
    State(state): State<SharedState>,
    _a: AuthedAdmin,
    Query(q): Query<GroupIdQuery>,
) -> Response {
    match svc::admin_list_collections(&state.biz_db, q.group_id).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_create_collection(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Json(req): Json<svc::CollectionUpsert>,
) -> Response {
    match svc::admin_create_collection(&state.biz_db, &state.db, req, &a.claims.sub).await {
        Ok(dto) => {
            (StatusCode::CREATED, Json(json!({"code": 0, "message": null, "service_id": null, "data": dto}))).into_response()
        }
        Err(e) => err_resp(e),
    }
}

pub async fn admin_update_collection(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<svc::CollectionUpsert>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404505));
    };
    match svc::admin_update_collection(&state.biz_db, &state.db, id, req, &a.claims.sub).await {
        Ok(dto) => ok_json(json!(dto)),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_delete_collection(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404505));
    };
    match svc::admin_delete_collection(&state.biz_db, &state.db, id, &a.claims.sub).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_list_collection_products(
    State(state): State<SharedState>,
    _a: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404505));
    };
    match svc::admin_list_collection_products(&state.biz_db, id).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct ProductsUpsert {
    pub product_ids: Vec<i64>,
}

pub async fn admin_replace_collection_products(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<ProductsUpsert>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404505));
    };
    match svc::admin_replace_collection_products(&state.biz_db, &state.db, id, req.product_ids, &a.claims.sub).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_remove_collection_product(
    State(state): State<SharedState>,
    _a: AuthedAdmin,
    Path((id, product_id)): Path<(String, String)>,
) -> Response {
    let (Ok(id), Ok(pid)) = (id.parse::<i64>(), product_id.parse::<i64>()) else {
        return err_resp(CatalogError::new(404505));
    };
    match svc::admin_remove_collection_product(&state.biz_db, id, pid).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

/// admin collection 路由(绝对路径)
pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{delete, get, post, put};
    axum::Router::new()
        .route("/api/admin/collection-groups", get(admin_list_groups).post(admin_create_group))
        .route("/api/admin/collection-groups/{id}", put(admin_update_group).delete(admin_delete_group))
        .route("/api/admin/collections", get(admin_list_collections).post(admin_create_collection))
        .route("/api/admin/collections/{id}", put(admin_update_collection).delete(admin_delete_collection))
        .route("/api/admin/collections/{id}/products", get(admin_list_collection_products).put(admin_replace_collection_products).post(admin_replace_collection_products))
        .route("/api/admin/collections/{id}/products/{product_id}", delete(admin_remove_collection_product))
        .with_state(CollState { shared: state, jwt })
}

#[derive(Clone)]
pub struct CollState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<CollState> for SharedState {
    fn from_ref(s: &CollState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<CollState> for identity::security::JwtProvider {
    fn from_ref(s: &CollState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}

/// store 集合导航路由(E-CAT-07)
pub fn store_router() -> axum::Router<SharedState> {
    use axum::routing::get;
    axum::Router::new().route("/api/store/collections", get(store_collections))
}
