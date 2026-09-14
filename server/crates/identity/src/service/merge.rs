//! 账户归并(v2.2 路由表版,语义对齐 Java MergeService)。
//!
//! 归并三规则(登录时按序判定):
//! 1. (provider, provider_uid) 路由表命中 → 幂等返回既有主账号
//! 2. 同邮箱且双方 email_verified → 自动归并(新凭证挂既有主账号,写审计)
//! 3. 同邮箱但任一方未验证 → 40902(绝不静默合并,防劫持)
//! 4. 都未命中 → 新建主账号(user + 路由 + 档案三写同事务)
//!
//! Apple 隐藏邮箱:identifier 用 relay email,不参与邮箱归并。

use common::state::SharedState;
use sea_orm::{ConnectionTrait, EntityTrait, Statement, TransactionTrait};

use crate::entity::user;
use crate::enums::{AuthProvider, UserStatus, UserTier};
use crate::service::SvcError;

pub struct MergeOutcome {
    pub user: user::Model,
    pub new_account: bool,
}

fn normalize(email: &str) -> String {
    email.trim().to_lowercase()
}

/// 路由表命中:凭证 → user_id(connected 语义内含:路由表只存活跃路由)
pub async fn find_user_id_by_provider(
    state: &SharedState,
    provider: AuthProvider,
    provider_uid: &str,
) -> Result<Option<u64>, SvcError> {
    use sea_orm::EntityTrait;
    let user_id = match provider {
        AuthProvider::Email => crate::entity::identity_email::Entity::find_by_id(provider_uid)
            .one(&state.db)
            .await?
            .map(|r| r.user_id),
        AuthProvider::Google => crate::entity::identity_google::Entity::find_by_id(provider_uid)
            .one(&state.db)
            .await?
            .map(|r| r.user_id),
        AuthProvider::Apple => crate::entity::identity_apple::Entity::find_by_id(provider_uid)
            .one(&state.db)
            .await?
            .map(|r| r.user_id),
    };
    Ok(user_id)
}

/// 同邮箱既有主账号(路由表两跳;排除匿名化——其 email 已 NULL 天然排除)
pub async fn find_user_by_email(
    state: &SharedState,
    email: &str,
) -> Result<Option<user::Model>, SvcError> {
    use sea_orm::EntityTrait;
    let Ok(route) = crate::entity::identity_email::Entity::find_by_id(normalize(email))
        .one(&state.db)
        .await
    else {
        return Ok(None);
    };
    let Some(route) = route else { return Ok(None) };
    Ok(user::Entity::find_by_id(route.user_id)
        .one(&state.db)
        .await?)
}

/// FLOW 登录归并主入口(v2.2:路由表 + 主档 + 档案三写同事务)
#[allow(clippy::too_many_arguments)]
pub async fn resolve_or_merge(
    state: &SharedState,
    provider: AuthProvider,
    provider_uid: &str,
    email: Option<&str>,
    email_verified: bool,
    hidden_email: bool,
    relay_email: Option<&str>,
) -> Result<MergeOutcome, SvcError> {
    // STEP-01:路由表幂等命中
    if let Some(user_id) = find_user_id_by_provider(state, provider, provider_uid).await? {
        let existing = user::Entity::find_by_id(user_id)
            .one(&state.db)
            .await?
            .ok_or(SvcError::NotFound)?;
        return Ok(MergeOutcome {
            user: existing,
            new_account: false,
        });
    }

    let normalized_email = email.map(normalize);
    // Apple 隐藏邮箱:identifier=relay,不参与邮箱归并
    let merge_email = if hidden_email {
        None
    } else {
        normalized_email.clone()
    };

    // STEP-02/03:同邮箱归并判定(仅当提供方带回邮箱且已验证才可自动并)
    if let Some(ref mail) = merge_email {
        if let Some(existing) = find_user_by_email(state, mail).await? {
            if email_verified && existing.email_verified != 0 {
                // 自动归并:路由 + 档案挂既有主账号(同事务)
                attach_identity(
                    state,
                    &existing,
                    provider,
                    provider_uid,
                    hidden_email,
                    relay_email,
                    true,
                )
                .await?;
                return Ok(MergeOutcome {
                    user: existing,
                    new_account: false,
                });
            }
            // 任一方未验证 → 40902
            return Err(SvcError::code(40902));
        }
    }

    // STEP-04:新建主账号(user + 路由 + 档案)
    let now = chrono::Local::now().naive_local();
    let email_for_user = merge_email.clone().unwrap_or_else(|| {
        relay_email
            .map(|r| r.to_string())
            .unwrap_or_else(|| format!("hidden-{}@relay.dreamy", provider_uid))
    });
    let txn = state.db.begin().await?;
    let insert = txn
        .execute(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"INSERT INTO user (email, email_verified, tier, status, anonymized, version, joined_at, created_at, updated_at)
               VALUES (?, ?, 1, 1, 0, 0, ?, NOW(), NOW())"#,
            [
                email_for_user.into(),
                (email_verified as i8).into(),
                now.into(),
            ],
        ))
        .await?;
    let user_id = insert.last_insert_id() as u64;
    // 路由表写入(凭证键 → user_id)
    insert_route(&txn, provider, provider_uid, user_id, relay_email).await?;
    // 档案写入(uk(user_id, provider);is_primary=首个凭证为 true)
    txn.execute(Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        r#"INSERT INTO user_identity
           (user_id, provider, provider_uid, identifier, is_primary, verified, connected, hidden_email, relay_email, bound_at, created_at, updated_at)
           VALUES (?, ?, ?, ?, 1, ?, 1, ?, ?, ?, NOW(), NOW())"#,
        [
            (user_id as i64).into(),
            provider.code().into(),
            provider_uid.into(),
            merge_email.clone().or(relay_email.map(|r| r.to_string())).into(),
            (email_verified as i8).into(),
            (hidden_email as i8).into(),
            relay_email.into(),
            now.into(),
        ],
    ))
    .await?;
    // 邮箱路由(新建且邮箱已验证时,email 路由同时建立——邮箱登录域)
    if let Some(ref mail) = merge_email {
        if provider != AuthProvider::Email && email_verified {
            txn.execute(Statement::from_sql_and_values(
                sea_orm::DatabaseBackend::MySql,
                r#"INSERT INTO identity_email (email, user_id, created_at) VALUES (?, ?, NOW())"#,
                [(mail.as_str()).into(), (user_id as i64).into()],
            ))
            .await?;
        }
    }
    txn.commit().await?;
    let created = user::Entity::find_by_id(user_id)
        .one(&state.db)
        .await?
        .ok_or(SvcError::NotFound)?;
    Ok(MergeOutcome {
        user: created,
        new_account: true,
    })
}

/// 既有主账号挂新凭证(路由 + 档案,同事务)
async fn attach_identity(
    state: &SharedState,
    existing: &user::Model,
    provider: AuthProvider,
    provider_uid: &str,
    hidden_email: bool,
    relay_email: Option<&str>,
    verified: bool,
) -> Result<(), SvcError> {
    let now = chrono::Local::now().naive_local();
    let txn = state.db.begin().await?;
    insert_route(&txn, provider, provider_uid, existing.id, relay_email).await?;
    let identifier = if hidden_email {
        relay_email.map(|r| r.to_string())
    } else {
        Some(existing.email.clone())
    };
    txn.execute(Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        r#"INSERT INTO user_identity
           (user_id, provider, provider_uid, identifier, is_primary, verified, connected, hidden_email, relay_email, bound_at, created_at, updated_at)
           VALUES (?, ?, ?, ?, 0, ?, 1, ?, ?, ?, NOW(), NOW())"#,
        [
            (existing.id as i64).into(),
            provider.code().into(),
            provider_uid.into(),
            identifier.into(),
            (verified as i8).into(),
            (hidden_email as i8).into(),
            relay_email.into(),
            now.into(),
        ],
    ))
    .await?;
    txn.commit().await?;
    Ok(())
}

async fn insert_route(
    txn: &sea_orm::DatabaseTransaction,
    provider: AuthProvider,
    provider_uid: &str,
    user_id: u64,
    relay_email: Option<&str>,
) -> Result<(), sea_orm::DbErr> {
    let (sql, params): (&str, Vec<sea_orm::sea_query::Value>) = match provider {
        AuthProvider::Email => (
            r#"INSERT INTO identity_email (email, user_id, created_at) VALUES (?, ?, NOW())"#,
            vec![provider_uid.into(), (user_id as i64).into()],
        ),
        AuthProvider::Google => (
            r#"INSERT INTO identity_google (google_sub, user_id, created_at) VALUES (?, ?, NOW())"#,
            vec![provider_uid.into(), (user_id as i64).into()],
        ),
        AuthProvider::Apple => (
            r#"INSERT INTO identity_apple (apple_sub, user_id, relay_email, created_at) VALUES (?, ?, ?, NOW())"#,
            vec![
                provider_uid.into(),
                (user_id as i64).into(),
                relay_email.into(),
            ],
        ),
    };
    txn.execute(Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        sql,
        params,
    ))
    .await?;
    Ok(())
}

/// 登录禁用拒签检查(40301:DISABLED/DELETED/ANONYMIZED)
pub fn ensure_login_allowed(u: &user::Model) -> Result<(), SvcError> {
    match UserStatus::from_code(u.status as i32) {
        Some(UserStatus::Active) => Ok(()),
        _ => Err(SvcError::code(40301)),
    }
}

/// 新账号默认等级(P2 保守:REGULAR;VIP 由 admin 侧调整)
pub fn default_tier() -> i32 {
    UserTier::Regular.code()
}
