//! /api/admin/* 端点(P3):auth(login/logout/me/permissions)、admins CRUD、
//! roles/permissions、users 运营、auth-config、operation-logs。
//! 鉴权 extractor = admin JWT 本地验签 + Redis 30s 缓存会话校验(对齐 Java AdminJwtFilter);
//! 权限校验 = 每端点显式 require_permission(对齐 Java @RequirePermission 切面,变更即时生效)。

use axum::extract::FromRequestParts;
use axum::extract::{Path, State};
use axum::http::HeaderMap;
use axum::response::{IntoResponse, Response};
use axum::routing::{delete, get, patch, post, put};
use axum::{Json, Router};
use common::error::{BizError, ErrorCode, R};
use common::state::SharedState;
use sea_orm::ConnectionTrait;
use serde_json::json;

use crate::security::{AdminClaims, JwtProvider};
use crate::service::{admin_auth, admin_ops, authconfig, permissions, session, SvcError};

/// admin 域路由状态
#[derive(Clone)]
pub struct AdminState {
    pub shared: SharedState,
    pub jwt: JwtProvider,
}

impl axum::extract::FromRef<AdminState> for SharedState {
    fn from_ref(state: &AdminState) -> Self {
        state.shared.clone()
    }
}

impl axum::extract::FromRef<AdminState> for JwtProvider {
    fn from_ref(state: &AdminState) -> Self {
        state.jwt.clone()
    }
}

pub fn router(shared: SharedState, jwt: JwtProvider) -> Router {
    let st = AdminState { shared, jwt };
    Router::new()
        .route("/auth/login", post(admin_login))
        .route("/auth/logout", post(admin_logout))
        .route("/auth/me", get(admin_me))
        .route("/auth/permissions", get(current_permissions))
        .route("/admins", get(list_admins).post(create_admin))
        .route("/admins/{id}", put(update_admin).delete(delete_admin))
        .route("/admins/{id}/status", patch(toggle_admin_status))
        .route("/admins/{id}/password", patch(reset_password))
        .route("/roles", get(list_roles).post(create_role))
        .route("/roles/{id}", put(update_role).delete(delete_role))
        .route("/permissions", get(list_permission_dict))
        .route("/users", get(list_users))
        .route("/users/{id}", get(get_user_detail))
        .route("/users/{id}/status", patch(toggle_user_status))
        .route("/users/{id}/sessions/force-logout", post(force_logout))
        .route("/auth-config", get(get_auth_config).put(update_auth_config))
        .route("/operation-logs", get(list_operation_logs))
        .route("/operation-logs/export", get(export_operation_logs))
        .route("/operation-logs/{_id}", delete(forbidden_delete_logs))
        .with_state(st)
}

// ══════════ 鉴权 extractor ══════════

/// admin JWT 鉴权(401 短路体固定中文,对齐 Java AdminJwtFilter)
pub struct AuthedAdmin {
    pub claims: AdminClaims,
}

impl<S> FromRequestParts<S> for AuthedAdmin
where
    S: Send + Sync,
    SharedState: axum::extract::FromRef<S>,
    JwtProvider: axum::extract::FromRef<S>,
{
    type Rejection = Response;

    async fn from_request_parts(
        parts: &mut axum::http::request::Parts,
        state: &S,
    ) -> Result<Self, Self::Rejection> {
        let st = <SharedState as axum::extract::FromRef<S>>::from_ref(state);
        let jwt = <JwtProvider as axum::extract::FromRef<S>>::from_ref(state);
        let token = parts
            .headers
            .get(axum::http::header::AUTHORIZATION)
            .and_then(|v| v.to_str().ok())
            .and_then(|v| v.strip_prefix("Bearer "))
            .map(|v| v.trim().to_string());
        let Some(token) = token else {
            return Err(unauthorized_zh());
        };
        let claims = jwt.parse_admin(&token).map_err(|_| unauthorized_zh())?;
        // 会话校验(Redis 30s 缓存 + DB 兜底,含管理员状态复核)
        match session::validate_admin(&st, &claims.jti).await {
            Ok(v) if v.valid && v.admin_active => Ok(AuthedAdmin { claims }),
            _ => Err(unauthorized_zh()),
        }
    }
}

fn unauthorized_zh() -> Response {
    let body = json!({"code": 40100, "message": "未认证"});
    let mut resp = Json(body).into_response();
    *resp.status_mut() = axum::http::StatusCode::UNAUTHORIZED;
    resp
}

/// 权限校验(对齐 Java @RequirePermission:实时查,变更下一请求生效)
async fn require_permission(
    st: &SharedState,
    admin_id: i64,
    perm: &str,
) -> Result<(), Box<Response>> {
    let keys = permissions::resolve(st, admin_id)
        .await
        .map_err(|e| Box::new(svc_err_resp("identity/admin/permission_check", e)))?;
    if !keys.iter().any(|k| k == perm) {
        let mut b = BizError::new("identity/admin/permission_check", ErrorCode::Forbidden);
        b.locale = "zh";
        return Err(Box::new(b.into_response()));
    }
    Ok(())
}

fn svc_err_resp(site: &'static str, err: SvcError) -> Response {
    super::store_auth::svc_to_biz(site, common::i18n::Locale::Zh, err).into_response()
}

fn client_ip(headers: &HeaderMap) -> String {
    headers
        .get("x-forwarded-for")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.split(',').next())
        .map(|v| v.trim().to_string())
        .unwrap_or_else(|| "127.0.0.1".into())
}

fn ua(headers: &HeaderMap) -> Option<String> {
    headers
        .get("user-agent")
        .and_then(|v| v.to_str().ok())
        .map(|v| v.to_string())
}

// ══════════ auth ══════════

async fn admin_login(
    State(st): State<AdminState>,
    headers: HeaderMap,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    let Some(Json(v)) = body else {
        return BizError::new("identity/admin/login", ErrorCode::BadRequestBody)
            .with_locale("zh")
            .into_response();
    };
    let email = v
        .get("email")
        .and_then(|e| e.as_str())
        .unwrap_or("")
        .trim()
        .to_lowercase();
    let password = v
        .get("password")
        .and_then(|p| p.as_str())
        .unwrap_or("")
        .to_string();
    if email.is_empty() || password.is_empty() {
        let mut b = BizError::new("identity/admin/login", ErrorCode::Validation);
        b.locale = "zh";
        return b.into_response();
    }
    match admin_auth::login(
        &st.shared,
        &st.jwt,
        &email,
        &password,
        &client_ip(&headers),
        ua(&headers).as_deref(),
    )
    .await
    {
        Ok(outcome) => Json(R::ok(Some(json!({
            "token": outcome.token,
            "admin": admin_json(&outcome.admin, "超级管理员", outcome.is_super),
            "permission_keys": outcome.permission_keys,
            "is_super": outcome.is_super,
        }))))
        .into_response(),
        Err(e) => svc_err_resp("identity/admin/login", e),
    }
}

fn admin_json(a: &admin_auth::AdminDto, role_name: &str, is_super: bool) -> serde_json::Value {
    // 对齐 Java AdminAuthSessionView:@JsonProperty 显式字段,status 为枚举名字符串
    json!({
        "id": a.id, "name": a.name, "email": a.email,
        "role_id": a.role_id, "role_name": if is_super { Some(role_name) } else { None },
        "status": status_name(a.status), "last_login_at": a.last_login_at,
    })
}

/// admin status 枚举名(对齐 Java AdminDTO.status 序列化为枚举名)
fn status_name(status: i32) -> String {
    match status {
        1 => "ACTIVE".into(),
        2 => "DISABLED".into(),
        _ => "ACTIVE".into(),
    }
}

async fn admin_logout(authed: AuthedAdmin, State(st): State<AdminState>) -> Response {
    match admin_auth::logout(&st.shared, &authed.claims.jti).await {
        Ok(()) => Json(R::<serde_json::Value>::ok(None)).into_response(),
        Err(e) => svc_err_resp("identity/admin/logout", e),
    }
}

async fn admin_me(authed: AuthedAdmin, State(st): State<AdminState>) -> Response {
    let admin_id: i64 = authed.claims.sub.parse().unwrap_or(0);
    match admin_auth::me_data(&st.shared, admin_id).await {
        Ok(data) => Json(R::ok(Some(json!({
            "admin": {
                "id": data.admin.id, "name": data.admin.name, "email": data.admin.email,
                "role_id": data.admin.role_id, "role_name": data.role_name,
                "status": status_name(data.admin.status), "last_login_at": data.admin.last_login_at,
            },
            "role_name": data.role_name,
            "is_super": data.is_super,
            "permission_keys": data.permission_keys,
        }))))
        .into_response(),
        Err(e) => svc_err_resp("identity/admin/me", e),
    }
}

async fn current_permissions(authed: AuthedAdmin, State(st): State<AdminState>) -> Response {
    let admin_id: i64 = authed.claims.sub.parse().unwrap_or(0);
    match permissions::resolve(&st.shared, admin_id).await {
        Ok(keys) => Json(R::ok(Some(json!(keys)))).into_response(),
        Err(e) => svc_err_resp("identity/admin/permissions", e),
    }
}

// ══════════ admins CRUD ══════════

async fn list_admins(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    axum::extract::Query(q): axum::extract::Query<std::collections::HashMap<String, String>>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/system/admins").await {
        return *r;
    }
    let page: u64 = q.get("page").and_then(|v| v.parse().ok()).unwrap_or(1);
    let page_size: u64 = q.get("pageSize").and_then(|v| v.parse().ok()).unwrap_or(20);
    let status: Option<i32> = q.get("status").and_then(|v| v.parse().ok());
    let role_id: Option<i64> = q
        .get("role_id")
        .and_then(|v| v.parse().ok())
        .or_else(|| q.get("roleId").and_then(|v| v.parse().ok()));
    match admin_ops::page_admins(&st.shared, page, page_size, status, role_id).await {
        Ok((items, total)) => Json(R::ok(Some(json!({
            "data": items.iter().map(|a| json!({
                "id": a.id, "name": a.name, "email": a.email,
                "role_id": a.role_id, "role_name": null,
                "status": a.status, "last_login_at": a.last_login_at,
            })).collect::<Vec<_>>(),
            "total_elements": total,
            "page_number": page, "page_size": page_size,
            "number_of_elements": items.len(),
            "total_pages": total.div_ceil(page_size),
        }))))
        .into_response(),
        Err(e) => svc_err_resp("identity/admin/list_admins", e),
    }
}

fn admin_id_of(a: &AuthedAdmin) -> i64 {
    a.claims.sub.parse().unwrap_or(0)
}

async fn create_admin(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    headers: HeaderMap,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/system/admins").await {
        return *r;
    }
    let Some(Json(v)) = body else {
        return BizError::new("identity/admin/create_admin", ErrorCode::BadRequestBody)
            .with_locale("zh")
            .into_response();
    };
    let name = v.get("name").and_then(|n| n.as_str()).unwrap_or("");
    let email = v.get("email").and_then(|e| e.as_str()).unwrap_or("");
    let password = v.get("password").and_then(|p| p.as_str()).unwrap_or("");
    let role_id = v
        .get("role_id")
        .and_then(|r| r.as_i64())
        .or_else(|| v.get("roleId").and_then(|r| r.as_i64()))
        .unwrap_or(0);
    if name.is_empty() || email.is_empty() || password.len() < 6 || role_id == 0 {
        let mut b = BizError::new("identity/admin/create_admin", ErrorCode::Validation);
        b.locale = "zh";
        return b.into_response();
    }
    match admin_ops::create_admin(&st.shared, name, email, password, role_id).await {
        Ok(admin) => {
            admin_ops::audit(
                &st.shared,
                admin_id_of(&authed),
                &authed.claims.sub,
                "创建管理员",
                &admin.email,
                &client_ip(&headers),
                ua(&headers).as_deref(),
            )
            .await;
            let mut resp = Json(R::ok(Some(json!({
                "id": admin.id, "name": admin.name, "email": admin.email,
                "role_id": admin.role_id, "role_name": null,
                "status": status_name(admin.status), "last_login_at": admin.last_login_at,
            }))))
            .into_response();
            *resp.status_mut() = axum::http::StatusCode::CREATED;
            resp
        }
        Err(e) => svc_err_resp("identity/admin/create_admin", e),
    }
}

async fn update_admin(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    headers: HeaderMap,
    Path(id): Path<i64>,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/system/admins").await {
        return *r;
    }
    let Some(Json(v)) = body else {
        return BizError::new("identity/admin/update_admin", ErrorCode::BadRequestBody)
            .with_locale("zh")
            .into_response();
    };
    let name = v.get("name").and_then(|n| n.as_str());
    let role_id = v
        .get("role_id")
        .and_then(|r| r.as_i64())
        .or_else(|| v.get("roleId").and_then(|r| r.as_i64()));
    match admin_ops::update_admin(&st.shared, id, admin_id_of(&authed), name, role_id).await {
        Ok(admin) => {
            admin_ops::audit(
                &st.shared,
                admin_id_of(&authed),
                &authed.claims.sub,
                "编辑管理员",
                &admin.email,
                &client_ip(&headers),
                ua(&headers).as_deref(),
            )
            .await;
            Json(R::ok(Some(json!({
                "id": admin.id, "name": admin.name, "email": admin.email,
                "role_id": admin.role_id, "role_name": null,
                "status": status_name(admin.status), "last_login_at": admin.last_login_at,
            }))))
            .into_response()
        }
        Err(e) => svc_err_resp("identity/admin/update_admin", e),
    }
}

async fn delete_admin(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    headers: HeaderMap,
    Path(id): Path<i64>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/system/admins").await {
        return *r;
    }
    match admin_ops::delete_admin(&st.shared, id, admin_id_of(&authed)).await {
        Ok(()) => {
            admin_ops::audit(
                &st.shared,
                admin_id_of(&authed),
                &authed.claims.sub,
                "删除管理员",
                &format!("ID:{id}"),
                &client_ip(&headers),
                ua(&headers).as_deref(),
            )
            .await;
            Json(R::<serde_json::Value>::ok(None)).into_response()
        }
        Err(e) => svc_err_resp("identity/admin/delete_admin", e),
    }
}

async fn toggle_admin_status(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    headers: HeaderMap,
    Path(id): Path<i64>,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/system/admins").await {
        return *r;
    }
    let Some(Json(v)) = body else {
        return BizError::new("identity/admin/toggle_status", ErrorCode::BadRequestBody)
            .with_locale("zh")
            .into_response();
    };
    let status_str = v
        .get("status")
        .and_then(|s| s.as_str())
        .unwrap_or("")
        .to_uppercase();
    let Ok(status) = status_str.parse::<i32>() else {
        let mut b = BizError::new("identity/admin/toggle_status", ErrorCode::Validation);
        b.locale = "zh";
        return b.into_response();
    };
    match admin_ops::toggle_admin_status(&st.shared, id, admin_id_of(&authed), status).await {
        Ok(admin) => {
            admin_ops::audit(
                &st.shared,
                admin_id_of(&authed),
                &authed.claims.sub,
                "禁用管理员",
                &admin.email,
                &client_ip(&headers),
                ua(&headers).as_deref(),
            )
            .await;
            Json(R::ok(Some(json!({
                "id": admin.id, "name": admin.name, "email": admin.email,
                "role_id": admin.role_id, "role_name": null,
                "status": status_name(admin.status), "last_login_at": admin.last_login_at,
            }))))
            .into_response()
        }
        Err(e) => svc_err_resp("identity/admin/toggle_status", e),
    }
}

async fn reset_password(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    headers: HeaderMap,
    Path(id): Path<i64>,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/system/admins").await {
        return *r;
    }
    let Some(Json(v)) = body else {
        return BizError::new("identity/admin/reset_password", ErrorCode::BadRequestBody)
            .with_locale("zh")
            .into_response();
    };
    let new_password = v.get("new_password").and_then(|p| p.as_str()).unwrap_or("");
    if new_password.len() < 6 {
        let mut b = BizError::new("identity/admin/reset_password", ErrorCode::Validation);
        b.locale = "zh";
        return b.into_response();
    }
    match admin_ops::reset_admin_password(&st.shared, id, new_password).await {
        Ok(()) => {
            admin_ops::audit(
                &st.shared,
                admin_id_of(&authed),
                &authed.claims.sub,
                "重置密码",
                &format!("ID:{id}"),
                &client_ip(&headers),
                ua(&headers).as_deref(),
            )
            .await;
            Json(R::<serde_json::Value>::ok(None)).into_response()
        }
        Err(e) => svc_err_resp("identity/admin/reset_password", e),
    }
}

// ══════════ roles ══════════

async fn list_roles(authed: AuthedAdmin, State(st): State<AdminState>) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/system/roles").await {
        return *r;
    }
    match admin_ops::list_roles(&st.shared).await {
        Ok(roles) => Json(R::ok(Some(json!(roles
            .iter()
            .map(|r| json!({
                "id": r.id, "name": r.name, "type": r.r#type, "is_locked": r.is_locked,
                "member_count": r.member_count, "permission_keys": r.permission_keys,
            }))
            .collect::<Vec<_>>()))))
        .into_response(),
        Err(e) => svc_err_resp("identity/admin/list_roles", e),
    }
}

async fn create_role(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    headers: HeaderMap,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/system/roles").await {
        return *r;
    }
    let Some(Json(v)) = body else {
        return BizError::new("identity/admin/create_role", ErrorCode::BadRequestBody)
            .with_locale("zh")
            .into_response();
    };
    let name = v.get("name").and_then(|n| n.as_str()).unwrap_or("");
    if name.is_empty() || name.chars().count() > 40 {
        let mut b = BizError::new("identity/admin/create_role", ErrorCode::Validation);
        b.locale = "zh";
        return b.into_response();
    }
    match admin_ops::create_role(&st.shared, name).await {
        Ok(role) => {
            admin_ops::audit(
                &st.shared,
                admin_id_of(&authed),
                &authed.claims.sub,
                "创建角色",
                &role.name,
                &client_ip(&headers),
                ua(&headers).as_deref(),
            )
            .await;
            let mut resp = Json(R::ok(Some(json!({
                "id": role.id, "name": role.name, "type": role.r#type, "is_locked": role.is_locked,
                "member_count": role.member_count, "permission_keys": role.permission_keys,
            }))))
            .into_response();
            *resp.status_mut() = axum::http::StatusCode::CREATED;
            resp
        }
        Err(e) => svc_err_resp("identity/admin/create_role", e),
    }
}

async fn update_role(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    headers: HeaderMap,
    Path(id): Path<i64>,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/system/roles").await {
        return *r;
    }
    let Some(Json(v)) = body else {
        return BizError::new("identity/admin/update_role", ErrorCode::BadRequestBody)
            .with_locale("zh")
            .into_response();
    };
    let name = v.get("name").and_then(|n| n.as_str());
    let keys: Option<Vec<String>> =
        v.get("permission_keys")
            .and_then(|k| k.as_array())
            .map(|arr| {
                arr.iter()
                    .filter_map(|s| s.as_str().map(|s| s.to_string()))
                    .collect()
            });
    match admin_ops::update_role(&st.shared, id, name, keys.as_deref()).await {
        Ok(()) => {
            admin_ops::audit(
                &st.shared,
                admin_id_of(&authed),
                &authed.claims.sub,
                "权限变更",
                &format!("ID:{id}"),
                &client_ip(&headers),
                ua(&headers).as_deref(),
            )
            .await;
            Json(R::<serde_json::Value>::ok(None)).into_response()
        }
        Err(e) => svc_err_resp("identity/admin/update_role", e),
    }
}

async fn delete_role(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    headers: HeaderMap,
    Path(id): Path<i64>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/system/roles").await {
        return *r;
    }
    match admin_ops::delete_role(&st.shared, id).await {
        Ok(()) => {
            admin_ops::audit(
                &st.shared,
                admin_id_of(&authed),
                &authed.claims.sub,
                "删除角色",
                &format!("ID:{id}"),
                &client_ip(&headers),
                ua(&headers).as_deref(),
            )
            .await;
            Json(R::<serde_json::Value>::ok(None)).into_response()
        }
        Err(e) => svc_err_resp("identity/admin/delete_role", e),
    }
}

async fn list_permission_dict(authed: AuthedAdmin, State(st): State<AdminState>) -> Response {
    // 对齐 Java RoleController:GET /api/admin/permissions 无 @RequirePermission(超管登录即可)
    let _ = authed;
    match admin_ops::list_permissions(&st.shared).await {
        Ok(perms) => Json(R::ok(Some(json!(perms
            .iter()
            .map(|p| json!({
                "key": p.perm_code, "group": p.group, "label": p.label,
            }))
            .collect::<Vec<_>>()))))
        .into_response(),
        Err(e) => svc_err_resp("identity/admin/list_permissions", e),
    }
}

// ══════════ users 运营 ══════════

async fn list_users(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    axum::extract::Query(q): axum::extract::Query<std::collections::HashMap<String, String>>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/customers").await {
        return *r;
    }
    let page: u64 = q.get("page").and_then(|v| v.parse().ok()).unwrap_or(1);
    let page_size: u64 = q.get("pageSize").and_then(|v| v.parse().ok()).unwrap_or(20);
    let status: Option<i32> = q.get("status").and_then(|v| v.parse().ok());
    let tier: Option<i32> = q.get("tier").and_then(|v| v.parse().ok());
    let email = q.get("email").cloned();

    let mut conds = vec![];
    if let Some(st) = status {
        conds.push(crate::service::Cond::Eq(
            crate::service::Col::Status,
            crate::service::CondVal::Num(st as i64),
        ));
    }
    if let Some(t) = tier {
        conds.push(crate::service::Cond::Eq(
            crate::service::Col::Tier,
            crate::service::CondVal::Num(t as i64),
        ));
    }
    if let Some(ref e) = email {
        if !e.trim().is_empty() {
            conds.push(crate::service::Cond::EmailLikePrefix(
                e.trim().to_lowercase(),
            ));
        }
    }
    let query = crate::service::ListQuery {
        conds,
        order: (crate::service::Col::CreatedAt, true),
        page,
        page_size,
    };
    match crate::service::user_query::list_users(&st.shared, &query).await {
        Ok((items, total)) => Json(R::ok(Some(json!({
            "data": items.iter().map(|u| json!({
                "id": u.id, "email": u.email, "email_verified": u.email_verified,
                "name": u.name, "phone": u.phone, "tier": u.tier, "status": u.status,
                "avatar": u.avatar, "joined_at": u.joined_at, "locale_pref": u.locale_pref,
            })).collect::<Vec<_>>(),
            "total_elements": total,
            "page_number": page, "page_size": page_size,
            "number_of_elements": items.len(),
            "total_pages": total.div_ceil(page_size),
        }))))
        .into_response(),
        Err(e) => svc_err_resp("identity/admin/list_users", e),
    }
}

async fn get_user_detail(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    Path(id): Path<i64>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/customers").await {
        return *r;
    }
    match admin_ops::user_detail(&st.shared, id, 20).await {
        Ok(d) => Json(R::ok(Some(json!({
            "user": {
                "id": d.user.id, "email": d.user.email, "email_verified": d.user.email_verified != 0,
                "name": d.user.name, "phone": d.user.phone, "tier": d.user.tier,
                "status": d.user.status, "avatar": d.user.avatar,
                "joined_at": d.user.joined_at.map(common::time::format_iso),
                "locale_pref": d.user.locale_pref,
            },
            "identities": d.identities.iter().map(|i| i.to_json()).collect::<Vec<_>>(),
            "sessions": d.sessions.iter().map(|s| json!({
                "id": s.id, "device": s.device, "browser": s.browser, "ip": s.ip,
                "location": s.location, "method": s.method, "status": s.status,
                "last_active_at": s.last_active_at,
            })).collect::<Vec<_>>(),
            "login_history": d.login_history.iter().map(|h| json!({
                "id": h.id, "method": h.method, "ip": h.ip, "device": h.device,
                "result": h.result, "created_at": h.created_at,
            })).collect::<Vec<_>>(),
        }))))
        .into_response(),
        Err(e) => svc_err_resp("identity/admin/get_user_detail", e),
    }
}

async fn toggle_user_status(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    headers: HeaderMap,
    Path(id): Path<i64>,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/customers").await {
        return *r;
    }
    let Some(Json(v)) = body else {
        return BizError::new("identity/admin/toggle_user", ErrorCode::BadRequestBody)
            .with_locale("zh")
            .into_response();
    };
    let status_str = v
        .get("status")
        .and_then(|s| s.as_str())
        .unwrap_or("")
        .to_uppercase();
    let Ok(status) = status_str.parse::<i32>() else {
        let mut b = BizError::new("identity/admin/toggle_user", ErrorCode::Validation);
        b.locale = "zh";
        return b.into_response();
    };
    match admin_ops::toggle_user_status(&st.shared, id, status).await {
        Ok(u) => {
            admin_ops::audit(
                &st.shared,
                admin_id_of(&authed),
                &authed.claims.sub,
                "用户禁用",
                &u.email,
                &client_ip(&headers),
                ua(&headers).as_deref(),
            )
            .await;
            Json(R::ok(Some(json!({
                "id": u.id, "email": u.email, "email_verified": u.email_verified != 0,
                "name": u.name, "phone": u.phone, "tier": u.tier, "status": u.status,
                "avatar": u.avatar, "joined_at": u.joined_at.map(common::time::format_iso),
                "locale_pref": u.locale_pref,
            }))))
            .into_response()
        }
        Err(e) => svc_err_resp("identity/admin/toggle_user", e),
    }
}

async fn force_logout(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    headers: HeaderMap,
    Path(id): Path<i64>,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/customers").await {
        return *r;
    }
    let Some(Json(v)) = body else {
        return BizError::new("identity/admin/force_logout", ErrorCode::BadRequestBody)
            .with_locale("zh")
            .into_response();
    };
    let scope = v
        .get("scope")
        .and_then(|s| s.as_str())
        .unwrap_or("all")
        .to_string();
    let session_id: Option<i64> = v.get("session_id").and_then(|s| {
        s.as_str()
            .and_then(|v| v.parse().ok())
            .or_else(|| s.as_i64())
    });
    match admin_ops::force_logout(&st.shared, id, &scope, session_id).await {
        Ok(()) => {
            admin_ops::audit(
                &st.shared,
                admin_id_of(&authed),
                &authed.claims.sub,
                "强制下线",
                &format!("ID:{id}"),
                &client_ip(&headers),
                ua(&headers).as_deref(),
            )
            .await;
            Json(R::<serde_json::Value>::ok(None)).into_response()
        }
        Err(e) => svc_err_resp("identity/admin/force_logout", e),
    }
}

// ══════════ auth-config ══════════

async fn get_auth_config(authed: AuthedAdmin, State(st): State<AdminState>) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/system/auth").await {
        return *r;
    }
    match authconfig::get(&st.shared).await {
        Ok(cfg) => Json(R::ok(Some(json!({
            "email_enabled": cfg.email_enabled, "google_enabled": cfg.google_enabled,
            "apple_enabled": cfg.apple_enabled, "otp_length": cfg.otp_length,
            "otp_ttl_minutes": cfg.otp_ttl_minutes, "otp_resend_seconds": cfg.otp_resend_seconds,
            "otp_max_attempts": cfg.otp_max_attempts, "min_methods": cfg.min_methods,
            "google_client_id": cfg.google_client_id, "apple_service_id": cfg.apple_service_id,
        }))))
        .into_response(),
        Err(e) => svc_err_resp("identity/admin/get_auth_config", e),
    }
}

async fn update_auth_config(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    headers: HeaderMap,
    body: Option<Json<serde_json::Value>>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/system/auth").await {
        return *r;
    }
    let Some(Json(v)) = body else {
        return BizError::new(
            "identity/admin/update_auth_config",
            ErrorCode::BadRequestBody,
        )
        .with_locale("zh")
        .into_response();
    };
    let b = |k: &str| v.get(k).and_then(|x| x.as_bool());
    let i = |k: &str| v.get(k).and_then(|x| x.as_i64()).map(|x| x as i32);
    let s = |k: &str| v.get(k).and_then(|x| x.as_str()).map(|x| x.to_string());
    let patch = authconfig::AuthConfigUpdate {
        google_enabled: b("google_enabled"),
        apple_enabled: b("apple_enabled"),
        otp_length: i("otp_length").map(|x| x as i8),
        otp_ttl_minutes: i("otp_ttl_minutes"),
        otp_resend_seconds: i("otp_resend_seconds"),
        otp_max_attempts: i("otp_max_attempts"),
        min_methods: i("min_methods").map(|x| x as i8),
        google_client_id: s("google_client_id"),
        apple_service_id: s("apple_service_id"),
    };
    match authconfig::update(&st.shared, &patch).await {
        Ok(cfg) => {
            admin_ops::audit(
                &st.shared,
                admin_id_of(&authed),
                &authed.claims.sub,
                "认证配置变更",
                "auth_config",
                &client_ip(&headers),
                ua(&headers).as_deref(),
            )
            .await;
            Json(R::ok(Some(json!({
                "email_enabled": cfg.email_enabled, "google_enabled": cfg.google_enabled,
                "apple_enabled": cfg.apple_enabled, "otp_length": cfg.otp_length,
                "otp_ttl_minutes": cfg.otp_ttl_minutes, "otp_resend_seconds": cfg.otp_resend_seconds,
                "otp_max_attempts": cfg.otp_max_attempts, "min_methods": cfg.min_methods,
                "google_client_id": cfg.google_client_id, "apple_service_id": cfg.apple_service_id,
            }))))
            .into_response()
        }
        Err(e) => svc_err_resp("identity/admin/update_auth_config", e),
    }
}

// ══════════ operation-logs(次连接读 identity 库) ══════════

async fn list_operation_logs(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    axum::extract::Query(q): axum::extract::Query<std::collections::HashMap<String, String>>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/system/logs").await {
        return *r;
    }
    let Some(legacy) = st.shared.db_legacy.as_ref() else {
        return svc_err_resp("identity/admin/operation_logs", SvcError::code(50001));
    };
    let page: u64 = q.get("page").and_then(|v| v.parse().ok()).unwrap_or(1);
    let page_size: u64 = q.get("pageSize").and_then(|v| v.parse().ok()).unwrap_or(20);
    let action = q.get("action").cloned();
    let operator_id: Option<i64> = q.get("operator_id").and_then(|v| v.parse().ok());
    let from = q.get("from").cloned();
    let to = q.get("to").cloned();

    let mut where_clauses = vec!["1=1".to_string()];
    let mut params: Vec<sea_orm::sea_query::Value> = vec![];
    if let Some(a) = &action {
        if !a.is_empty() {
            where_clauses.push("action = ?".into());
            params.push(a.clone().into());
        }
    }
    if let Some(oid) = operator_id {
        where_clauses.push("operator_id = ?".into());
        params.push(oid.into());
    }
    if let Some(f) = &from {
        where_clauses.push("created_at >= ?".into());
        params.push(f.clone().into());
    }
    if let Some(t) = &to {
        where_clauses.push("created_at <= ?".into());
        params.push(t.clone().into());
    }
    let where_sql = where_clauses.join(" AND ");
    let total_params = params.clone();
    let total: i64 = legacy
        .query_one(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            format!(r#"SELECT COUNT(*) FROM operation_log WHERE {where_sql}"#),
            total_params,
        ))
        .await
        .ok()
        .flatten()
        .and_then(|r| r.try_get_by_index::<i64>(0).ok())
        .unwrap_or(0);
    let offset = (page - 1) * page_size;
    let mut page_params = params;
    page_params.push((page_size as i64).into());
    page_params.push((offset as i64).into());
    let rows = legacy
        .query_all(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            format!(
                r#"SELECT id, operator_name, action, target, ip, changes, created_at
                       FROM operation_log WHERE {where_sql} ORDER BY id DESC LIMIT ? OFFSET ?"#
            ),
            page_params,
        ))
        .await;
    let Ok(rows) = rows else {
        return svc_err_resp("identity/admin/operation_logs", SvcError::code(50001));
    };
    let items: Vec<serde_json::Value> = rows
        .iter()
        .map(|r| {
            json!({
                "id": r.try_get_by_index::<u64>(0).unwrap_or(0),
                "operator_name": r.try_get_by_index::<Option<String>>(1).ok().flatten(),
                "action": r.try_get_by_index::<String>(2).unwrap_or_default(),
                "target": r.try_get_by_index::<Option<String>>(3).ok().flatten(),
                "ip": r.try_get_by_index::<Option<String>>(4).ok().flatten(),
                "changes": r.try_get_by_index::<Option<String>>(5).ok().flatten(),
                "created_at": r.try_get_by_index::<chrono::NaiveDateTime>(6).map(common::time::format_iso).unwrap_or_default(),
            })
        })
        .collect();
    let count = items.len();
    Json(R::ok(Some(json!({
        "data": items,
        "total_elements": total,
        "page_number": page, "page_size": page_size,
        "number_of_elements": count,
        "total_pages": (total as u64).div_ceil(page_size),
    }))))
    .into_response()
}

async fn export_operation_logs(
    authed: AuthedAdmin,
    State(st): State<AdminState>,
    axum::extract::Query(q): axum::extract::Query<std::collections::HashMap<String, String>>,
) -> Response {
    if let Err(r) = require_permission(&st.shared, admin_id_of(&authed), "/system/logs").await {
        return *r;
    }
    // from/to 必传且跨度 ≤92 天(对齐 Java AuditService)
    let (Some(from), Some(to)) = (q.get("from").cloned(), q.get("to").cloned()) else {
        let mut b = BizError::new("identity/admin/export_logs", ErrorCode::Validation);
        b.locale = "zh";
        return b.into_response();
    };
    if let (Ok(f), Ok(t)) = (
        chrono::NaiveDateTime::parse_from_str(&from, "%Y-%m-%dT%H:%M:%S"),
        chrono::NaiveDateTime::parse_from_str(&to, "%Y-%m-%dT%H:%M:%S"),
    ) {
        if t.signed_duration_since(f).num_days() > 92 {
            let mut b = BizError::new("identity/admin/export_logs", ErrorCode::Validation);
            b.locale = "zh";
            return b.with_message("时间跨度不能超过 92 天").into_response();
        }
    }
    let Some(legacy) = st.shared.db_legacy.as_ref() else {
        return svc_err_resp("identity/admin/export_logs", SvcError::code(50001));
    };
    let mut where_clauses = vec!["created_at >= ?".to_string(), "created_at <= ?".to_string()];
    let mut params: Vec<sea_orm::sea_query::Value> = vec![from.into(), to.into()];
    if let Some(a) = q.get("action") {
        if !a.is_empty() {
            where_clauses.push("action = ?".into());
            params.push(a.clone().into());
        }
    }
    if let Some(oid) = q.get("operator_id").and_then(|v| v.parse::<i64>().ok()) {
        where_clauses.push("operator_id = ?".into());
        params.push(oid.into());
    }
    let rows = legacy
        .query_all(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            format!(
                r#"SELECT id, operator_name, action, target, ip, created_at
                   FROM operation_log WHERE {} ORDER BY id"#,
                where_clauses.join(" AND ")
            ),
            params,
        ))
        .await;
    let Ok(rows) = rows else {
        return svc_err_resp("identity/admin/export_logs", SvcError::code(50001));
    };
    // CSV 构建(表头 + 双引号转义,对齐 Java csv())
    let mut csv = String::from("id,operator_name,action,target,ip,created_at\n");
    for r in &rows {
        let esc = |v: String| format!("\"{}\"", v.replace('"', "\"\""));
        csv.push_str(&format!(
            "{},{},{},{},{},{}\n",
            r.try_get_by_index::<u64>(0).unwrap_or(0),
            esc(r
                .try_get_by_index::<Option<String>>(1)
                .ok()
                .flatten()
                .unwrap_or_default()),
            esc(r.try_get_by_index::<String>(2).unwrap_or_default()),
            esc(r
                .try_get_by_index::<Option<String>>(3)
                .ok()
                .flatten()
                .unwrap_or_default()),
            esc(r
                .try_get_by_index::<Option<String>>(4)
                .ok()
                .flatten()
                .unwrap_or_default()),
            r.try_get_by_index::<chrono::NaiveDateTime>(5)
                .map(common::time::format_iso)
                .unwrap_or_default(),
        ));
    }
    let mut resp = csv.into_response();
    resp.headers_mut().insert(
        axum::http::header::CONTENT_TYPE,
        axum::http::HeaderValue::from_static("text/csv;charset=UTF-8"),
    );
    resp.headers_mut().insert(
        axum::http::header::CONTENT_DISPOSITION,
        axum::http::HeaderValue::from_static("attachment; filename=operation-logs.csv"),
    );
    resp
}

/// DELETE → 405 固定中文(操作日志只读)
async fn forbidden_delete_logs(authed: AuthedAdmin) -> Response {
    let _ = authed;
    let body = json!({"code": 40500, "message": "操作日志只读，不可删除", "service_id": null, "data": null});
    let mut resp = Json(body).into_response();
    *resp.status_mut() = axum::http::StatusCode::METHOD_NOT_ALLOWED;
    resp
}
