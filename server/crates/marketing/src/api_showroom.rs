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

#[derive(Deserialize)]
pub struct ItemBody {
    pub product_id: Option<i64>,
    pub color: Option<String>,
}

pub async fn add_item(State(state): State<SharedState>, user: AuthedUser, Path(id): Path<String>, Json(req): Json<ItemBody>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    let Some(pid) = req.product_id else { return err_resp(CatalogError::field_validation(&[("product_id", "required")])); };
    match svc::add_item(&state.biz_db, owner_id(&user), id, pid, req.color).await {
        Ok(v) => (StatusCode::CREATED, ok_json(v)).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn delete_item(State(state): State<SharedState>, user: AuthedUser, Path((id, item_id)): Path<(String, String)>) -> Response {
    let (Ok(id), Ok(iid)) = (id.parse::<i64>(), item_id.parse::<i64>()) else { return err_resp(svc::not_found()); };
    match svc::delete_item(&state.biz_db, owner_id(&user), id, iid).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct MemberBody {
    pub nickname: Option<String>,
    pub email: Option<String>,
}

pub async fn add_member(State(state): State<SharedState>, user: AuthedUser, Path(id): Path<String>, Json(req): Json<MemberBody>) -> Response {
    let Ok(id) = id.parse::<i64>() else { return err_resp(svc::not_found()); };
    let Some(nick) = req.nickname.as_deref().map(str::trim).filter(|s| !s.is_empty()) else {
        return err_resp(CatalogError::field_validation(&[("nickname", "required")]));
    };
    let Some(email) = req.email.as_deref().map(str::trim).filter(|s| !s.is_empty()) else {
        return err_resp(CatalogError::field_validation(&[("email", "required")]));
    };
    match svc::add_member(&state.biz_db, owner_id(&user), id, nick, email).await {
        Ok(v) => (StatusCode::CREATED, ok_json(v)).into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn delete_member(State(state): State<SharedState>, user: AuthedUser, Path((id, member_id)): Path<(String, String)>) -> Response {
    let (Ok(id), Ok(mid)) = (id.parse::<i64>(), member_id.parse::<i64>()) else { return err_resp(svc::not_found()); };
    match svc::delete_member(&state.biz_db, owner_id(&user), id, mid).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct VoteBody {
    pub vote: Option<i64>,
}

pub async fn vote(State(state): State<SharedState>, user: AuthedUser, Path((id, item_id)): Path<(String, String)>, Json(req): Json<VoteBody>) -> Response {
    let (Ok(_id), Ok(iid)) = (id.parse::<i64>(), item_id.parse::<i64>()) else { return err_resp(svc::not_found()); };
    let member_id: i64 = user.claims.sub.parse().unwrap_or(0); // owner 即 member(owner 视图)
    match svc::vote(&state.biz_db, member_id, iid, req.vote.unwrap_or(0)).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct CommentBody {
    pub content: Option<String>,
}

pub async fn comment(State(state): State<SharedState>, user: AuthedUser, Path((id, item_id)): Path<(String, String)>, Json(req): Json<CommentBody>) -> Response {
    let (Ok(_id), Ok(iid)) = (id.parse::<i64>(), item_id.parse::<i64>()) else { return err_resp(svc::not_found()); };
    let member_id: i64 = user.claims.sub.parse().unwrap_or(0);
    let Some(content) = req.content.clone() else {
        return err_resp(CatalogError::field_validation(&[("content", "required")]));
    };
    match svc::comment(&state.biz_db, member_id, iid, &content).await {
        Ok(v) => (StatusCode::CREATED, ok_json(v)).into_response(),
        Err(e) => err_resp(e),
    }
}

#[derive(Deserialize)]
pub struct AssignBody {
    pub item_id: Option<i64>,
}

pub async fn assign(State(state): State<SharedState>, user: AuthedUser, Path((id, member_id)): Path<(String, String)>, Json(req): Json<AssignBody>) -> Response {
    let (Ok(id), Ok(mid)) = (id.parse::<i64>(), member_id.parse::<i64>()) else { return err_resp(svc::not_found()); };
    let Some(iid) = req.item_id else { return err_resp(CatalogError::field_validation(&[("item_id", "required")])); };
    match svc::assign(&state.biz_db, owner_id(&user), id, mid, iid).await {
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
        .route("/api/store/showrooms/{id}/items", post(add_item))
        .route("/api/store/showrooms/{id}/items/{itemId}", delete(delete_item))
        .route("/api/store/showrooms/{id}/items/{itemId}/vote", put(vote))
        .route("/api/store/showrooms/{id}/items/{itemId}/comments", post(comment))
        .route("/api/store/showrooms/{id}/members", axum::routing::post(add_member))
        .route("/api/store/showrooms/{id}/members/{memberId}", delete(delete_member))
        .route("/api/store/showrooms/{id}/members/{memberId}/assign", post(assign))
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
