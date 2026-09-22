//! product REST 层:E-CAT-01~05(store 公开)+ E-CAT-27 filters。
//! 与 Java 逐字对齐:路径/参数名(snake_case)/Cache-Control: no-store/错误码 404501。

use axum::extract::{Path, Query, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;

use crate::product::store as svc;
use crate::service_category::CatalogError;

common::error_site!(SITE_STORE_PRODUCTS = "catalog/product/store_list");
common::error_site!(SITE_STORE_PRODUCT_SEARCH = "catalog/product/store_search");
common::error_site!(SITE_STORE_PRODUCT_DETAIL = "catalog/product/store_detail");
common::error_site!(SITE_STORE_PRODUCT_FILTERS = "catalog/product/store_filters");
common::error_site!(SITE_STORE_RECOMMENDATIONS = "catalog/product/store_recommendations");

fn err_resp(e: CatalogError) -> Response {
    let status = axum::http::StatusCode::from_u16(crate::service_category::cat_err::http_status(e.code))
        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
    let body = json!({"code": e.code, "message": null, "service_id": null, "data": e.details});
    (status, Json(body)).into_response()
}

fn with_no_store(resp: Response) -> Response {
    let mut r = resp;
    r.headers_mut()
        .insert("cache-control", axum::http::HeaderValue::from_static("no-store"));
    r
}

fn ok_json(v: serde_json::Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

/// Paginated 六字段 snake_case 装配(对齐 CatalogPaginatedSupport.of)
fn paginated(items: Vec<Value>, total: i64, page: i64, page_size: i64) -> Value {
    let total_pages = if page_size > 0 {
        (total as f64 / page_size as f64).ceil() as i64
    } else {
        0
    };
    serde_json::json!({
        "data": items,
        "total_elements": total,
        "page_number": page,
        "page_size": page_size,
        "number_of_elements": items.len() as i64,
        "total_pages": total_pages,
    })
}

#[derive(Debug, Deserialize)]
pub struct ListQueryRaw {
    pub locale: Option<String>,
    pub page: Option<i64>,
    #[serde(rename = "page_size")]
    pub page_size: Option<i64>,
    #[serde(rename = "category_id")]
    pub category_id: Option<i64>,
    #[serde(rename = "collection_id")]
    pub collection_id: Option<i64>,
    pub color: Option<String>,
    pub size: Option<String>,
    #[serde(rename = "price_min")]
    pub price_min: Option<f64>,
    #[serde(rename = "price_max")]
    pub price_max: Option<f64>,
    pub sort: Option<String>,
    #[serde(rename = "attr", default)]
    pub attr: Vec<String>,
}

/// E-CAT-01 列表(attr 重复参数由 serde Query 收集为 Vec)
pub async fn list_products(
    State(state): State<SharedState>,
    Query(q): Query<ListQueryRaw>,
) -> Response {
    let params = svc::ListParams {
        locale: q.locale,
        page: q.page,
        page_size: q.page_size,
        category_id: q.category_id,
        collection_id: q.collection_id,
        color: q.color,
        size: q.size,
        price_min: q.price_min,
        price_max: q.price_max,
        sort: q.sort,
        attr: q.attr,
    };
    let parsed = match svc::parse_list(&params) {
        Ok(p) => p,
        Err(e) => return err_resp(e),
    };
    match svc::list_products(&state.biz_db, &parsed).await {
        Ok((items, total)) => with_no_store(ok_json(paginated(items, total, parsed.page, parsed.page_size))),
        Err(e) => with_no_store(err_resp(e)),
    }
}

#[derive(Debug, Deserialize)]
pub struct SearchQueryRaw {
    pub q: Option<String>,
    pub locale: Option<String>,
    pub page: Option<i64>,
    #[serde(rename = "page_size")]
    pub page_size: Option<i64>,
}

/// E-CAT-02 搜索(CDN 不缓存;决策 17)
pub async fn search(
    State(state): State<SharedState>,
    Query(q): Query<SearchQueryRaw>,
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
    let page = match q.page {
        None => 1,
        Some(p) if p < 1 => {
            fields.push(("page", "range_invalid"));
            1
        }
        Some(p) => p,
    };
    let page_size = match q.page_size {
        None => 20,
        Some(p) if !(1..=100).contains(&p) => {
            fields.push(("page_size", "range_invalid"));
            20
        }
        Some(p) => p,
    };
    if !fields.is_empty() {
        return err_resp(CatalogError::field_validation(&fields));
    }
    match svc::search(&state.biz_db, q.q, &locale, page, page_size).await {
        Ok((items, total)) => ok_json(paginated(items, total, page, page_size)),
        Err(e) => err_resp(e),
    }
}

#[derive(Debug, Deserialize)]
pub struct OptionalIdLocale {
    #[serde(rename = "category_id")]
    pub category_id: Option<i64>,
    pub locale: Option<String>,
}

/// E-CAT-27 筛选维度
pub async fn list_filters(
    State(state): State<SharedState>,
    Query(q): Query<OptionalIdLocale>,
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
    let category_id = match q.category_id {
        None => None,
        Some(x) if x <= 0 => {
            fields.push(("category_id", "invalid_id"));
            None
        }
        Some(x) => Some(x),
    };
    if !fields.is_empty() {
        return err_resp(CatalogError::field_validation(&fields));
    }
    match svc::list_filters(&state.biz_db, category_id, &locale).await {
        Ok(items) => with_no_store(ok_json(json!({ "items": items }))),
        Err(e) => with_no_store(err_resp(e)),
    }
}

#[derive(Debug, Deserialize)]
pub struct LocaleOnly {
    pub locale: Option<String>,
}

/// E-CAT-04 PDP(ISR 商品页同源)
pub async fn get_product(
    State(state): State<SharedState>,
    Path(slug): Path<String>,
    Query(q): Query<LocaleOnly>,
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
    if !fields.is_empty() {
        return err_resp(CatalogError::field_validation(&fields));
    }
    match svc::get_product(&state.biz_db, &slug, &locale).await {
        Ok(detail) => with_no_store(ok_json(detail)),
        Err(e) => with_no_store(err_resp(e)),
    }
}

/// store 商品路由(公开;顶层绝对路径)
pub fn store_router() -> axum::Router<SharedState> {
    use axum::routing::{get, post};
    axum::Router::new()
        .route("/api/store/products", get(list_products))
        .route("/api/store/products/search", get(search))
        .route("/api/store/products/filters", get(list_filters))
        .route("/api/store/products/recommendations", get(recommendations))
        .route("/api/store/products/{slug}", get(get_product))
        .route("/api/store/products/{id}/size-recommendation", post(recommend_size))
}

/// E-CAT-03 推荐位(recommendation 域逻辑批次 5 接线;当前回空集契约)
pub async fn recommendations(
    Query(q): Query<RecommendationsQuery>,
) -> Response {
    let _ = (q.block, q.product_id, q.collection_id, q.limit, q.locale);
    with_no_store(ok_json(json!({ "items": [] })))
}

#[derive(Debug, Deserialize)]
pub struct RecommendationsQuery {
    pub block: Option<String>,
    #[serde(rename = "product_id")]
    pub product_id: Option<i64>,
    #[serde(rename = "collection_id")]
    pub collection_id: Option<i64>,
    pub limit: Option<i64>,
    pub locale: Option<String>,
}

/// E-CAT-05 尺码推荐(size chart 规则域批次 5 接线;当前 404501 同口径防探测)
pub async fn recommend_size(
    Path(id): Path<String>,
) -> Response {
    let _ = id;
    err_resp(CatalogError::new(404501))
}

// headers 引用消警(实际无额外 header 读取需求)
#[allow(dead_code)]
fn _hdr(_h: &HeaderMap) {}
