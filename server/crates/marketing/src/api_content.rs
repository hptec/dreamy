//! content REST 层:store 公开读(E-MKT-01~08)+ admin 博客 CRUD(E-MKT-24~33)。
//! wedding/lookbook/guide 的 admin 端点同构,随下一批接线(本批 store 读 + blog admin 全量)。

use axum::extract::{Path, Query, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::content as svc;
use crate::service_category::CatalogError;

common::error_site!(SITE_STORE_CONTENT = "marketing/content/store");
common::error_site!(SITE_ADMIN_BLOG = "marketing/content/admin_blog");

fn err_resp(e: CatalogError) -> Response {
    let status = axum::http::StatusCode::from_u16(crate::service_category::cat_err::http_status(e.code))
        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
    (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

fn no_store(resp: Response) -> Response {
    let mut r = resp;
    r.headers_mut()
        .insert("cache-control", axum::http::HeaderValue::from_static("no-store"));
    r
}

fn paginated(items: Vec<Value>, total: i64, page: i64, page_size: i64) -> Value {
    let total_pages = if page_size > 0 { (total as f64 / page_size as f64).ceil() as i64 } else { 0 };
    serde_json::json!({
        "data": items, "total_elements": total, "page_number": page,
        "page_size": page_size, "number_of_elements": items.len() as i64, "total_pages": total_pages,
    })
}

#[derive(Deserialize)]
pub struct LocaleQuery {
    pub locale: Option<String>,
}

fn parse_locale_q(locale: Option<&str>) -> Result<String, CatalogError> {
    match locale.map(str::trim).filter(|s| !s.is_empty()) {
        None => Ok("en".into()),
        Some(l @ ("en" | "es" | "fr")) => Ok(l.into()),
        Some(_) => Err(CatalogError::field_validation(&[("locale", "invalid_enum")])),
    }
}

// ─────────────── store 公开(E-MKT-01~08)───────────────

pub async fn store_banners(State(state): State<SharedState>, Query(q): Query<StoreBannerQuery>, _h: HeaderMap) -> Response {
    let Ok(locale) = parse_locale_q(q.locale.as_deref()) else {
        return err_resp(CatalogError::field_validation(&[("locale", "invalid_enum")]));
    };
    let _ = locale;
    match crate::banner::store_list(&state.biz_db, q.position, &locale).await {
        Ok(items) => no_store(ok_json(json!({ "items": items }))),
        Err(e) => no_store(err_resp(e)),
    }
}

#[derive(Deserialize)]
pub struct StoreBannerQuery {
    pub position: Option<i64>,
    pub locale: Option<String>,
}

pub async fn store_blogs(State(state): State<SharedState>, Query(q): Query<BlogListQuery>) -> Response {
    let Ok(locale) = parse_locale_q(q.locale.as_deref()) else {
        return err_resp(CatalogError::field_validation(&[("locale", "invalid_enum")]));
    };
    let page = q.page.unwrap_or(1).max(1);
    let page_size = match q.page_size {
        Some(p) if (1..=100).contains(&p) => p,
        _ => 20,
    };
    match svc::blog_page(&state.biz_db, q.category.clone(), page, page_size, &locale).await {
        Ok((items, total)) => no_store(ok_json(paginated(items, total, page, page_size))),
        Err(e) => no_store(err_resp(e)),
    }
}

#[derive(Deserialize)]
pub struct BlogListQuery {
    pub category: Option<String>,
    pub locale: Option<String>,
    pub page: Option<i64>,
    #[serde(rename = "page_size")]
    pub page_size: Option<i64>,
}

pub async fn store_blog_detail(State(state): State<SharedState>, Path(slug): Path<String>, Query(q): Query<LocaleQuery>) -> Response {
    let Ok(locale) = parse_locale_q(q.locale.as_deref()) else {
        return err_resp(CatalogError::field_validation(&[("locale", "invalid_enum")]));
    };
    match svc::blog_by_slug(&state.biz_db, &slug, &locale).await {
        Ok(detail) => no_store(ok_json(detail)),
        Err(e) => no_store(err_resp(e)),
    }
}

pub async fn store_blog_view(State(state): State<SharedState>, Path(slug): Path<String>) -> Response {
    let _ = svc::blog_record_view(&state.biz_db, &slug).await;
    let mut r = StatusCode::NO_CONTENT.into_response();
    r.headers_mut()
        .insert("cache-control", axum::http::HeaderValue::from_static("no-store"));
    r
}

pub async fn store_blog_preview(State(state): State<SharedState>, Path(token): Path<String>, Query(q): Query<LocaleQuery>) -> Response {
    match svc::blog_preview_resolve(&state.biz_db, state.redis.as_ref(), &token).await {
        Ok(detail) => {
            let mut r = ok_json(detail);
            r.headers_mut()
                .insert("cache-control", axum::http::HeaderValue::from_static("no-store"));
            r.headers_mut()
                .insert("x-robots-tag", axum::http::HeaderValue::from_static("noindex, nofollow"));
            r
        }
        Err(e) => err_resp(e),
    }
}

pub async fn store_sitemap_blogs(State(state): State<SharedState>) -> Response {
    match svc::blog_sitemap(&state.biz_db).await {
        Ok(items) => no_store(ok_json(json!({ "items": items }))),
        Err(e) => no_store(err_resp(e)),
    }
}

pub async fn store_weddings(State(state): State<SharedState>, Query(q): Query<BlogListQuery>) -> Response {
    let Ok(locale) = parse_locale_q(q.locale.as_deref()) else {
        return err_resp(CatalogError::field_validation(&[("locale", "invalid_enum")]));
    };
    let page = q.page.unwrap_or(1).max(1);
    let page_size = match q.page_size {
        Some(p) if (1..=100).contains(&p) => p,
        _ => 20,
    };
    let ck = crate::content::cache_key("weddings", &format!("{}:{}:{}", locale, page, page_size));
    if let Some(hit) = crate::content::cache_get_json(state.redis.as_ref(), &ck).await {
        return no_store(ok_json(hit));
    }
    match svc::wedding_page(&state.biz_db, page, page_size, &locale).await {
        Ok((items, total)) => {
            let payload = paginated(items, total, page, page_size);
            crate::content::cache_set_json(state.redis.as_ref(), &ck, &payload, 60).await;
            no_store(ok_json(payload))
        }
        Err(e) => no_store(err_resp(e)),
    }
}

pub async fn store_lookbooks(State(state): State<SharedState>, Query(q): Query<LocaleQuery>) -> Response {
    let Ok(locale) = parse_locale_q(q.locale.as_deref()) else {
        return err_resp(CatalogError::field_validation(&[("locale", "invalid_enum")]));
    };
    let ck = crate::content::cache_key("lookbooks", &locale);
    if let Some(hit) = crate::content::cache_get_json(state.redis.as_ref(), &ck).await {
        return no_store(ok_json(hit));
    }
    match svc::lookbook_list(&state.biz_db, &locale).await {
        Ok(items) => {
            let payload = json!({ "items": items });
            crate::content::cache_set_json(state.redis.as_ref(), &ck, &payload, 60).await;
            no_store(ok_json(payload))
        }
        Err(e) => no_store(err_resp(e)),
    }
}

pub async fn store_guides(State(state): State<SharedState>, Query(q): Query<LocaleQuery>) -> Response {
    let Ok(locale) = parse_locale_q(q.locale.as_deref()) else {
        return err_resp(CatalogError::field_validation(&[("locale", "invalid_enum")]));
    };
    let ck = crate::content::cache_key("guides", &locale);
    if let Some(hit) = crate::content::cache_get_json(state.redis.as_ref(), &ck).await {
        return no_store(ok_json(hit));
    }
    match svc::guide_list(&state.biz_db, &locale).await {
        Ok(items) => {
            let payload = json!({ "items": items });
            crate::content::cache_set_json(state.redis.as_ref(), &ck, &payload, 60).await;
            no_store(ok_json(payload))
        }
        Err(e) => no_store(err_resp(e)),
    }
}

// ─────────────── admin 博客(E-MKT-24~33)───────────────

pub async fn admin_blog_list(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Query(q): Query<BlogListQuery>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/blog").await {
        return *r;
    }
    let page = q.page.unwrap_or(1).max(1);
    let page_size = match q.page_size {
        Some(p) if (1..=100).contains(&p) => p,
        _ => 20,
    };
    match svc::blog_admin_page(&state.biz_db, page, page_size).await {
        Ok((items, total)) => ok_json(paginated(items, total, page, page_size)),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_blog_get(State(state): State<SharedState>, a: AuthedAdmin, Path(id): Path<String>) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/blog").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc::not_found());
    };
    match svc::blog_admin_get(&state.biz_db, id).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_blog_create(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Json(req): Json<svc::BlogUpsert>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/blog").await {
        return *r;
    }
    match svc::blog_create(&state.biz_db, &state.db, req, &a.claims.sub).await {
        Ok(v) => {
            (StatusCode::CREATED, Json(json!({"code": 0, "message": null, "service_id": null, "data": v}))).into_response()
        }
        Err(e) => err_resp(e),
    }
}

pub async fn admin_blog_update(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<svc::BlogUpsert>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/blog").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc::not_found());
    };
    match svc::blog_update(&state.biz_db, &state.db, id, req, &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_blog_delete(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/blog").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc::not_found());
    };
    match svc::blog_delete(&state.biz_db, &state.db, id, &a.claims.sub).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_blog_toggle(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<ToggleBody>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/blog").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc::not_found());
    };
    match svc::blog_toggle_status(&state.biz_db, &state.db, id, req.status.unwrap_or(0), &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct ToggleBody {
    pub status: Option<i64>,
}

pub async fn admin_blog_preview_token(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/blog").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc::not_found());
    };
    match svc::blog_preview_issue(&state.biz_db, state.redis.as_ref(), id).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub fn store_router() -> axum::Router<SharedState> {
    use axum::routing::{get, post};
    axum::Router::new()
        .route("/api/store/content/banners", get(store_banners))
        .route("/api/store/content/blogs", get(store_blogs))
        .route("/api/store/content/blogs/preview/{token}", get(store_blog_preview))
        .route("/api/store/content/blogs/{slug}/view", post(store_blog_view))
        .route("/api/store/content/blogs/{slug}", get(store_blog_detail))
        .route("/api/store/content/sitemap-blogs", get(store_sitemap_blogs))
        .route("/api/store/content/weddings", get(store_weddings))
        .route("/api/store/content/weddings/{id}", get(store_wedding_detail))
        .route("/api/store/content/lookbooks", get(store_lookbooks))
        .route("/api/store/content/lookbooks/{id}", get(store_lookbook_detail))
        .route("/api/store/content/guides", get(store_guides))
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{delete, get, post, put};
    axum::Router::new()
        .route("/api/admin/content/blogs", get(admin_blog_list).post(admin_blog_create))
        .route("/api/admin/content/blogs/preview-token-placeholder", get(admin_blog_get))
        .route("/api/admin/content/blogs/{id}", get(admin_blog_get).put(admin_blog_update).delete(admin_blog_delete))
        .route("/api/admin/content/blogs/{id}/status", axum::routing::patch(admin_blog_toggle))
        .route("/api/admin/content/blogs/{id}/preview-token", post(admin_blog_preview_token))
        .route("/api/admin/content/weddings", get(admin_wedding_list).post(admin_wedding_create))
        .route(
            "/api/admin/content/weddings/{id}",
            get(admin_wedding_get).put(admin_wedding_update).delete(admin_wedding_delete),
        )
        .route("/api/admin/content/weddings/{id}/status", axum::routing::patch(admin_wedding_toggle))
        .route("/api/admin/content/lookbooks", get(admin_lookbook_list).post(admin_lookbook_create))
        .route(
            "/api/admin/content/lookbooks/{id}",
            get(admin_lookbook_get).put(admin_lookbook_update).delete(admin_lookbook_delete),
        )
        .route("/api/admin/content/lookbooks/{id}/status", axum::routing::patch(admin_lookbook_toggle))
        .route("/api/admin/content/guides", get(admin_guide_list).post(admin_guide_create))
        .route(
            "/api/admin/content/guides/{id}",
            get(admin_guide_get).put(admin_guide_update).delete(admin_guide_delete),
        )
        .route("/api/admin/content/guides/{id}/status", axum::routing::patch(admin_guide_toggle))
        .route("/api/admin/content/guides/reorder", post(admin_guide_reorder))
        .with_state(ContentState { shared: state, jwt })
}

#[derive(Clone)]
pub struct ContentState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<ContentState> for SharedState {
    fn from_ref(s: &ContentState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<ContentState> for identity::security::JwtProvider {
    fn from_ref(s: &ContentState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}

// ─────────────── store 详情(E-MKT-05/07;V-MKT-006 非法 id → 404701)───────────────

fn parse_content_id(raw: &str) -> Result<i64, CatalogError> {
    match raw.parse::<i64>() {
        Ok(v) if v > 0 => Ok(v),
        _ => Err(CatalogError::new(404701)),
    }
}

pub async fn store_wedding_detail(
    State(state): State<SharedState>,
    Path(id): Path<String>,
    Query(q): Query<LocaleQuery>,
) -> Response {
    let Ok(locale) = parse_locale_q(q.locale.as_deref()) else {
        return err_resp(CatalogError::field_validation(&[("locale", "invalid_enum")]));
    };
    let Ok(cid) = parse_content_id(&id) else {
        return err_resp(CatalogError::new(404701));
    };
    match crate::content::wedding_detail(&state.biz_db, cid, &locale).await {
        Ok(v) => no_store(ok_json(v)),
        Err(e) => no_store(err_resp(e)),
    }
}

pub async fn store_lookbook_detail(
    State(state): State<SharedState>,
    Path(id): Path<String>,
    Query(q): Query<LocaleQuery>,
) -> Response {
    let Ok(locale) = parse_locale_q(q.locale.as_deref()) else {
        return err_resp(CatalogError::field_validation(&[("locale", "invalid_enum")]));
    };
    let Ok(cid) = parse_content_id(&id) else {
        return err_resp(CatalogError::new(404701));
    };
    match svc::lookbook_get(&state.biz_db, cid, &locale).await {
        Ok(v) => no_store(ok_json(v)),
        Err(e) => no_store(err_resp(e)),
    }
}

// ─────────────── admin weddings(E-MKT-32~36;RBAC /content/weddings)───────────────

#[derive(Deserialize)]
pub struct WeddingListQuery {
    pub page: Option<i64>,
    pub page_size: Option<i64>,
    pub status: Option<i64>,
}

#[derive(Deserialize)]
pub struct AdminWeddingStatusBody {
    pub status: i64,
}

pub async fn admin_wedding_list(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Query(q): Query<WeddingListQuery>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/weddings").await {
        return *r;
    }
    let page = q.page.unwrap_or(1).max(1);
    let page_size = match q.page_size {
        Some(p) if (1..=100).contains(&p) => p,
        _ => 20,
    };
    match svc::wedding_admin_page(&state.biz_db, page, page_size, q.status).await {
        Ok((items, total)) => ok_json(paginated(items, total, page, page_size)),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_wedding_get(State(state): State<SharedState>, a: AuthedAdmin, Path(id): Path<String>) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/weddings").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404701));
    };
    match svc::wedding_admin_get(&state.biz_db, id).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_wedding_create(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Json(req): Json<svc::WeddingUpsert>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/weddings").await {
        return *r;
    }
    match svc::wedding_admin_create(&state.biz_db, &state.db, req, &a.claims.sub).await {
        Ok(v) => (StatusCode::CREATED, Json(json!({"code": 0, "message": null, "service_id": null, "data": v}))).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_wedding_update(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<svc::WeddingUpsert>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/weddings").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404701));
    };
    match svc::wedding_admin_update(&state.biz_db, &state.db, id, req, &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_wedding_delete(State(state): State<SharedState>, a: AuthedAdmin, Path(id): Path<String>) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/weddings").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404701));
    };
    match svc::wedding_admin_delete(&state.biz_db, &state.db, id, &a.claims.sub).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_wedding_toggle(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<AdminWeddingStatusBody>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/weddings").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404701));
    };
    match svc::wedding_admin_toggle_status(&state.biz_db, &state.db, id, req.status, &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

// ─────────────── admin lookbooks(E-MKT-37~41;RBAC /content/lookbooks)───────────────

#[derive(Deserialize)]
pub struct AdminLookbookListQuery {
    pub status: Option<i64>,
}

#[derive(Deserialize)]
pub struct AdminLookbookStatusBody {
    pub status: i64,
}

pub async fn admin_lookbook_list(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Query(q): Query<AdminLookbookListQuery>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/lookbook").await {
        return *r;
    }
    match svc::lookbook_admin_list(&state.biz_db, q.status).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_lookbook_get(State(state): State<SharedState>, a: AuthedAdmin, Path(id): Path<String>) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/lookbook").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404701));
    };
    match svc::lookbook_admin_get(&state.biz_db, id).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_lookbook_create(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Json(req): Json<svc::LookbookUpsert>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/lookbook").await {
        return *r;
    }
    match svc::lookbook_admin_create(&state.biz_db, &state.db, req, &a.claims.sub).await {
        Ok(v) => (StatusCode::CREATED, Json(json!({"code": 0, "message": null, "service_id": null, "data": v}))).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_lookbook_update(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<svc::LookbookUpsert>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/lookbook").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404701));
    };
    match svc::lookbook_admin_update(&state.biz_db, &state.db, id, req, &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_lookbook_delete(State(state): State<SharedState>, a: AuthedAdmin, Path(id): Path<String>) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/lookbook").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404701));
    };
    match svc::lookbook_admin_delete(&state.biz_db, &state.db, id, &a.claims.sub).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_lookbook_toggle(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<AdminLookbookStatusBody>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/lookbook").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404701));
    };
    match svc::lookbook_admin_toggle_status(&state.biz_db, &state.db, id, req.status, &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

// ─────────────── admin guides(E-MKT-42~46+reorder;RBAC /content/guides)───────────────

#[derive(Deserialize)]
pub struct AdminGuideListQuery {
    pub status: Option<i64>,
}

#[derive(Deserialize)]
pub struct AdminGuideStatusBody {
    pub status: i64,
}

#[derive(Deserialize)]
pub struct AdminGuideReorderBody {
    pub ids: Vec<i64>,
}

pub async fn admin_guide_list(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Query(q): Query<AdminGuideListQuery>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/lookbook").await {
        return *r;
    }
    match svc::guide_admin_list(&state.biz_db, q.status).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_guide_get(State(state): State<SharedState>, a: AuthedAdmin, Path(id): Path<String>) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/lookbook").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404701));
    };
    match svc::guide_admin_get(&state.biz_db, id).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_guide_create(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Json(req): Json<svc::GuideUpsert>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/lookbook").await {
        return *r;
    }
    match svc::guide_admin_create(&state.biz_db, &state.db, req, &a.claims.sub).await {
        Ok(v) => (StatusCode::CREATED, Json(json!({"code": 0, "message": null, "service_id": null, "data": v}))).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_guide_update(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<svc::GuideUpsert>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/lookbook").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404701));
    };
    match svc::guide_admin_update(&state.biz_db, &state.db, id, req, &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_guide_delete(State(state): State<SharedState>, a: AuthedAdmin, Path(id): Path<String>) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/lookbook").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404701));
    };
    match svc::guide_admin_delete(&state.biz_db, &state.db, id, &a.claims.sub).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_guide_toggle(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<AdminGuideStatusBody>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/lookbook").await {
        return *r;
    }
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404701));
    };
    match svc::guide_admin_toggle_status(&state.biz_db, &state.db, id, req.status, &a.claims.sub).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_guide_reorder(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Json(req): Json<AdminGuideReorderBody>,
) -> Response {
    if let Err(r) = identity::api::admin::require_permission(&state, &a, "/content/lookbook").await {
        return *r;
    }
    match svc::guide_admin_reorder(&state.biz_db, &state.db, req.ids, &a.claims.sub).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}
