//! question REST 层:store 提问/列表 + admin 回答/删除/可见性/批量。

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

use crate::question as svc;
use crate::service_category::CatalogError;

fn err_resp(e: CatalogError) -> Response {
    (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

#[derive(Deserialize)]
pub struct ProductQuery {
    pub product_id: Option<i64>,
}

pub async fn store_list(State(state): State<SharedState>, Query(q): Query<ProductQuery>) -> Response {
    let Some(pid) = q.product_id else {
        return ok_json(json!({ "items": [] }));
    };
    match svc::store_list(&state.biz_db, pid).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct QuestionCreateBody {
    pub product_id: Option<i64>,
    pub content: Option<String>,
}

pub async fn store_create(
    State(state): State<SharedState>,
    user: AuthedUser,
    Json(req): Json<QuestionCreateBody>,
) -> Response {
    let Some(pid) = req.product_id else {
        return err_resp(CatalogError::field_validation(&[("product_id", "required")]));
    };
    let Some(content) = req.content.as_deref().map(str::trim).filter(|s| !s.is_empty()) else {
        return err_resp(CatalogError::field_validation(&[("content", "required")]));
    };
    let customer_id: i64 = user.claims.sub.parse().unwrap_or(0);
    let user_name = state
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
    match svc::create(&state.biz_db, pid, customer_id, &user_name, content).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_list(
    State(state): State<SharedState>,
    _a: AuthedAdmin,
    Query(q): Query<ProductQuery>,
) -> Response {
    match svc::admin_list(&state.biz_db, q.product_id).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct AnswerBody {
    pub answer: Option<String>,
}

pub async fn admin_answer(
    State(state): State<SharedState>,
    _a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<AnswerBody>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404801));
    };
    let Some(answer) = req.answer.as_deref().map(str::trim).filter(|s| !s.is_empty()) else {
        return err_resp(CatalogError::field_validation(&[("answer", "required")]));
    };
    match svc::admin_answer(&state.biz_db, id, answer).await {
        Ok(()) => ok_json(json!({"answered": true})),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_delete(
    State(state): State<SharedState>,
    _a: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404801));
    };
    match svc::admin_delete(&state.biz_db, id).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn admin_visibility(
    State(state): State<SharedState>,
    _a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<VisibilityBody>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404801));
    };
    match svc::admin_visibility(&state.biz_db, id, req.visible.unwrap_or(true)).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct VisibilityBody {
    pub visible: Option<bool>,
}

#[derive(Deserialize)]
pub struct BatchBody {
    pub ids: Option<Vec<i64>>,
    pub visible: Option<bool>,
}

pub async fn admin_batch(
    State(state): State<SharedState>,
    _a: AuthedAdmin,
    Json(req): Json<BatchBody>,
) -> Response {
    let ids = req.ids.unwrap_or_default();
    if ids.is_empty() {
        return err_resp(CatalogError::field_validation(&[("ids", "required")]));
    }
    match svc::admin_batch_visible(&state.biz_db, &ids, req.visible.unwrap_or(true)).await {
        Ok(n) => ok_json(json!({ "updated": n })),
        Err(e) => err_resp(e),
    }
}

pub fn store_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{get, post};
    axum::Router::new()
        .route("/api/store/questions", get(store_list).post(store_create))
        .with_state(QState { shared: state, jwt })
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{delete, get, post, put};
    axum::Router::new()
        .route("/api/admin/questions", get(admin_list).post(admin_batch))
        .route("/api/admin/questions/{id}/answer", put(admin_answer))
        .route("/api/admin/questions/{id}/answer", delete(admin_delete))
        .route("/api/admin/questions/{id}/visibility", axum::routing::patch(admin_visibility))
        .with_state(QState { shared: state, jwt })
}

#[derive(Clone)]
pub struct QState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<QState> for SharedState {
    fn from_ref(s: &QState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<QState> for identity::security::JwtProvider {
    fn from_ref(s: &QState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
