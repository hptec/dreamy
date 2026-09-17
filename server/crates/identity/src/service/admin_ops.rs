//! admin 运营服务(FLOW-10/12):管理员 CRUD、角色 CRUD、用户运营、认证配置、审计写入。

use common::state::SharedState;
use sea_orm::sea_query::Expr;
use sea_orm::{
    ActiveModelTrait, ColumnTrait, Condition, EntityTrait, PaginatorTrait, QueryFilter, QueryOrder,
    QuerySelect, Set, TransactionTrait,
};

use crate::entity::{admin_session, admin_user, login_history, operation_log, permission, role,
    role_permission, user, user_session};
use crate::enums::{AdminStatus, RoleType, SessionStatus, UserStatus};
use crate::service::{permissions, session, user_query, SvcError};

// ══════════ 管理员 CRUD(FLOW-10) ══════════

pub struct AdminRow {
    pub id: i64,
    pub name: Option<String>,
    pub email: String,
    pub role_id: i64,
    pub status: i32,
    pub last_login_at: Option<String>,
}

fn admin_row(a: admin_user::Model) -> AdminRow {
    AdminRow {
        id: a.id as i64,
        name: a.name,
        email: a.email,
        role_id: a.role_id,
        status: a.status as i32,
        last_login_at: a.last_login_at.map(common::time::format_iso),
    }
}

async fn fetch_admin(state: &SharedState, admin_id: i64) -> Result<Option<AdminRow>, SvcError> {
    Ok(admin_user::Entity::find_by_id(admin_id as u64)
        .one(&state.db)
        .await?
        .map(admin_row))
}

pub async fn get_admin(state: &SharedState, admin_id: i64) -> Result<AdminRow, SvcError> {
    fetch_admin(state, admin_id)
        .await?
        .ok_or(SvcError::code(40400))
}

pub async fn page_admins(
    state: &SharedState,
    page: u64,
    page_size: u64,
    status: Option<i32>,
    role_id: Option<i64>,
) -> Result<(Vec<AdminRow>, u64), SvcError> {
    let mut cond = Condition::all();
    if let Some(st) = status {
        cond = cond.add(admin_user::Column::Status.eq(st as i8));
    }
    if let Some(rid) = role_id {
        cond = cond.add(admin_user::Column::RoleId.eq(rid));
    }
    let total = admin_user::Entity::find()
        .filter(cond.clone())
        .count(&state.db)
        .await?;
    let offset = (page - 1) * page_size;
    let rows = admin_user::Entity::find()
        .filter(cond)
        .order_by_asc(admin_user::Column::Id)
        .limit(page_size)
        .offset(offset)
        .all(&state.db)
        .await?;
    Ok((rows.into_iter().map(admin_row).collect(), total))
}

pub async fn create_admin(
    state: &SharedState,
    name: &str,
    email: &str,
    password: &str,
    role_id: i64,
) -> Result<AdminRow, SvcError> {
    let email = email.trim().to_lowercase();
    // 40901 邮箱重复
    if admin_email_exists(state, &email).await? {
        return Err(SvcError::code(40901));
    }
    // 40000 角色不存在
    if !role_exists(state, role_id).await? {
        let details = serde_json::json!({"roleId": "role not found"});
        return Err(SvcError::code_with(40000, details));
    }
    let hash = bcrypt::hash(password, bcrypt::DEFAULT_COST).map_err(|_| SvcError::code(50000))?;
    let created = admin_user::ActiveModel {
        name: Set(Some(name.to_string())),
        email: Set(email),
        password_hash: Set(hash),
        role_id: Set(role_id),
        status: Set(AdminStatus::Active.code() as i8),
        version: Set(0),
        ..Default::default()
    }
    .insert(&state.db)
    .await?;
    fetch_admin(state, created.id as i64)
        .await?
        .ok_or(SvcError::code(50000))
}

pub async fn update_admin(
    state: &SharedState,
    admin_id: i64,
    operator_id: i64,
    name: Option<&str>,
    role_id: Option<i64>,
) -> Result<AdminRow, SvcError> {
    let target = get_admin(state, admin_id).await?;
    if let Some(new_role) = role_id {
        // 40306:超管降权(操作者是超管且目标是超管→改角色)
        if is_super_admin(state, operator_id).await?
            && is_super_role_id(state, target.role_id).await?
        {
            return Err(SvcError::code(40306));
        }
        if !role_exists(state, new_role).await? {
            let details = serde_json::json!({"roleId": "role not found"});
            return Err(SvcError::code_with(40000, details));
        }
    }
    // COALESCE 语义:仅更新提供字段 + version 自增
    let now = chrono::Local::now().naive_local();
    let mut upd = admin_user::Entity::update_many()
        .col_expr(
            admin_user::Column::Version,
            Expr::col(admin_user::Column::Version).add(1),
        )
        .col_expr(admin_user::Column::UpdatedAt, Expr::value(now))
        .filter(admin_user::Column::Id.eq(admin_id as u64))
        .to_owned();
    if let Some(n) = name {
        upd = upd.col_expr(admin_user::Column::Name, Expr::value(n.to_string()));
    }
    if let Some(rid) = role_id {
        upd = upd.col_expr(admin_user::Column::RoleId, Expr::value(rid));
    }
    upd.exec(&state.db).await?;
    get_admin(state, admin_id).await
}

pub async fn delete_admin(
    state: &SharedState,
    admin_id: i64,
    operator_id: i64,
) -> Result<(), SvcError> {
    // 40307:删自己
    if admin_id == operator_id {
        return Err(SvcError::code(40307));
    }
    let target = get_admin(state, admin_id).await?;
    // 40306:超管保护
    if is_super_role_id(state, target.role_id).await? {
        return Err(SvcError::code(40306));
    }
    let txn = state.db.begin().await?;
    admin_user::Entity::delete_by_id(admin_id as u64)
        .exec(&txn)
        .await?;
    admin_session::Entity::delete_many()
        .filter(admin_session::Column::AdminId.eq(admin_id))
        .exec(&txn)
        .await?;
    txn.commit().await?;
    Ok(())
}

pub async fn toggle_admin_status(
    state: &SharedState,
    admin_id: i64,
    operator_id: i64,
    status: i32,
) -> Result<AdminRow, SvcError> {
    let target = get_admin(state, admin_id).await?;
    if status != AdminStatus::Active.code() {
        // 禁用:超管保护
        if is_super_role_id(state, target.role_id).await? {
            return Err(SvcError::code(40306));
        }
    }
    admin_user::Entity::update_many()
        .col_expr(admin_user::Column::Status, Expr::value(status as i8))
        .col_expr(
            admin_user::Column::UpdatedAt,
            Expr::value(chrono::Local::now().naive_local()),
        )
        .col_expr(
            admin_user::Column::Version,
            Expr::col(admin_user::Column::Version).add(1),
        )
        .filter(admin_user::Column::Id.eq(admin_id as u64))
        .exec(&state.db)
        .await?;
    if status != AdminStatus::Active.code() {
        // 禁用即级联撤销全部会话
        revoke_all_admin_sessions(state, admin_id).await;
    }
    let _ = operator_id;
    get_admin(state, admin_id).await
}

pub async fn reset_admin_password(
    state: &SharedState,
    admin_id: i64,
    new_password: &str,
) -> Result<(), SvcError> {
    let hash =
        bcrypt::hash(new_password, bcrypt::DEFAULT_COST).map_err(|_| SvcError::code(50000))?;
    admin_user::Entity::update_many()
        .col_expr(admin_user::Column::PasswordHash, Expr::value(hash))
        .col_expr(
            admin_user::Column::UpdatedAt,
            Expr::value(chrono::Local::now().naive_local()),
        )
        .col_expr(
            admin_user::Column::Version,
            Expr::col(admin_user::Column::Version).add(1),
        )
        .filter(admin_user::Column::Id.eq(admin_id as u64))
        .exec(&state.db)
        .await?;
    revoke_all_admin_sessions(state, admin_id).await;
    Ok(())
}

async fn revoke_all_admin_sessions(state: &SharedState, admin_id: i64) {
    let Ok(tokens) = admin_session::Entity::find()
        .filter(admin_session::Column::AdminId.eq(admin_id))
        .filter(admin_session::Column::Status.eq(SessionStatus::Active.code() as i8))
        .all(&state.db)
        .await
    else {
        return;
    };
    admin_session::Entity::update_many()
        .col_expr(
            admin_session::Column::Status,
            Expr::value(SessionStatus::Revoked.code() as i8),
        )
        .col_expr(
            admin_session::Column::UpdatedAt,
            Expr::value(chrono::Local::now().naive_local()),
        )
        .filter(admin_session::Column::AdminId.eq(admin_id))
        .filter(admin_session::Column::Status.eq(SessionStatus::Active.code() as i8))
        .exec(&state.db)
        .await
        .ok();
    for t in &tokens {
        crate::service::session::revoke_admin(state, &t.token_id).await;
    }
}

async fn admin_email_exists(state: &SharedState, email: &str) -> Result<bool, SvcError> {
    let found = admin_user::Entity::find()
        .filter(admin_user::Column::Email.eq(email))
        .one(&state.db)
        .await?;
    Ok(found.is_some())
}

async fn role_exists(state: &SharedState, role_id: i64) -> Result<bool, SvcError> {
    let found = role::Entity::find_by_id(role_id as u64)
        .one(&state.db)
        .await?;
    Ok(found.is_some())
}

pub async fn is_super_admin(state: &SharedState, admin_id: i64) -> Result<bool, SvcError> {
    let admin = admin_user::Entity::find_by_id(admin_id as u64)
        .one(&state.db)
        .await?;
    let Some(admin) = admin else {
        return Ok(false);
    };
    is_super_role_id(state, admin.role_id).await
}

async fn is_super_role_id(state: &SharedState, role_id: i64) -> Result<bool, SvcError> {
    let locked = role::Entity::find_by_id(role_id as u64)
        .one(&state.db)
        .await?
        .map(|r| r.is_locked != 0)
        .unwrap_or(false);
    Ok(locked)
}

// ══════════ 角色 CRUD ══════════

pub struct RoleRow {
    pub id: i64,
    pub name: String,
    pub r#type: i32,
    pub is_locked: bool,
    pub member_count: i64,
    pub permission_keys: Vec<String>,
}

pub async fn list_roles(state: &SharedState) -> Result<Vec<RoleRow>, SvcError> {
    let roles = role::Entity::find()
        .order_by_asc(role::Column::Id)
        .all(&state.db)
        .await?;
    // member_count 单查询聚合(防 N+1)
    let counts: Vec<(i64, i64)> = admin_user::Entity::find()
        .select_only()
        .column(admin_user::Column::RoleId)
        .column_as(admin_user::Column::Id.count(), "member_count")
        .group_by(admin_user::Column::RoleId)
        .into_tuple()
        .all(&state.db)
        .await?;
    let count_map: std::collections::HashMap<i64, i64> = counts.into_iter().collect();
    let mut list = Vec::with_capacity(roles.len());
    for r in roles {
        let keys = role_permission_keys(state, r.id as i64).await?;
        list.push(RoleRow {
            id: r.id as i64,
            name: r.name,
            r#type: r.r#type as i32,
            is_locked: r.is_locked != 0,
            member_count: count_map.get(&(r.id as i64)).copied().unwrap_or(0),
            permission_keys: keys,
        });
    }
    Ok(list)
}

async fn role_permission_keys(state: &SharedState, role_id: i64) -> Result<Vec<String>, SvcError> {
    let rps = role_permission::Entity::find()
        .filter(role_permission::Column::RoleId.eq(role_id))
        .all(&state.db)
        .await?;
    let pids: Vec<i64> = rps.iter().map(|r| r.permission_id).collect();
    if pids.is_empty() {
        return Ok(vec![]);
    }
    let perms = permission::Entity::find()
        .filter(permission::Column::Id.is_in(pids))
        .order_by_asc(permission::Column::Id)
        .all(&state.db)
        .await?;
    Ok(perms.into_iter().map(|p| p.perm_code).collect())
}

pub async fn create_role(state: &SharedState, name: &str) -> Result<RoleRow, SvcError> {
    if role_name_exists(state, name).await? {
        let details = serde_json::json!({"name": "role name already exists"});
        return Err(SvcError::code_with(40000, details));
    }
    let created = role::ActiveModel {
        name: Set(name.to_string()),
        r#type: Set(RoleType::Custom.code() as i8),
        is_locked: Set(0),
        version: Set(0),
        ..Default::default()
    }
    .insert(&state.db)
    .await?;
    Ok(RoleRow {
        id: created.id as i64,
        name: created.name,
        r#type: RoleType::Custom.code(),
        is_locked: false,
        member_count: 0,
        permission_keys: vec![],
    })
}

pub async fn update_role(
    state: &SharedState,
    role_id: i64,
    name: Option<&str>,
    permission_keys: Option<&[String]>,
) -> Result<(), SvcError> {
    // 40308:锁定角色
    if is_super_role_id(state, role_id).await? {
        return Err(SvcError::code(40308));
    }
    if let Some(n) = name {
        if role_name_exists(state, n).await? {
            let details = serde_json::json!({"name": "role name already exists"});
            return Err(SvcError::code_with(40000, details));
        }
        role::Entity::update_many()
            .col_expr(role::Column::Name, Expr::value(n.to_string()))
            .col_expr(
                role::Column::UpdatedAt,
                Expr::value(chrono::Local::now().naive_local()),
            )
            .col_expr(
                role::Column::Version,
                Expr::col(role::Column::Version).add(1),
            )
            .filter(role::Column::Id.eq(role_id as u64))
            .exec(&state.db)
            .await?;
    }
    if let Some(keys) = permission_keys {
        // 先校验 key 全部存在
        for k in keys {
            if !perm_code_exists(state, k).await? {
                let details =
                    serde_json::json!({"permission_keys": format!("unknown permission: {k}")});
                return Err(SvcError::code_with(40000, details));
            }
        }
        // 全量重写(DELETE + INSERT,对齐 Java)
        let perms = permission::Entity::find()
            .filter(permission::Column::PermCode.is_in(keys.to_vec()))
            .all(&state.db)
            .await?;
        let txn = state.db.begin().await?;
        role_permission::Entity::delete_many()
            .filter(role_permission::Column::RoleId.eq(role_id))
            .exec(&txn)
            .await?;
        let rows: Vec<role_permission::ActiveModel> = perms
            .into_iter()
            .map(|p| role_permission::ActiveModel {
                role_id: Set(role_id),
                permission_id: Set(p.id as i64),
                ..Default::default()
            })
            .collect();
        if !rows.is_empty() {
            role_permission::Entity::insert_many(rows)
                .exec_without_returning(&txn)
                .await?;
        }
        txn.commit().await?;
        // 权限变更 → 该角色全部管理员缓存失效
        permissions::invalidate_by_role(state, role_id).await;
    }
    Ok(())
}

pub async fn delete_role(state: &SharedState, role_id: i64) -> Result<(), SvcError> {
    if is_super_role_id(state, role_id).await? {
        return Err(SvcError::code(40308));
    }
    // 40904:有成员
    let members = admin_user::Entity::find()
        .filter(admin_user::Column::RoleId.eq(role_id))
        .count(&state.db)
        .await?;
    if members > 0 {
        return Err(SvcError::code(40904));
    }
    let txn = state.db.begin().await?;
    role_permission::Entity::delete_many()
        .filter(role_permission::Column::RoleId.eq(role_id))
        .exec(&txn)
        .await?;
    role::Entity::delete_by_id(role_id as u64)
        .exec(&txn)
        .await?;
    txn.commit().await?;
    Ok(())
}

async fn role_name_exists(state: &SharedState, name: &str) -> Result<bool, SvcError> {
    let found = role::Entity::find()
        .filter(role::Column::Name.eq(name))
        .one(&state.db)
        .await?;
    Ok(found.is_some())
}

async fn perm_code_exists(state: &SharedState, code: &str) -> Result<bool, SvcError> {
    let found = permission::Entity::find()
        .filter(permission::Column::PermCode.eq(code))
        .one(&state.db)
        .await?;
    Ok(found.is_some())
}

pub struct PermissionRow {
    pub perm_code: String,
    pub group: String,
    pub label: String,
}

pub async fn list_permissions(state: &SharedState) -> Result<Vec<PermissionRow>, SvcError> {
    let perms = permission::Entity::find()
        .order_by_asc(permission::Column::Id)
        .all(&state.db)
        .await?;
    Ok(perms
        .into_iter()
        .map(|p| PermissionRow {
            perm_code: p.perm_code,
            group: p.group,
            label: p.label,
        })
        .collect())
}

// ══════════ 用户运营(FLOW-12) ══════════

pub async fn toggle_user_status(
    state: &SharedState,
    user_id: i64,
    status: i32,
) -> Result<crate::entity::user::Model, SvcError> {
    user::Entity::update_many()
        .col_expr(user::Column::Status, Expr::value(status as i8))
        .col_expr(
            user::Column::UpdatedAt,
            Expr::value(chrono::Local::now().naive_local()),
        )
        .col_expr(
            user::Column::Version,
            Expr::col(user::Column::Version).add(1),
        )
        .filter(user::Column::Id.eq(user_id as u64))
        .exec(&state.db)
        .await?;
    if status == UserStatus::Disabled.code() {
        session::revoke_all_for_user(state, user_id).await;
    }
    user_query::invalidate_user(state, user_id).await;
    user::Entity::find_by_id(user_id as u64)
        .one(&state.db)
        .await?
        .ok_or(SvcError::code(40400))
}

pub async fn force_logout(
    state: &SharedState,
    user_id: i64,
    scope: &str,
    session_id: Option<i64>,
) -> Result<(), SvcError> {
    if scope == "single" {
        let Some(sid) = session_id else {
            return Err(SvcError::code(40000));
        };
        let row = user_session::Entity::find_by_id(sid as u64)
            .one(&state.db)
            .await?
            .ok_or(SvcError::code(40400))?;
        if row.user_id as i64 != user_id {
            return Err(SvcError::code(40400));
        }
        session::revoke_store(state, &row.token_id, user_id).await;
    } else {
        session::revoke_all_for_user(state, user_id).await;
    }
    Ok(())
}

/// 用户详情(FLOW-12 getUserDetail:NP-001 防 N+1)
pub struct UserDetailData {
    pub user: user::Model,
    pub identities: Vec<crate::service::account::IdentityView>,
    pub sessions: Vec<SessionView>,
    pub login_history: Vec<LoginHistoryView>,
}

pub struct SessionView {
    pub id: i64,
    pub device: Option<String>,
    pub browser: Option<String>,
    pub ip: Option<String>,
    pub location: Option<String>,
    pub method: i32,
    pub status: i32,
    pub last_active_at: Option<String>,
}

pub struct LoginHistoryView {
    pub id: i64,
    pub method: i32,
    pub ip: Option<String>,
    pub device: Option<String>,
    pub result: i32,
    pub created_at: String,
}

pub async fn user_detail(
    state: &SharedState,
    user_id: i64,
    history_limit: u64,
) -> Result<UserDetailData, SvcError> {
    let me = user::Entity::find_by_id(user_id as u64)
        .one(&state.db)
        .await?
        .ok_or(SvcError::code(40400))?;
    let identities = crate::service::account::list_identities(state, user_id).await?;

    let sessions = user_session::Entity::find()
        .filter(user_session::Column::UserId.eq(user_id as u64))
        .order_by_desc(user_session::Column::Id)
        .all(&state.db)
        .await?
        .into_iter()
        .map(|s| SessionView {
            id: s.id as i64,
            device: s.device,
            browser: s.browser,
            ip: s.ip,
            location: s.location,
            method: s.method as i32,
            status: s.status as i32,
            last_active_at: s.last_active_at.map(common::time::format_iso),
        })
        .collect();

    let login_history = login_history::Entity::find()
        .filter(login_history::Column::UserId.eq(user_id as u64))
        .order_by_desc(login_history::Column::CreatedAt)
        .limit(history_limit)
        .all(&state.db)
        .await?
        .into_iter()
        .map(|h| LoginHistoryView {
            id: h.id as i64,
            method: h.method as i32,
            ip: h.ip,
            device: h.device,
            result: h.result as i32,
            created_at: common::time::format_iso(h.created_at),
        })
        .collect();

    Ok(UserDetailData {
        user: me,
        identities,
        sessions,
        login_history,
    })
}

// ══════════ 审计写入(主库 dreamy_server,只增不删) ══════════

/// admin 操作审计写入(失败 ERROR 不阻塞;REST 调用点与 gRPC AuditGate 共用)。
/// operator_id:None = 系统操作(Java MergeService 语义)。
/// operator_name 快照兜底:Java 侧(删码后)不再查 admin 名,传空且 operator_id 非空时
/// 由本侧同库查 admin_user 补齐——省一次 gRPC 往返,快照语义不变(MAP-006)。
pub async fn audit(
    state: &SharedState,
    operator_id: Option<i64>,
    operator_name: &str,
    action: &str,
    target: &str,
    ip: &str,
    user_agent: Option<&str>,
) {
    let name_snapshot = match (operator_id, operator_name) {
        (Some(id), "") => admin_user::Entity::find_by_id(id as u64)
            .one(&state.db)
            .await
            .ok()
            .flatten()
            .and_then(|a| a.name)
            .unwrap_or_else(|| id.to_string()),
        (None, _) => String::new(),
        (Some(_), name) => name.to_string(),
    };
    let result = operation_log::Entity::insert(operation_log::ActiveModel {
        operator_id: Set(operator_id),
        operator_name: Set(Some(name_snapshot)),
        action: Set(action.to_string()),
        target: Set(Some(target.to_string())),
        ip: Set(Some(ip.to_string())),
        user_agent: Set(user_agent.map(|u| u.to_string())),
        ..Default::default()
    })
    .exec_without_returning(&state.db)
    .await;
    if let Err(e) = result {
        // 对齐 Java AuditAspect 吞异常语义 + 用户指令不静默:ERROR 日志 + 可见计数
        tracing::error!(error = %e, action, "[audit] 审计写入失败(不阻塞主流程)");
    }
}

/// 批量管理员名快照(Java 订单时间线 DTO 装配;ids 1..100 由 gRPC 层校验)。
/// 仅暴露 name;不存在/已删除 id 不出现在结果中(调用方自行回退 id 字符串)。
pub async fn list_admin_names(
    state: &SharedState,
    admin_ids: &[i64],
) -> Result<Vec<(i64, String)>, SvcError> {
    if admin_ids.is_empty() {
        return Ok(Vec::new());
    }
    let ids: Vec<u64> = admin_ids.iter().map(|v| *v as u64).collect();
    let rows = admin_user::Entity::find()
        .filter(admin_user::Column::Id.is_in(ids))
        .all(&state.db)
        .await?;
    Ok(rows
        .into_iter()
        .filter_map(|a| a.name.map(|n| (a.id as i64, n)))
        .collect())
}

// ══════════ 审计查询(主库 dreamy_server;REST handler 与 gRPC AuditGate 共用) ══════════

/// operation_log 行(REST JSON 与 gRPC OperationLogRow 的共同数据源)
pub struct OperationLogRowData {
    pub id: u64,
    pub operator_name: Option<String>,
    pub action: String,
    pub target: Option<String>,
    pub ip: Option<String>,
    pub changes: Option<String>,
    pub created_at: Option<chrono::NaiveDateTime>,
}

pub struct OperationLogFilter {
    pub action: Option<String>,
    pub operator_id: Option<i64>,
    pub from: Option<String>,
    pub to: Option<String>,
}

fn oplog_condition(f: &OperationLogFilter) -> Condition {
    let mut cond = Condition::all();
    if let Some(a) = f.action.as_deref() {
        if !a.is_empty() {
            cond = cond.add(operation_log::Column::Action.eq(a));
        }
    }
    if let Some(oid) = f.operator_id {
        cond = cond.add(operation_log::Column::OperatorId.eq(oid));
    }
    if let Some(v) = f.from.as_deref() {
        if !v.is_empty() {
            // 字符串与 DATETIME 比较,MySQL 隐式转换(与原绑定参数语义一致)
            cond = cond.add(operation_log::Column::CreatedAt.gte(v.to_string()));
        }
    }
    if let Some(v) = f.to.as_deref() {
        if !v.is_empty() {
            cond = cond.add(operation_log::Column::CreatedAt.lte(v.to_string()));
        }
    }
    cond
}

fn row_data(r: operation_log::Model) -> OperationLogRowData {
    OperationLogRowData {
        id: r.id,
        operator_name: r.operator_name,
        action: r.action,
        target: r.target,
        ip: r.ip,
        changes: r.changes,
        created_at: r.created_at,
    }
}

/// 分页查询(ORDER BY id DESC;返回 (rows, total))
pub async fn query_operation_logs(
    state: &SharedState,
    filter: &OperationLogFilter,
    page: u64,
    page_size: u64,
) -> Result<(Vec<OperationLogRowData>, i64), SvcError> {
    let cond = oplog_condition(filter);
    let total = operation_log::Entity::find()
        .filter(cond.clone())
        .count(&state.db)
        .await? as i64;
    let offset = page.saturating_sub(1).saturating_mul(page_size);
    let rows = operation_log::Entity::find()
        .filter(cond)
        .order_by_desc(operation_log::Column::Id)
        .limit(page_size)
        .offset(offset)
        .all(&state.db)
        .await?;
    Ok((rows.into_iter().map(row_data).collect(), total))
}

/// 导出查询(ORDER BY id ASC;from/to 必传与 92 天窗口由调用方校验)
pub async fn stream_operation_logs(
    state: &SharedState,
    filter: &OperationLogFilter,
) -> Result<Vec<OperationLogRowData>, SvcError> {
    let rows = operation_log::Entity::find()
        .filter(oplog_condition(filter))
        .order_by_asc(operation_log::Column::Id)
        .all(&state.db)
        .await?;
    Ok(rows.into_iter().map(row_data).collect())
}
