//! attribute REST 层:E-CAT-19~26(admin)。

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde_json::{json, Value};

use common::state::SharedState;
use identity::api::admin::AuthedAdmin;

use crate::product::attribute as svc;
use crate::service_category::CatalogError;

common::error_site!(SITE_ADMIN_ATTR_DEFS = "catalog/attribute/admin_defs");
common::error_site!(SITE_ADMIN_ATTR_SETS = "catalog/attribute/admin_sets");

fn err_resp(e: CatalogError) -> Response {
    let status = axum::http::StatusCode::from_u16(crate::service_category::cat_err::http_status(e.code))
        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);
    (status, Json(json!({"code": e.code, "message": null, "service_id": null, "data": e.details}))).into_response()
}

fn ok_json(v: Value) -> Response {
    Json(json!({"code": 0, "message": null, "service_id": null, "data": v})).into_response()
}

pub async fn def_list(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match svc::def_list(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn def_create(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Json(req): Json<svc::DefUpsert>,
) -> Response {
    match svc::def_create(&state.biz_db, &state.db, req, &a.claims.sub).await {
        Ok(dto) => {
            (StatusCode::CREATED, Json(json!({"code": 0, "message": null, "service_id": null, "data": dto}))).into_response()
        }
        Err(e) => err_resp(e),
    }
}

pub async fn def_update(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<svc::DefUpsert>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404504));
    };
    match svc::def_update(&state.biz_db, &state.db, id, req, &a.claims.sub).await {
        Ok(dto) => ok_json(json!(dto)),
        Err(e) => err_resp(e),
    }
}

pub async fn def_delete(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404504));
    };
    match svc::def_delete(&state.biz_db, &state.db, id, &a.claims.sub).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub async fn set_list(State(state): State<SharedState>, _a: AuthedAdmin) -> Response {
    match svc::set_list(&state.biz_db).await {
        Ok(items) => ok_json(json!({ "items": items })),
        Err(e) => err_resp(e),
    }
}

pub async fn set_create(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Json(req): Json<svc::SetUpsert>,
) -> Response {
    match svc::set_create(&state.biz_db, &state.db, req, &a.claims.sub).await {
        Ok(dto) => {
            (StatusCode::CREATED, Json(json!({"code": 0, "message": null, "service_id": null, "data": dto}))).into_response()
        }
        Err(e) => err_resp(e),
    }
}

pub async fn set_update(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
    Json(req): Json<svc::SetUpsert>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404505));
    };
    match svc::set_update(&state.biz_db, &state.db, id, req, &a.claims.sub).await {
        Ok(dto) => ok_json(json!(dto)),
        Err(e) => err_resp(e),
    }
}

pub async fn set_delete(
    State(state): State<SharedState>,
    a: AuthedAdmin,
    Path(id): Path<String>,
) -> Response {
    let Ok(id) = id.parse::<i64>() else {
        return err_resp(CatalogError::new(404505));
    };
    match svc::set_delete(&state.biz_db, &state.db, id, &a.claims.sub).await {
        Ok(()) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => err_resp(e),
    }
}

pub fn admin_router(state: SharedState, jwt: identity::security::JwtProvider) -> axum::Router {
    use axum::routing::{delete, get, post, put};
    axum::Router::new()
        .route("/api/admin/attribute-defs", get(def_list).post(def_create))
        .route("/api/admin/attribute-defs/{id}", put(def_update).delete(def_delete))
        .route("/api/admin/attribute-sets", get(set_list).post(set_create))
        .route("/api/admin/attribute-sets/{id}", put(set_update).delete(set_delete))
        .with_state(AttrState { shared: state, jwt })
}

#[derive(Clone)]
pub struct AttrState {
    pub shared: SharedState,
    pub jwt: identity::security::JwtProvider,
}

impl axum::extract::FromRef<AttrState> for SharedState {
    fn from_ref(s: &AttrState) -> SharedState {
        s.shared.clone()
    }
}

impl axum::extract::FromRef<AttrState> for identity::security::JwtProvider {
    fn from_ref(s: &AttrState) -> identity::security::JwtProvider {
        s.jwt.clone()
    }
}
