//! product admin REST 层。

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::product::admin as svc;
use crate::service_category::CatalogError;

fn err_resp(e: CatalogError) -> Response {
    // 码表 → HTTP 状态(对齐 api_category.rs/CatalogErrorCode:404→404,409→409,422→422)
    let status = StatusCode::from_u16(crate::service_category::cat_err::http_status(e.code))
        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
    (status, Json(json!({"code": e.code, "message": common::i18n::message(e.code, common::i18n::Locale::Zh), "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

#[derive(Deserialize)]
pub struct ListQuery {
    pub status: Option<i64>,
    pub category_id: Option<i64>,
    pub keyword: Option<String>,
    pub page: Option<i64>,
    #[serde(rename = "page_size")]
    pub page_size: Option<i64>,
}

pub async fn list(State(state): State<SharedState>, a: AuthedAdmin, Query(q): Query<ListQuery>) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/products").await {
        return *r;
    }
    let page = q.page.unwrap_or(1).max(1);
    let page_size = match q.page_size {
        Some(p) if (1..=100).contains(&p) => p,
        _ => 20,
    };
    match svc::page_list(&state.biz_db, q.status, q.keyword.as_deref(), page, page_size).await {
        Ok((items, total)) => ok_json(json!({
            "data": items, "total_elements": total, "page_number": page, "page_size": page_size,
        })),
        Err(e) => err_resp(e),
    }
}

pub async fn get_product(State(state): State<SharedState>, a: AuthedAdmin, Path(id): Path<String>) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/products").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::get(&state.biz_db, id).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct StatusBody {
    pub status: Option<i64>,
}

pub async fn toggle_status(State(state): State<SharedState>, a: AuthedAdmin, Path(id): Path<String>, Json(req): Json<StatusBody>) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/products").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    let Some(status) = req.status else { return err_resp(CatalogError::field_validation(&[("status", "required")])); };
    match svc::toggle_status(&state.biz_db, id, status).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct FlagsBody {
    pub is_new: Option<bool>,
    pub is_best: Option<bool>,
    pub recommend: Option<bool>,
    pub sort: Option<i64>,
}

pub async fn patch_flags(State(state): State<SharedState>, a: AuthedAdmin, Path(id): Path<String>, Json(req): Json<FlagsBody>) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/products").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::patch_flags(&state.biz_db, id, req.is_new, req.is_best, req.recommend, req.sort).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn remove(State(state): State<SharedState>, a: AuthedAdmin, Path(id): Path<String>) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/products").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::delete(&state.biz_db, id).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

/// E-CAT-09 创建商品(TX-CAT-001):201 + AdminProductDetail;V-CAT-023~038 校验失败 → 422501
pub async fn create_product(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Json(req): Json<svc::ProductUpsertIn>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/products").await {
        return *r;
    }
    match svc::create(&state.biz_db, &state.db, req, &a.claims.sub).await {
        Ok(v) => (
            StatusCode::CREATED,
            Json(json!({"code": 0, "message": null, "service_id": null, "data": v})),
        )
            .into_response(),
        Err(e) => err_resp(e),
    }
}

/// E-CAT-11 编辑商品(TX-CAT-002):200 + AdminProductDetail;slug 冲突 409501/sku_code 冲突 409504/并发 409508
pub async fn update_product(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<svc::ProductUpsertIn>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/products").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    match svc::update(&state.biz_db, &state.db, id, req, &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

/// Java AdminProductBatchService 移植(E-CAT-01):action ∈ {publish,unpublish,recommend,unrecommend,delete};
/// ≤200 件;逐条独立事务容错(部分失败仍 200);delete 目标不存在幂等成功(BE-DIM-4)。
#[derive(Deserialize)]
pub struct BatchRequest {
    pub action: Option<String>,
    pub ids: Option<Vec<i64>>,
}

#[derive(serde::Serialize)]
pub struct BatchFailure {
    pub id: i64,
    pub code: i32,
    pub message: String,
}

pub async fn batch(State(state): State<SharedState>, a: AuthedAdmin, Json(req): Json<BatchRequest>) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/products").await {
        return *r;
    }
    // V-001 action 必填且枚举合法
    let Some(action) = req.action.as_deref().map(str::trim).filter(|s| !s.is_empty()) else {
        return err_resp(CatalogError::field_validation(&[("action", "required")]));
    };
    let action = match action {
        "publish" => "publish",
        "unpublish" => "unpublish",
        "recommend" => "recommend",
        "unrecommend" => "unrecommend",
        "delete" => "delete",
        _ => return err_resp(CatalogError::field_validation(&[("action", "invalid_enum")])),
    };
    // V-002 ids 必填,1..=200,去重保序
    let Some(raw_ids) = req.ids.clone() else {
        return err_resp(CatalogError::field_validation(&[("ids", "required")]));
    };
    if raw_ids.is_empty() {
        return err_resp(CatalogError::field_validation(&[("ids", "required")]));
    }
    if raw_ids.len() > 200 {
        return err_resp(CatalogError::field_validation(&[("ids", "单次最多 200 件")]));
    }
    let mut ids: Vec<i64> = Vec::new();
    for id in raw_ids {
        if !ids.contains(&id) {
            ids.push(id);
        }
    }

    let mut success_ids: Vec<i64> = Vec::new();
    let mut failures: Vec<BatchFailure> = Vec::new();
    for id in ids {
        let row = match action {
            "publish" => svc::toggle_status(&state.biz_db, id, 2).await.map(|_| ()),
            "unpublish" => svc::toggle_status(&state.biz_db, id, 1).await.map(|_| ()),
            "recommend" => svc::patch_flags(&state.biz_db, id, None, None, Some(true), None).await.map(|_| ()),
            "unrecommend" => svc::patch_flags(&state.biz_db, id, None, None, Some(false), None).await.map(|_| ()),
            _ => svc::delete(&state.biz_db, id).await,
        };
        match row {
            Ok(()) => success_ids.push(id),
            Err(e) if action == "delete" && e.code == 404501 => {
                // EC-CAT-01/BE-DIM-4:delete 目标已不存在 → 幂等成功
                success_ids.push(id);
            }
            Err(e) => failures.push(BatchFailure {
                id,
                code: e.code,
                message: String::new(),
            }),
        }
    }
    ok_json(json!({ "successIds": success_ids, "failures": failures }))
}

/// API-CAT-02 商品 CSV 导出(Java AdminProductExportService;keyset 500/上限 10000/BOM/截断标记)
pub async fn export_csv(State(state): State<SharedState>, a: AuthedAdmin, Query(q): Query<ListQuery>) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/products").await {
        return *r;
    }
    match svc::export(&state.biz_db, q.status, q.category_id, q.keyword.as_deref()).await {
        Ok((content, truncated, filename, rows)) => {
            let mut resp = (StatusCode::OK, content).into_response();
            let headers = resp.headers_mut();
            headers.insert(
                axum::http::header::CONTENT_TYPE,
                axum::http::HeaderValue::from_static("text/csv; charset=UTF-8"),
            );
            if let Ok(hv) = axum::http::HeaderValue::from_str(&format!("attachment; filename=\"{}\"", filename)) {
                headers.insert(axum::http::header::CONTENT_DISPOSITION, hv);
            }
            if truncated {
                headers.insert(
                    axum::http::HeaderName::from_static("x-export-truncated"),
                    axum::http::HeaderValue::from_static("true"),
                );
            }
            let _ = rows;
            resp
        }
        Err(e) => err_resp(e),
    }
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::get;
    axum::Router::new()
        .route("/api/admin/products", get(list).post(create_product))
        .route("/api/admin/products/batch", axum::routing::post(batch))
        .route("/api/admin/products/export", get(export_csv))
        .route("/api/admin/products/{id}", get(get_product).put(update_product).delete(remove))
        .route("/api/admin/products/{id}/status", axum::routing::patch(toggle_status))
        .route("/api/admin/products/{id}/flags", axum::routing::patch(patch_flags))
        .with_state(PAdminState { shared: state, jwt })
}

#[derive(Clone)]
pub struct PAdminState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<PAdminState> for SharedState {
    fn from_ref(s: &PAdminState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<PAdminState> for identity::security::JwtProvider {
    fn from_ref(s: &PAdminState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
