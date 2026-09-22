//! showroom REST 层:store 全操作(create/list/get/update/delete/items/invite reset)。

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::store_account::AuthedUser;

use crate::showroom as svc;
use crate::service_category::CatalogError;

fn err_resp(e: CatalogError) -> Response {
    (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

fn owner_id(user: &AuthedUser) -> i64 {
    user.claims.sub.parse().unwrap_or(0)
}

#[derive(Deserialize)]
pub struct UpsertBody {
    pub name: Option<String>,
    pub wedding_date: Option<String>,
}

pub async fn create(State(state): State<SharedState>, user: AuthedUser, Json(req): Json<UpsertBody>) -> Response {
    let r = svc::ShowroomUpsert { name: req.name, wedding_date: req.wedding_date };
    match svc::create(&state.biz_db, owner_id(&user), &r).await {
        Ok(v) => (StatusCode::CREATED, ok_json(v)).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn list(State(state): State<SharedState>, user: AuthedUser) -> Response {
    match svc::list(&state.biz_db, owner_id(&user)).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn get_showroom(State(state): State<SharedState>, user: AuthedUser, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc::not_found());
    };
    match svc::get_for_owner(&state.biz_db, owner_id(&user), id).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn update(State(state): State<SharedState>, user: AuthedUser, Path(id): Path<String>, Json(req): Json<UpsertBody>) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc::not_found());
    };
    let r = svc::ShowroomUpsert { name: req.name, wedding_date: req.wedding_date };
    match svc::update(&state.biz_db, owner_id(&user), id, &r).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub async fn delete_showroom(State(state): State<SharedState>, user: AuthedUser, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc::not_found());
    };
    match svc::delete(&state.biz_db, owner_id(&user), id).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn reset_invite(State(state): State<SharedState>, user: AuthedUser, Path(id): Path<String>) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(svc::not_found());
    };
    match svc::reset_invite(&state.biz_db, owner_id(&user), id).await {
        Ok(v) => ok_json(v),
        Err(e) => err_resp(e),
    }
}

pub fn router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{delete, get, post, put};
    axum::Router::new()
        .route("/api/store/showrooms", get(list).post(create))
        .route("/api/store/showrooms/{id}", get(get_showroom).put(update).delete(delete_showroom))
        .route("/api/store/showrooms/{id}/invite/reset", post(reset_invite))
        .with_state(ShowroomState { shared: state, jwt })
}

#[derive(Clone)]
pub struct ShowroomState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<ShowroomState> for SharedState {
    fn from_ref(s: &ShowroomState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<ShowroomState> for identity::security::JwtProvider {
    fn from_ref(s: &ShowroomState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
