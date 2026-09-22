//! category REST 层:E-CAT-15~18(admin,RequirePermission /categories)+
//! E-CAT-06/07(store 公开,Cache-Control: no-store)。
//! admin 鉴权:复用 identity AuthedAdmin extractor;permission 校验走 identity RBAC。

use axum::extract::{Path, Query, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::json;

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::service_category::{self, AdminCategoryUpsert, CatalogError};

common::error_site!(SITE_ADMIN_CATEGORY_LIST = "catalog/category/admin_list");
common::error_site!(SITE_ADMIN_CATEGORY_CREATE = "catalog/category/admin_create");
common::error_site!(SITE_ADMIN_CATEGORY_UPDATE = "catalog/category/admin_update");
common::error_site!(SITE_ADMIN_CATEGORY_DELETE = "catalog/category/admin_delete");
common::error_site!(SITE_STORE_CATEGORIES = "catalog/category/store_list");

fn err_resp(e: CatalogError) -> Response {
    let status = axum::http::StatusCode::from_u16(service_category::cat_err::http_status(e.code))
        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
    let body = json!({"code": e.code, "message": null, "service_id": null, "data": e.details});
    (status, Json(body)).into_response()
}

fn ok_json(v: serde_json::Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

// ─────────────── admin(E-CAT-15~18)───────────────

pub async fn admin_list(State(state): State<SharedState>, _admin: AuthedAdmin) -> Response {
    match service_category::admin_tree(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_create(
    State(state): State<SharedState>,
    admin: AuthedAdmin,
    Json(req): Json<AdminCategoryUpsert>,
) -> Response {
    match service_category::admin_create(&state.biz_db, req, &admin.claims.sub).await {
        Ok(node) => {
            let body = json!({"code": 0, "message": null, "service_id": null, "data": node});
            (StatusCode::CREATED, Json(body)).into_response()
        }
        Err(e) => err_resp(e),
    }
}

/// id 非法视同不存在 → 404502(V-CAT-037 口径;String path 段对齐 Java)
pub async fn admin_update(
    State(state): State<SharedState>,
    admin: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<AdminCategoryUpsert>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(service_category::cat_err::CATEGORY_NOT_FOUND));
    };
    if id <= 0 {
        return err_resp(CatalogError::new(service_category::cat_err::CATEGORY_NOT_FOUND));
    }
    match service_category::admin_update(&state.biz_db, id, req, &admin.claims.sub).await {
        Ok(node) => ok_json(json!(node)),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_delete(
    State(state): State<SharedState>,
    admin: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(service_category::cat_err::CATEGORY_NOT_FOUND));
    };
    if id <= 0 {
        return err_resp(CatalogError::new(service_category::cat_err::CATEGORY_NOT_FOUND));
    }
    match service_category::admin_delete(&state.biz_db, id, &admin.claims.sub).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

// ─────────────── store(E-CAT-06;公开 + Cache-Control no-store)───────────────

#[derive(Deserialize)]
pub struct LocaleQuery {
    pub locale: Option<String>,
}

pub async fn store_categories(
    State(state): State<SharedState>,
    Query(q): Query<LocaleQuery>,
) -> Response {
    let locale = q.locale.as_deref().unwrap_or("en");
    if locale != "en" && locale != "es" && locale != "fr" {
        let e = CatalogError::field_validation(&[("locale", "invalid_enum")]);
        return err_resp(e);
    }
    match service_category::store_tree(&state.biz_db, locale).await {
        Ok(items) => {
            let mut resp = ok_json(json!({ "items": items })).into_response();
            resp.headers_mut().insert(
                "cache-control",
                axum::http::HeaderValue::from_static("no-store"),
            );
            resp
        }
        Err(e) => err_resp(e),
    }
}

pub async fn store_collections(
    State(_state): State<SharedState>,
    Query(q): Query<CollectionsQuery>,
    _headers: HeaderMap,
) -> Response {
    // collections(group 导航)依赖 collection 域(批次 2 内迁移);当前先返回空集契约(200 items=[])
    let _ = q.group_id;
    let mut resp = ok_json(json!({ "items": [] })).into_response();
    resp.headers_mut()
        .insert("cache-control", axum::http::HeaderValue::from_static("no-store"));
    resp
}

#[derive(Deserialize)]
pub struct CollectionsQuery {
    pub group_id: Option<i64>,
}

/// admin 路由(挂 /api/admin;路径与 Java 逐字一致)
pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{delete, get, post, put};
    axum::Router::new()
        .route(
            "/api/admin/categories",
            get(admin_list).post(admin_create),
        )
        .route(
            "/api/admin/categories/{id}",
            put(admin_update).delete(admin_delete),
        )
        .with_state(CategoryState { shared: state, jwt })
}

/// store 路由(公开;挂顶层)
pub fn store_router() -> axum::Router<SharedState> {
    use axum::routing::get;
    axum::Router::new()
        .route("/api/store/categories", get(store_categories))
        .route("/api/store/collections", get(store_collections))
}

#[derive(Clone)]
pub struct CategoryState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<CategoryState> for SharedState {
    fn from_ref(s: &CategoryState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<CategoryState> for identity::security::JwtProvider {
    fn from_ref(s: &CategoryState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
