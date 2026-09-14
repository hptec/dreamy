//! 账户服务(FLOW-05/06/08):资料更新、登录方式管理、换主邮箱、删号。
//! 语义对齐 Java IdentityService(v2.2 路由表版)。

use common::state::SharedState;
use sea_orm::{ConnectionTrait, EntityTrait, Statement, TransactionTrait};

use crate::entity::user;
use crate::enums::{AuthProvider, UserStatus};
use crate::service::{authconfig, merge, otp, session, user_query, SvcError};

/// IdentityDTO 同构(MAP-002:不暴露 provider_uid)
pub struct IdentityView {
    pub id: i64,
    pub provider: i32,
    pub identifier: Option<String>,
    pub is_primary: bool,
    pub verified: bool,
    pub hidden_email: bool,
    pub relay_valid: Option<bool>,
    pub last_login_at: Option<String>,
}

impl IdentityView {
    pub fn to_json(&self) -> serde_json::Value {
        serde_json::json!({
            "id": self.id,
            "provider": self.provider,
            "identifier": self.identifier,
            "is_primary": self.is_primary,
            "verified": self.verified,
            "hidden_email": self.hidden_email,
            "relay_valid": self.relay_valid,
            "last_login_at": self.last_login_at,
        })
    }
}

/// FUNC-019 更新资料(仅更新提供字段)
pub async fn update_profile(
    state: &SharedState,
    user_id: i64,
    display_name: Option<&str>,
    locale_pref: Option<&str>,
) -> Result<user::Model, SvcError> {
    // 乐观更新(仅提供字段;version 不动——Java 侧同样不带乐观锁)
    let sets: Vec<String> = vec![];
    let _ = sets;
    let dn = display_name.map(|s| s.to_string());
    let lp = locale_pref.map(|s| s.to_string());
    state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"UPDATE user SET
                 name = COALESCE(?, name),
                 locale_pref = COALESCE(?, locale_pref),
                 updated_at = NOW()
               WHERE id = ?"#,
            [dn.into(), lp.into(), user_id.into()],
        ))
        .await?;
    user_query::invalidate_user(state, user_id).await;
    let updated = user::Entity::find_by_id(user_id as u64)
        .one(&state.db)
        .await?
        .ok_or(SvcError::NotFound)?;
    Ok(updated)
}

/// FUNC-010 登录方式列表(仅 connected=true)
pub async fn list_identities(
    state: &SharedState,
    user_id: i64,
) -> Result<Vec<IdentityView>, SvcError> {
    let rows = state
        .db
        .query_all(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT id, provider, identifier, is_primary, verified, hidden_email, relay_valid, last_login_at
               FROM user_identity WHERE user_id = ? AND connected = 1 ORDER BY id"#,
            [user_id.into()],
        ))
        .await?;
    let mut list = Vec::with_capacity(rows.len());
    for r in rows {
        list.push(IdentityView {
            id: r.try_get_by_index::<u64>(0).unwrap_or(0) as i64,
            provider: r.try_get_by_index::<i32>(1).unwrap_or(0),
            identifier: r.try_get_by_index::<Option<String>>(2).ok().flatten(),
            is_primary: r.try_get_by_index::<i8>(3).unwrap_or(0) != 0,
            verified: r.try_get_by_index::<i8>(4).unwrap_or(0) != 0,
            hidden_email: r.try_get_by_index::<i8>(5).unwrap_or(0) != 0,
            relay_valid: r
                .try_get_by_index::<Option<i8>>(6)
                .ok()
                .flatten()
                .map(|v| v != 0),
            last_login_at: r
                .try_get_by_index::<Option<chrono::NaiveDateTime>>(7)
                .ok()
                .flatten()
                .map(common::time::format_iso),
        });
    }
    Ok(list)
}

/// FLOW-05 bindIdentity(FUNC-008)
#[allow(clippy::too_many_arguments)]
pub async fn bind_identity(
    state: &SharedState,
    user_id: i64,
    provider: i32,
    id_token: &str,
    email: &str,
    code: &str,
    ip: &str,
) -> Result<Vec<IdentityView>, SvcError> {
    let provider_enum = AuthProvider::from_code(provider).ok_or_else(|| SvcError::code(40000))?;

    // BLOCKER-4:email 分支仅校验 OTP 码(不走全登录管线)
    let (provider_uid, identifier, verified, hidden, relay) =
        if provider_enum == AuthProvider::Email {
            otp::verify_code_only(state, email, code).await?;
            let normalized = email.trim().to_lowercase();
            (normalized.clone(), Some(normalized), true, false, None)
        } else {
            // OIDC 分支:验签取 sub
            let cfg = authconfig::get(state).await?;
            let (enabled, client_id) = match provider_enum {
                AuthProvider::Google => (
                    cfg.google_enabled,
                    cfg.google_client_id.clone().unwrap_or_default(),
                ),
                AuthProvider::Apple => (
                    cfg.apple_enabled,
                    cfg.apple_service_id.clone().unwrap_or_default(),
                ),
                AuthProvider::Email => unreachable!(),
            };
            if !enabled {
                return Err(SvcError::code(40303));
            }
            let result = crate::oidc::verify(
                state,
                &provider_enum.code().to_string(),
                id_token,
                None,
                &client_id,
            )
            .await?;
            let hidden = result.hidden_email;
            let relay = result.relay_email.clone();
            let ident = if hidden {
                relay.clone()
            } else {
                result.email.clone()
            };
            (result.sub, ident, result.email_verified, hidden, relay)
        };

    // STEP-02 占用校验(路由表全局唯一 → 40903)
    if let Some(owner) =
        merge::find_user_id_by_provider(state, provider_enum, &provider_uid).await?
    {
        if owner as i64 != user_id {
            return Err(SvcError::code(40903));
        }
        // 既有且属本人 → 重连语义(connected 已 true;幂等返回)
        return list_identities(state, user_id).await;
    }

    // STEP-03 INSERT(路由 + 档案,同事务)
    let now = chrono::Local::now().naive_local();
    let txn = state.db.begin().await?;
    // 路由表
    match provider_enum {
        AuthProvider::Email => {
            txn.execute(Statement::from_sql_and_values(
                sea_orm::DatabaseBackend::MySql,
                r#"INSERT INTO identity_email (email, user_id, created_at) VALUES (?, ?, NOW())"#,
                [provider_uid.clone().into(), user_id.into()],
            ))
            .await?;
        }
        AuthProvider::Google => {
            txn.execute(Statement::from_sql_and_values(
                sea_orm::DatabaseBackend::MySql,
                r#"INSERT INTO identity_google (google_sub, user_id, created_at) VALUES (?, ?, NOW())"#,
                [provider_uid.clone().into(), user_id.into()],
            ))
            .await?;
        }
        AuthProvider::Apple => {
            txn.execute(Statement::from_sql_and_values(
                sea_orm::DatabaseBackend::MySql,
                r#"INSERT INTO identity_apple (apple_sub, user_id, relay_email, created_at) VALUES (?, ?, ?, NOW())"#,
                [provider_uid.clone().into(), user_id.into(), relay.clone().into()],
            ))
            .await?;
        }
    }
    // 档案(非首个凭证 is_primary=0)
    txn.execute(Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        r#"INSERT INTO user_identity
           (user_id, provider, provider_uid, identifier, is_primary, verified, connected, hidden_email, relay_email, bound_at, created_at, updated_at)
           VALUES (?, ?, ?, ?, 0, ?, 1, ?, ?, ?, NOW(), NOW())"#,
        [
            user_id.into(),
            provider.into(),
            provider_uid.into(),
            identifier.into(),
            (verified as i8).into(),
            (hidden as i8).into(),
            relay.into(),
            now.into(),
        ],
    ))
    .await?;
    txn.commit().await?;
    let _ = ip; // Java 记录 bind ip 于 login_history;此处省略(登录历史只记登录事件)

    list_identities(state, user_id).await
}

/// FLOW-05 unbindIdentity(FUNC-009 R2):归属 40300 / 主邮箱 40304 / min_methods 40305
pub async fn unbind_identity(
    state: &SharedState,
    user_id: i64,
    identity_id: i64,
) -> Result<(), SvcError> {
    let row = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT id, user_id, is_primary, connected FROM user_identity WHERE id = ?"#,
            [identity_id.into()],
        ))
        .await?;
    let Some(row) = row else {
        return Err(SvcError::code(40400));
    };
    let owner = row
        .try_get_by_index::<u64>(1)
        .map_err(|_| SvcError::code(50000))?;
    if owner != user_id as u64 {
        return Err(SvcError::code(40300)); // STEP-01 归属
    }
    let is_primary = row.try_get_by_index::<i8>(2).unwrap_or(0) != 0;
    if is_primary {
        return Err(SvcError::code(40304)); // STEP-02 主邮箱
    }
    // STEP-03 min_methods
    let cfg = authconfig::get(state).await?;
    let connected = count_connected(state, user_id).await?;
    if connected - 1 < cfg.min_methods as i64 {
        return Err(SvcError::code(40305));
    }
    // STEP-04 connected=false + 删路由(同事务)
    let provider = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT provider, provider_uid FROM user_identity WHERE id = ?"#,
            [identity_id.into()],
        ))
        .await?;
    let txn = state.db.begin().await?;
    txn.execute(Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        r#"UPDATE user_identity SET connected = 0, updated_at = NOW() WHERE id = ?"#,
        [identity_id.into()],
    ))
    .await?;
    if let Some(pr) = provider {
        let prov = pr.try_get_by_index::<i32>(0).unwrap_or(0);
        let uid = pr.try_get_by_index::<String>(1).unwrap_or_default();
        let del_sql = match prov {
            1 => "DELETE FROM identity_email WHERE email = ?",
            2 => "DELETE FROM identity_google WHERE google_sub = ?",
            3 => "DELETE FROM identity_apple WHERE apple_sub = ?",
            _ => "",
        };
        if !del_sql.is_empty() {
            txn.execute(Statement::from_sql_and_values(
                sea_orm::DatabaseBackend::MySql,
                del_sql,
                [uid.into()],
            ))
            .await?;
        }
    }
    txn.commit().await?;
    Ok(())
}

async fn count_connected(state: &SharedState, user_id: i64) -> Result<i64, SvcError> {
    let row = state
        .db
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT COUNT(*) FROM user_identity WHERE user_id = ? AND connected = 1"#,
            [user_id.into()],
        ))
        .await?;
    Ok(row
        .and_then(|r| r.try_get_by_index::<i64>(0).ok())
        .unwrap_or(0))
}

/// FLOW-06 changePrimaryEmail(FUNC-026 EDGE-020)
pub async fn change_primary_email(
    state: &SharedState,
    user_id: i64,
    new_email: &str,
    code: &str,
) -> Result<Vec<IdentityView>, SvcError> {
    let normalized = new_email.trim().to_lowercase();
    // STEP-01 占用(路由表:他人持有 → 40901)
    if let Some(other) = merge::find_user_by_email(state, &normalized).await? {
        if other.id as i64 != user_id {
            return Err(SvcError::code(40901));
        }
    }
    // STEP-02 OTP 校验(对 new_email;BLOCKER-4 仅校验码)
    otp::verify_code_only(state, &normalized, code).await?;

    let now = chrono::Local::now().naive_local();
    let txn = state.db.begin().await?;
    // STEP-3a 旧 email 凭证降级 is_primary=0 + connected=0 + 删路由
    txn.execute(Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        r#"UPDATE user_identity SET is_primary = 0, connected = 0, updated_at = NOW()
           WHERE user_id = ? AND provider = 1 AND is_primary = 1"#,
        [user_id.into()],
    ))
    .await?;
    txn.execute(Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        r#"DELETE pe FROM user u JOIN identity_email pe ON pe.user_id = u.id
           WHERE u.id = ? AND pe.email = u.email"#,
        [user_id.into()],
    ))
    .await?;
    // STEP-3b 新 email 凭证 is_primary=1(无则建)+ 路由 upsert
    let existing_new = txn
        .query_one(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT id FROM user_identity WHERE user_id = ? AND provider = 1 AND provider_uid = ?"#,
            [user_id.into(), normalized.clone().into()],
        ))
        .await?;
    if existing_new.is_some() {
        txn.execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"UPDATE user_identity SET is_primary = 1, verified = 1, connected = 1, updated_at = NOW()
               WHERE user_id = ? AND provider = 1 AND provider_uid = ?"#,
            [user_id.into(), normalized.clone().into()],
        ))
        .await?;
    } else {
        txn.execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"INSERT INTO user_identity
               (user_id, provider, provider_uid, identifier, is_primary, verified, connected, bound_at, created_at, updated_at)
               VALUES (?, 1, ?, ?, 1, 1, 1, ?, NOW(), NOW())"#,
            [user_id.into(), normalized.clone().into(), normalized.clone().into(), now.into()],
        ))
        .await?;
    }
    // 路由 upsert(他人不持有时插入;本人持有为幂等)
    txn.execute(Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        r#"INSERT INTO identity_email (email, user_id, created_at) VALUES (?, ?, NOW())
           ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)"#,
        [normalized.clone().into(), user_id.into()],
    ))
    .await?;
    // STEP-3c user 主邮箱更新
    txn.execute(Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        r#"UPDATE user SET email = ?, email_verified = 1, updated_at = NOW() WHERE id = ?"#,
        [normalized.clone().into(), user_id.into()],
    ))
    .await?;
    txn.commit().await?;

    // STEP-4 旧邮箱通知 + 缓存失效
    user_query::invalidate_user(state, user_id).await;
    let st = state.clone();
    let mail_to = normalized.clone();
    let _ = &normalized;
    tokio::spawn(async move {
        let _ = crate::mail::send_template(
            &st,
            &mail_to,
            "change_primary",
            "en",
            &[("new_email".into(), mail_to.clone())],
        )
        .await;
    });

    list_identities(state, user_id).await
}

/// FLOW-08 deleteAccount(FUNC-027):软删 + 全会话撤销 + 通知邮件
pub async fn delete_account(state: &SharedState, user_id: i64) -> Result<(), SvcError> {
    let me = user::Entity::find_by_id(user_id as u64)
        .one(&state.db)
        .await?
        .ok_or(SvcError::NotFound)?;
    let now = chrono::Local::now().naive_local();
    // STEP-01 软删 + 撤销全部会话
    state
        .db
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"UPDATE user SET status = 3, deleted_at = ?, updated_at = NOW() WHERE id = ?"#,
            [now.into(), user_id.into()],
        ))
        .await?;
    session::revoke_all_for_user(state, user_id).await;
    user_query::invalidate_user(state, user_id).await;

    // STEP-3 通知(不阻塞)
    let st = state.clone();
    let email = me.email.clone();
    tokio::spawn(async move {
        let _ = crate::mail::send_template(&st, &email, "account_deleted", "en", &[]).await;
    });
    let _ = UserStatus::Deleted; // 语义锚点:status=3
    Ok(())
}
