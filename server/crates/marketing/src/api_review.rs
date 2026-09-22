//! review REST 层:store 提交/列表/我的 + admin 审核/回复/删除。

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;
use identity::api::store_account::AuthedUser;

use sea_orm::ConnectionTrait;

use crate::review as svc;
use crate::service_category::CatalogError;

common::error_site!(SITE_STORE_REVIEW = "review/store");
common::error_site!(SITE_ADMIN_REVIEW = "review/admin");

fn err_resp(e: CatalogError) -> Response {
    let status = axum::http::StatusCode::from_u16(crate::service_category::cat_err::http_status(e.code))
        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
    // review 域段 8 的 HTTP 映射
    let status = if e.code == 403801 { StatusCode::FORBIDDEN }
        else if e.code == 404801 { StatusCode::NOT_FOUND }
        else if e.code == 409801 { StatusCode::CONFLICT }
        else if e.code == 422801 { StatusCode::UNPROCESSABLE_ENTITY }
        else { status };
    (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

pub async fn store_list(
    State(state): State<SharedState>,
    Query(q): Query<PageQuery>,
) -> Response {
    let page = q.page.unwrap_or(1).max(1);
    let page_size = match q.page_size {
        Some(p) if (1..=100).contains(&p) => p,
        _ => 20,
    };
    let Some(product_id) = q.product_id else {
        return err_resp(CatalogError::field_validation(&[("product_id", "required")]));
    };
    match svc::store_list(&state.biz_db, product_id, page, page_size).await {
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
    pub product_id: Option<i64>,
    pub page: Option<i64>,
    #[serde(rename = "page_size")]
    pub page_size: Option<i64>,
    pub status: Option<i64>,
}

#[derive(Deserialize)]
pub struct ReviewCreateBody {
    pub product_id: Option<i64>,
    pub rating: Option<i64>,
    pub content: Option<String>,
    pub images: Option<Vec<String>>,
}

pub async fn store_create(
    State(state): State<SharedState>,
    user: AuthedUser,
    Json(req): Json<ReviewCreateBody>,
) -> Response {
    let customer_id: i64 = user.claims.sub.parse().unwrap_or(0);
    // 姓名快照(user 表主库)
    let customer_name = state
        .db
        .query_one(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "SELECT COALESCE(name, SUBSTRING_INDEX(email, '@', 1)) AS n FROM user WHERE id = ?",
            [customer_id.into()],
        ))
        .await
        .ok()
        .flatten()
        .and_then(|r| r.try_get::<String>("", "n").ok())
        .unwrap_or_else(|| "Anonymous".into());
    let req = svc::ReviewCreate { product_id: req.product_id, rating: req.rating, content: req.content, images: req.images };
    match svc::create(&state.biz_db, customer_id, &customer_name, &req).await {
        Ok(v) => {
            (StatusCode::CREATED, Json(json!({"code": 0, "message": null, "service_id": null, "data": v}))).into_response()
        }
        Err(e) => err_resp(e),
    }
}

pub async fn my_reviews(
    State(state): State<SharedState>,
    user: AuthedUser,
    Query(q): Query<PageQuery>,
) -> Response {
    let page = q.page.unwrap_or(1).max(1);
    let page_size = match q.page_size {
        Some(p) if (1..=100).contains(&p) => p,
        _ => 20,
    };
    match svc::my_reviews(&state.biz_db, user.claims.sub.parse().unwrap_or(0), page, page_size).await {
        Ok((items, total)) => ok_json(json!({
            "data": items, "total_elements": total, "page_number": page, "page_size": page_size,
        })),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_list(
    State(state): State<SharedState>,
    _a: AuthedAdmin,
    Query(q): Query<PageQuery>,
) -> Response {
    let page = q.page.unwrap_or(1).max(1);
    let page_size = match q.page_size {
        Some(p) if (1..=100).contains(&p) => p,
        _ => 20,
    };
    match svc::admin_list(&state.biz_db, q.status, page, page_size).await {
        Ok((items, total)) => ok_json(json!({
            "data": items, "total_elements": total, "page_number": page, "page_size": page_size,
        })),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct ModerateBody {
    pub approve: Option<bool>,
}

pub async fn admin_moderate(
    State(state): State<SharedState>,
    _a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<ModerateBody>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404801));
    };
    match svc::admin_moderate(&state.biz_db, id, req.approve.unwrap_or(true)).await {
        Ok(()) => ok_json(json!({"moderated": true})),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct ReplyBody {
    pub reply_author: Option<String>,
    pub reply_content: Option<String>,
}

pub async fn admin_reply(
    State(state): State<SharedState>,
    _a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<ReplyBody>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404801));
    };
    let author = req.reply_author.as_deref().unwrap_or("Admin");
    let Some(content) = req.reply_content.as_deref().map(str::trim).filter(|s| !s.is_empty()) else {
        return err_resp(CatalogError::field_validation(&[("reply_content", "required")]));
    };
    match svc::admin_reply(&state.biz_db, id, author, content).await {
        Ok(()) => ok_json(json!({"replied": true})),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_delete_reply(
    State(state): State<SharedState>,
    _a: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404801));
    };
    match svc::admin_delete_reply(&state.biz_db, id).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub fn store_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{get, post};
    axum::Router::new()
        .route("/api/store/reviews", get(store_list).post(store_create))
        .route("/api/store/reviews/mine", get(my_reviews))
        .with_state(ReviewState { shared: state, jwt })
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{delete, get, post, put};
    axum::Router::new()
        .route("/api/admin/reviews", get(admin_list))
        .route("/api/admin/reviews/{id}/reply", put(admin_reply).delete(admin_delete_reply))
        .route("/api/admin/reviews/{id}/moderate", axum::routing::patch(admin_moderate))
        .with_state(ReviewState { shared: state, jwt })
}

#[derive(Clone)]
pub struct ReviewState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<ReviewState> for SharedState {
    fn from_ref(s: &ReviewState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<ReviewState> for identity::security::JwtProvider {
    fn from_ref(s: &ReviewState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
