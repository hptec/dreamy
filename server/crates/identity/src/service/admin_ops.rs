//! admin 运营服务(FLOW-10/12):管理员 CRUD、角色 CRUD、用户运营、认证配置、审计写入。

use common::state::SharedState;
use sea_orm::ConnectionTrait;
use sea_orm::Statement;
use sea_orm::TransactionTrait;

use crate::enums::{AdminStatus, RoleType, UserStatus};
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

async fn fetch_admin(state: &SharedState, admin_id: i64) -> Result<Option<AdminRow>, SvcError> {
    let row = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT id, name, email, role_id, status, last_login_at FROM admin_user WHERE id = ?"#,
            [admin_id.into()],
        ))
        .await?;
    Ok(row.map(|r| AdminRow {
        id: r.try_get_by_index::<u64>(0).unwrap_or(0) as i64,
        name: r.try_get_by_index::<Option<String>>(1).ok().flatten(),
        email: r.try_get_by_index::<String>(2).unwrap_or_default(),
        role_id: r.try_get_by_index::<i64>(3).unwrap_or(0),
        status: r.try_get_by_index::<i8>(4).unwrap_or(1) as i32,
        last_login_at: r
            .try_get_by_index::<Option<chrono::NaiveDateTime>>(5)
            .ok()
            .flatten()
            .map(common::time::format_iso),
    }))
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
    let mut where_clauses = vec!["1=1".to_string()];
    let mut params: Vec<sea_orm::sea_query::Value> = vec![];
    if let Some(st) = status {
        where_clauses.push(format!("status = {}", st));
    }
    if let Some(rid) = role_id {
        where_clauses.push("role_id = ?".to_string());
        params.push(rid.into());
    }
    let where_sql = where_clauses.join(" AND ");
    let params_clone = params.clone();
    let total: u64 = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            format!(r#"SELECT COUNT(*) FROM admin_user WHERE {where_sql}"#),
            params,
        ))
        .await?
        .and_then(|r| r.try_get_by_index::<i64>(0).ok())
        .unwrap_or(0) as u64;
    let offset = (page - 1) * page_size;
    let rows = state
        .db
        .query_all(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            format!(r#"SELECT id, name, email, role_id, status, last_login_at FROM admin_user WHERE {where_sql} ORDER BY id LIMIT ? OFFSET ?"#),
            {
                let mut p = params_clone;
                p.push((page_size as i64).into());
                p.push((offset as i64).into());
                p
            },
        ))
        .await?;
    let items = rows
        .iter()
        .map(|r| AdminRow {
            id: r.try_get_by_index::<u64>(0).unwrap_or(0) as i64,
            name: r.try_get_by_index::<Option<String>>(1).ok().flatten(),
            email: r.try_get_by_index::<String>(2).unwrap_or_default(),
            role_id: r.try_get_by_index::<i64>(3).unwrap_or(0),
            status: r.try_get_by_index::<i8>(4).unwrap_or(1) as i32,
            last_login_at: r
                .try_get_by_index::<Option<chrono::NaiveDateTime>>(5)
                .ok()
                .flatten()
                .map(common::time::format_iso),
        })
        .collect();
    Ok((items, total))
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
    let inserted = state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"INSERT INTO admin_user (name, email, password_hash, role_id, status, version, created_at, updated_at)
               VALUES (?, ?, ?, ?, 1, 0, NOW(), NOW())"#,
            [name.into(), email.clone().into(), hash.into(), role_id.into()],
        ))
        .await?;
    fetch_admin(state, inserted.last_insert_id() as i64)
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
    state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"UPDATE admin_user SET
                 name = COALESCE(?, name),
                 role_id = COALESCE(?, role_id),
                 updated_at = NOW(), version = version + 1
               WHERE id = ?"#,
            [
                name.map(|s| s.to_string()).into(),
                role_id.into(),
                admin_id.into(),
            ],
        ))
        .await?;
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
    txn.execute(Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        r#"DELETE FROM admin_user WHERE id = ?"#,
        [admin_id.into()],
    ))
    .await?;
    txn.execute(Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        r#"DELETE FROM admin_session WHERE admin_id = ?"#,
        [admin_id.into()],
    ))
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
    state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"UPDATE admin_user SET status = ?, updated_at = NOW(), version = version + 1 WHERE id = ?"#,
            [status.into(), admin_id.into()],
        ))
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
    state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"UPDATE admin_user SET password_hash = ?, updated_at = NOW(), version = version + 1 WHERE id = ?"#,
            [hash.into(), admin_id.into()],
        ))
        .await?;
    revoke_all_admin_sessions(state, admin_id).await;
    Ok(())
}

async fn revoke_all_admin_sessions(state: &SharedState, admin_id: i64) {
    let Ok(tokens) = state
        .db
        .query_all(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT token_id FROM admin_session WHERE admin_id = ? AND status = 1"#,
            [admin_id.into()],
        ))
        .await
    else {
        return;
    };
    state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"UPDATE admin_session SET status = 2, updated_at = NOW() WHERE admin_id = ? AND status = 1"#,
            [admin_id.into()],
        ))
        .await
        .ok();
    for t in tokens {
        if let Ok(jti) = t.try_get_by_index::<String>(0) {
            crate::service::session::revoke_admin(state, &jti).await;
        }
    }
}

async fn admin_email_exists(state: &SharedState, email: &str) -> Result<bool, SvcError> {
    let row = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT 1 FROM admin_user WHERE email = ?"#,
            [email.into()],
        ))
        .await?;
    Ok(row.is_some())
}

async fn role_exists(state: &SharedState, role_id: i64) -> Result<bool, SvcError> {
    let row = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT 1 FROM role WHERE id = ?"#,
            [role_id.into()],
        ))
        .await?;
    Ok(row.is_some())
}

pub async fn is_super_admin(state: &SharedState, admin_id: i64) -> Result<bool, SvcError> {
    let row = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT r.is_locked FROM admin_user a JOIN role r ON r.id = a.role_id WHERE a.id = ?"#,
            [admin_id.into()],
        ))
        .await?;
    Ok(row
        .and_then(|r| r.try_get_by_index::<i8>(0).ok())
        .map(|v| v != 0)
        .unwrap_or(false))
}

async fn is_super_role_id(state: &SharedState, role_id: i64) -> Result<bool, SvcError> {
    let row = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT is_locked FROM role WHERE id = ?"#,
            [role_id.into()],
        ))
        .await?;
    Ok(row
        .and_then(|r| r.try_get_by_index::<i8>(0).ok())
        .map(|v| v != 0)
        .unwrap_or(false))
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
    let rows = state
        .db
        .query_all(Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT r.id, r.name, r.type, r.is_locked,
                      (SELECT COUNT(*) FROM admin_user a WHERE a.role_id = r.id) AS member_count
               FROM role r ORDER BY r.id"#,
        ))
        .await?;
    let mut list = Vec::with_capacity(rows.len());
    for r in rows {
        let role_id = r.try_get_by_index::<u64>(0).unwrap_or(0) as i64;
        let keys = role_permission_keys(state, role_id).await?;
        list.push(RoleRow {
            id: role_id,
            name: r.try_get_by_index::<String>(1).unwrap_or_default(),
            r#type: r.try_get_by_index::<i8>(2).unwrap_or(2) as i32,
            is_locked: r.try_get_by_index::<i8>(3).unwrap_or(0) != 0,
            member_count: r.try_get_by_index::<i64>(4).unwrap_or(0),
            permission_keys: keys,
        });
    }
    Ok(list)
}

async fn role_permission_keys(state: &SharedState, role_id: i64) -> Result<Vec<String>, SvcError> {
    let rows = state
        .db
        .query_all(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT p.perm_code FROM role_permission rp JOIN permission p ON p.id = rp.permission_id
               WHERE rp.role_id = ? ORDER BY p.id"#,
            [role_id.into()],
        ))
        .await?;
    Ok(rows
        .iter()
        .filter_map(|r| r.try_get_by_index::<String>(0).ok())
        .collect())
}

pub async fn create_role(state: &SharedState, name: &str) -> Result<RoleRow, SvcError> {
    if role_name_exists(state, name).await? {
        let details = serde_json::json!({"name": "role name already exists"});
        return Err(SvcError::code_with(40000, details));
    }
    let inserted = state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"INSERT INTO role (name, type, is_locked, version, created_at, updated_at)
               VALUES (?, 2, 0, 0, NOW(), NOW())"#,
            [name.into()],
        ))
        .await?;
    let role_id = inserted.last_insert_id() as i64;
    Ok(RoleRow {
        id: role_id,
        name: name.to_string(),
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
        state
            .db
            .execute(Statement::from_sql_and_values(
                sea_orm::DatabaseBackend::MySql,
                r#"UPDATE role SET name = ?, updated_at = NOW(), version = version + 1 WHERE id = ?"#,
                [n.into(), role_id.into()],
            ))
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
        let txn = state.db.begin().await?;
        txn.execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"DELETE FROM role_permission WHERE role_id = ?"#,
            [role_id.into()],
        ))
        .await?;
        for k in keys {
            txn.execute(Statement::from_sql_and_values(
                sea_orm::DatabaseBackend::MySql,
                r#"INSERT INTO role_permission (role_id, permission_id, created_at, updated_at)
                   SELECT ?, id, NOW(), NOW() FROM permission WHERE perm_code = ?"#,
                [role_id.into(), k.into()],
            ))
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
    let row = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT COUNT(*) FROM admin_user WHERE role_id = ?"#,
            [role_id.into()],
        ))
        .await?;
    let count = row
        .and_then(|r| r.try_get_by_index::<i64>(0).ok())
        .unwrap_or(0);
    if count > 0 {
        return Err(SvcError::code(40904));
    }
    let txn = state.db.begin().await?;
    txn.execute(Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        r#"DELETE FROM role_permission WHERE role_id = ?"#,
        [role_id.into()],
    ))
    .await?;
    txn.execute(Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        r#"DELETE FROM role WHERE id = ?"#,
        [role_id.into()],
    ))
    .await?;
    txn.commit().await?;
    Ok(())
}

async fn role_name_exists(state: &SharedState, name: &str) -> Result<bool, SvcError> {
    let row = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT 1 FROM role WHERE name = ?"#,
            [name.into()],
        ))
        .await?;
    Ok(row.is_some())
}

async fn perm_code_exists(state: &SharedState, code: &str) -> Result<bool, SvcError> {
    let row = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT 1 FROM permission WHERE perm_code = ?"#,
            [code.into()],
        ))
        .await?;
    Ok(row.is_some())
}

pub struct PermissionRow {
    pub perm_code: String,
    pub group: String,
    pub label: String,
}

pub async fn list_permissions(state: &SharedState) -> Result<Vec<PermissionRow>, SvcError> {
    let rows = state
        .db
        .query_all(Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT perm_code, `group`, label FROM permission ORDER BY id"#,
        ))
        .await?;
    Ok(rows
        .iter()
        .map(|r| PermissionRow {
            perm_code: r.try_get_by_index::<String>(0).unwrap_or_default(),
            group: r.try_get_by_index::<String>(1).unwrap_or_default(),
            label: r.try_get_by_index::<String>(2).unwrap_or_default(),
        })
        .collect())
}

// ══════════ 用户运营(FLOW-12) ══════════

pub async fn toggle_user_status(
    state: &SharedState,
    user_id: i64,
    status: i32,
) -> Result<crate::entity::user::Model, SvcError> {
    use sea_orm::EntityTrait;
    state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"UPDATE user SET status = ?, updated_at = NOW(), version = version + 1 WHERE id = ?"#,
            [status.into(), user_id.into()],
        ))
        .await?;
    if status == UserStatus::Disabled.code() {
        session::revoke_all_for_user(state, user_id).await;
    }
    user_query::invalidate_user(state, user_id).await;
    crate::entity::user::Entity::find_by_id(user_id as u64)
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
        let row = state
            .db
            .query_one(Statement::from_sql_and_values(
                sea_orm::DatabaseBackend::MySql,
                r#"SELECT token_id, user_id, status FROM user_session WHERE id = ?"#,
                [sid.into()],
            ))
            .await?;
        let Some(row) = row else {
            return Err(SvcError::code(40400));
        };
        let uid = row
            .try_get_by_index::<u64>(1)
            .map_err(|_| SvcError::code(50000))?;
        if uid as i64 != user_id {
            return Err(SvcError::code(40400));
        }
        let token = row
            .try_get_by_index::<String>(0)
            .map_err(|_| SvcError::code(50000))?;
        session::revoke_store(state, &token, user_id).await;
    } else {
        session::revoke_all_for_user(state, user_id).await;
    }
    Ok(())
}

/// 用户详情(FLOW-12 getUserDetail:NP-001 防 N+1)
pub struct UserDetailData {
    pub user: crate::entity::user::Model,
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
    use sea_orm::EntityTrait;
    let user = crate::entity::user::Entity::find_by_id(user_id as u64)
        .one(&state.db)
        .await?
        .ok_or(SvcError::code(40400))?;
    let identities = crate::service::account::list_identities(state, user_id).await?;

    let rows = state
        .db
        .query_all(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT id, device, browser, ip, location, method, status, last_active_at
               FROM user_session WHERE user_id = ? ORDER BY id DESC"#,
            [user_id.into()],
        ))
        .await?;
    let sessions = rows
        .iter()
        .map(|r| SessionView {
            id: r.try_get_by_index::<u64>(0).unwrap_or(0) as i64,
            device: r.try_get_by_index::<Option<String>>(1).ok().flatten(),
            browser: r.try_get_by_index::<Option<String>>(2).ok().flatten(),
            ip: r.try_get_by_index::<Option<String>>(3).ok().flatten(),
            location: r.try_get_by_index::<Option<String>>(4).ok().flatten(),
            method: r.try_get_by_index::<i32>(5).unwrap_or(0),
            status: r.try_get_by_index::<i8>(6).unwrap_or(1) as i32,
            last_active_at: r
                .try_get_by_index::<Option<chrono::NaiveDateTime>>(7)
                .ok()
                .flatten()
                .map(common::time::format_iso),
        })
        .collect();

    let rows = state
        .db
        .query_all(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT id, method, ip, device, result, created_at FROM login_history
               WHERE user_id = ? ORDER BY created_at DESC LIMIT ?"#,
            [user_id.into(), (history_limit as i64).into()],
        ))
        .await?;
    let login_history = rows
        .iter()
        .map(|r| LoginHistoryView {
            id: r.try_get_by_index::<u64>(0).unwrap_or(0) as i64,
            method: r.try_get_by_index::<i32>(1).unwrap_or(0),
            ip: r.try_get_by_index::<Option<String>>(2).ok().flatten(),
            device: r.try_get_by_index::<Option<String>>(3).ok().flatten(),
            result: r.try_get_by_index::<i8>(4).unwrap_or(1) as i32,
            created_at: r
                .try_get_by_index::<chrono::NaiveDateTime>(5)
                .map(common::time::format_iso)
                .unwrap_or_default(),
        })
        .collect();

    Ok(UserDetailData {
        user,
        identities,
        sessions,
        login_history,
    })
}

// ══════════ 审计写入(主库 dreamy_server,只增不删) ══════════

/// admin 操作审计写入(失败 ERROR 不阻塞;REST 调用点与 gRPC AuditGate 共用)。
/// operator_id:None = 系统操作(Java MergeService 语义)。
pub async fn audit(
    state: &SharedState,
    operator_id: Option<i64>,
    operator_name: &str,
    action: &str,
    target: &str,
    ip: &str,
    user_agent: Option<&str>,
) {
    let result = state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"INSERT INTO operation_log (operator_id, operator_name, action, target, ip, user_agent, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())"#,
            [
                operator_id.into(),
                operator_name.into(),
                action.into(),
                target.into(),
                ip.into(),
                user_agent.into(),
            ],
        ))
        .await;
    if let Err(e) = result {
        // 对齐 Java AuditAspect 吞异常语义 + 用户指令不静默:ERROR 日志 + 可见计数
        tracing::error!(error = %e, action, "[audit] 审计写入失败(不阻塞主流程)");
    }
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

fn build_oplog_where(f: &OperationLogFilter) -> (String, Vec<sea_orm::sea_query::Value>) {
    let mut clauses = vec!["1=1".to_string()];
    let mut params: Vec<sea_orm::sea_query::Value> = vec![];
    if let Some(a) = f.action.as_deref() {
        if !a.is_empty() {
            clauses.push("action = ?".into());
            params.push(a.to_string().into());
        }
    }
    if let Some(oid) = f.operator_id {
        clauses.push("operator_id = ?".into());
        params.push(oid.into());
    }
    if let Some(v) = f.from.as_deref() {
        if !v.is_empty() {
            clauses.push("created_at >= ?".into());
            params.push(v.to_string().into());
        }
    }
    if let Some(v) = f.to.as_deref() {
        if !v.is_empty() {
            clauses.push("created_at <= ?".into());
            params.push(v.to_string().into());
        }
    }
    (clauses.join(" AND "), params)
}

fn row_data(r: &sea_orm::QueryResult) -> OperationLogRowData {
    OperationLogRowData {
        id: r.try_get_by_index::<u64>(0).unwrap_or(0),
        operator_name: r.try_get_by_index::<Option<String>>(1).ok().flatten(),
        action: r.try_get_by_index::<String>(2).unwrap_or_default(),
        target: r.try_get_by_index::<Option<String>>(3).ok().flatten(),
        ip: r.try_get_by_index::<Option<String>>(4).ok().flatten(),
        changes: r.try_get_by_index::<Option<String>>(5).ok().flatten(),
        created_at: r
            .try_get_by_index::<Option<chrono::NaiveDateTime>>(6)
            .ok()
            .flatten(),
    }
}

/// 分页查询(ORDER BY id DESC;返回 (rows, total))
pub async fn query_operation_logs(
    state: &SharedState,
    filter: &OperationLogFilter,
    page: u64,
    page_size: u64,
) -> Result<(Vec<OperationLogRowData>, i64), SvcError> {
    let (where_sql, params) = build_oplog_where(filter);
    let total_params = params.clone();
    let total: i64 = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            format!(r#"SELECT COUNT(*) FROM operation_log WHERE {where_sql}"#),
            total_params,
        ))
        .await?
        .and_then(|r| r.try_get_by_index::<i64>(0).ok())
        .unwrap_or(0);
    let offset = page.saturating_sub(1).saturating_mul(page_size);
    let mut page_params = params;
    page_params.push((page_size as i64).into());
    page_params.push((offset as i64).into());
    let rows = state
        .db
        .query_all(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            format!(
                r#"SELECT id, operator_name, action, target, ip, changes, created_at
                   FROM operation_log WHERE {where_sql} ORDER BY id DESC LIMIT ? OFFSET ?"#
            ),
            page_params,
        ))
        .await?;
    Ok((rows.iter().map(row_data).collect(), total))
}

/// 导出查询(ORDER BY id ASC;from/to 必传与 92 天窗口由调用方校验)
pub async fn stream_operation_logs(
    state: &SharedState,
    filter: &OperationLogFilter,
) -> Result<Vec<OperationLogRowData>, SvcError> {
    let (where_sql, params) = build_oplog_where(filter);
    let rows = state
        .db
        .query_all(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            format!(
                r#"SELECT id, operator_name, action, target, ip, changes, created_at
                   FROM operation_log WHERE {where_sql} ORDER BY id"#
            ),
            params,
        ))
        .await?;
    Ok(rows.iter().map(row_data).collect())
}
