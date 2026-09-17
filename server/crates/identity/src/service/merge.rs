//! 账户归并(v2.2 路由表版,语义对齐 Java MergeService)。
//!
//! 归并三规则(登录时按序判定):
//! 1. (provider, provider_uid) 路由表命中 → 幂等返回既有主账号
//! 2. 同邮箱且双方 email_verified → 自动归并(新凭证挂既有主账号,写审计)
//! 3. 同邮箱但任一方未验证 → 40902(绝不静默合并,防劫持)
//! 4. 都未命中 → 新建主账号(user + 路由 + 档案三写同事务)
//!
//! Apple 隐藏邮箱:identifier 用 relay email,不参与邮箱归并。

use common::partition;
use common::state::SharedState;
use sea_orm::{ActiveModelTrait, EntityTrait, Set, TransactionTrait};

use crate::entity::user;
use crate::entity::user_identity;
use crate::enums::{AuthProvider, UserStatus, UserTier};
use crate::service::{account, ensure_user_segments, SvcError};

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
    use sea_orm::EntityTrait as _;
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
    use sea_orm::EntityTrait as _;
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
    let created = user::ActiveModel {
        email: Set(email_for_user),
        email_verified: Set(email_verified as i8),
        tier: Set(UserTier::Regular.code() as i8),
        status: Set(UserStatus::Active.code() as i8),
        anonymized: Set(0),
        version: Set(0),
        joined_at: Set(Some(now)),
        ..Default::default()
    }
    .insert(&txn)
    .await?;
    let user_id = created.id;
    // 路由表写入(凭证键 → user_id)
    account::insert_route(
        &txn,
        provider,
        provider_uid,
        user_id,
        relay_email.map(|r| r.to_string()),
    )
    .await?;
    // 档案写入(uk(user_id, provider);is_primary=首个凭证为 true)
    user_identity::Entity::insert(user_identity::ActiveModel {
        user_id: Set(user_id),
        provider: Set(provider.code() as i8),
        provider_uid: Set(provider_uid.to_string()),
        identifier: Set(merge_email.clone().or(relay_email.map(|r| r.to_string()))),
        is_primary: Set(1),
        verified: Set(email_verified as i8),
        connected: Set(1),
        hidden_email: Set(hidden_email as i8),
        relay_email: Set(relay_email.map(|r| r.to_string())),
        bound_at: Set(Some(now)),
        ..Default::default()
    })
    .exec_without_returning(&txn)
    .await?;
    // 邮箱路由(新建且邮箱已验证时,email 路由同时建立——邮箱登录域)
    if let Some(ref mail) = merge_email {
        if provider != AuthProvider::Email && email_verified {
            crate::entity::identity_email::Entity::insert(
                crate::entity::identity_email::ActiveModel {
                    email: Set(mail.clone()),
                    user_id: Set(user_id),
                    ..Default::default()
                },
            )
            .exec_without_returning(&txn)
            .await?;
        }
    }
    txn.commit().await?;
    // v3 分区水位:user INSERT 成功后预扩下一段(id 事后才知;user_identity 同边界跟随)
    ensure_user_segments(state, user_id).await;
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
    // v3 分区拦截:既有账号 user_id 已知,事务前精确确保段(事务内 DDL 会隐式提交)
    let _ = partition::ensure_id_segments(state, "user_identity", &[existing.id]).await;
    let txn = state.db.begin().await?;
    account::insert_route(
        &txn,
        provider,
        provider_uid,
        existing.id,
        relay_email.map(|r| r.to_string()),
    )
    .await?;
    let identifier = if hidden_email {
        relay_email.map(|r| r.to_string())
    } else {
        Some(existing.email.clone())
    };
    user_identity::Entity::insert(user_identity::ActiveModel {
        user_id: Set(existing.id),
        provider: Set(provider.code() as i8),
        provider_uid: Set(provider_uid.to_string()),
        identifier: Set(identifier),
        is_primary: Set(0),
        verified: Set(verified as i8),
        connected: Set(1),
        hidden_email: Set(hidden_email as i8),
        relay_email: Set(relay_email.map(|r| r.to_string())),
        bound_at: Set(Some(now)),
        ..Default::default()
    })
    .exec_without_returning(&txn)
    .await?;
    txn.commit().await?;
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
