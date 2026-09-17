//! 账户服务(FLOW-05/06/08):资料更新、登录方式管理、换主邮箱、删号。
//! 语义对齐 Java IdentityService(v2.2 路由表版)。

use common::partition;
use common::state::SharedState;
use sea_orm::sea_query::Expr;
use sea_orm::{
    ActiveModelTrait, ColumnTrait, EntityTrait, PaginatorTrait, QueryFilter, QueryOrder, Set,
    TransactionTrait,
};

use crate::entity::{identity_apple, identity_email, identity_google, user, user_identity};
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
    let mut am = user::ActiveModel {
        id: Set(user_id as u64),
        ..Default::default()
    };
    if let Some(name) = display_name {
        am.name = Set(Some(name.to_string()));
    }
    if let Some(locale) = locale_pref {
        am.locale_pref = Set(Some(locale.to_string()));
    }
    am.updated_at = Set(Some(chrono::Local::now().naive_local()));
    let updated = am.update(&state.db).await?;
    user_query::invalidate_user(state, user_id).await;
    Ok(updated)
}

/// FUNC-010 登录方式列表(仅 connected=true)
pub async fn list_identities(
    state: &SharedState,
    user_id: i64,
) -> Result<Vec<IdentityView>, SvcError> {
    let rows = user_identity::Entity::find()
        .filter(user_identity::Column::UserId.eq(user_id as u64))
        .filter(user_identity::Column::Connected.eq(1i8))
        .order_by_asc(user_identity::Column::Id)
        .all(&state.db)
        .await?;
    Ok(rows
        .into_iter()
        .map(|r| IdentityView {
            id: r.id as i64,
            provider: r.provider as i32,
            identifier: r.identifier,
            is_primary: r.is_primary != 0,
            verified: r.verified != 0,
            hidden_email: r.hidden_email != 0,
            relay_valid: r.relay_valid.map(|v| v != 0),
            last_login_at: r.last_login_at.map(common::time::format_iso),
        })
        .collect())
}

/// 路由表写入(凭证键 → user_id;按提供方分表)
pub(crate) async fn insert_route<C>(
    txn: &C,
    provider: AuthProvider,
    provider_uid: &str,
    user_id: u64,
    relay_email: Option<String>,
) -> Result<(), sea_orm::DbErr>
where
    C: sea_orm::ConnectionTrait,
{
    match provider {
        AuthProvider::Email => {
            identity_email::Entity::insert(identity_email::ActiveModel {
                email: Set(provider_uid.to_string()),
                user_id: Set(user_id),
                ..Default::default()
            })
            .exec_without_returning(txn)
            .await?;
        }
        AuthProvider::Google => {
            identity_google::Entity::insert(identity_google::ActiveModel {
                google_sub: Set(provider_uid.to_string()),
                user_id: Set(user_id),
                ..Default::default()
            })
            .exec_without_returning(txn)
            .await?;
        }
        AuthProvider::Apple => {
            identity_apple::Entity::insert(identity_apple::ActiveModel {
                apple_sub: Set(provider_uid.to_string()),
                user_id: Set(user_id),
                relay_email: Set(relay_email),
                ..Default::default()
            })
            .exec_without_returning(txn)
            .await?;
        }
    }
    Ok(())
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
    // v3 分区拦截:user_id 已知,事务前精确确保 user_identity 段(事务内 DDL 会隐式提交)
    let _ = partition::ensure_id_segments(state, "user_identity", &[user_id as u64]).await;
    let txn = state.db.begin().await?;
    // 路由表
    insert_route(&txn, provider_enum, &provider_uid, user_id as u64, relay.clone()).await?;
    // 档案(非首个凭证 is_primary=0;复合主键+自增 → exec_without_returning)
    user_identity::Entity::insert(user_identity::ActiveModel {
        user_id: Set(user_id as u64),
        provider: Set(provider as i8),
        provider_uid: Set(provider_uid),
        identifier: Set(identifier),
        is_primary: Set(0),
        verified: Set(verified as i8),
        connected: Set(1),
        hidden_email: Set(hidden as i8),
        relay_email: Set(relay),
        bound_at: Set(Some(now)),
        ..Default::default()
    })
    .exec_without_returning(&txn)
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
    let row = user_identity::Entity::find()
        .filter(user_identity::Column::Id.eq(identity_id as u64))
        .one(&state.db)
        .await?
        .ok_or(SvcError::code(40400))?;
    if row.user_id as i64 != user_id {
        return Err(SvcError::code(40300)); // STEP-01 归属
    }
    if row.is_primary != 0 {
        return Err(SvcError::code(40304)); // STEP-02 主邮箱
    }
    // STEP-03 min_methods
    let cfg = authconfig::get(state).await?;
    let connected = count_connected(state, user_id).await?;
    if connected - 1 < cfg.min_methods as i64 {
        return Err(SvcError::code(40305));
    }
    // STEP-04 connected=false + 删路由(同事务)
    let now = chrono::Local::now().naive_local();
    let provider = AuthProvider::from_code(row.provider as i32);
    let provider_uid = row.provider_uid.clone();
    let txn = state.db.begin().await?;
    user_identity::Entity::update_many()
        .col_expr(user_identity::Column::Connected, Expr::value(0i8))
        .col_expr(user_identity::Column::UpdatedAt, Expr::value(now))
        .filter(user_identity::Column::Id.eq(identity_id as u64))
        .exec(&txn)
        .await?;
    match provider {
        Some(AuthProvider::Email) => {
            identity_email::Entity::delete_by_id(provider_uid)
                .exec(&txn)
                .await?;
        }
        Some(AuthProvider::Google) => {
            identity_google::Entity::delete_by_id(provider_uid)
                .exec(&txn)
                .await?;
        }
        Some(AuthProvider::Apple) => {
            identity_apple::Entity::delete_by_id(provider_uid)
                .exec(&txn)
                .await?;
        }
        None => {}
    }
    txn.commit().await?;
    Ok(())
}

async fn count_connected(state: &SharedState, user_id: i64) -> Result<i64, SvcError> {
    let n = user_identity::Entity::find()
        .filter(user_identity::Column::UserId.eq(user_id as u64))
        .filter(user_identity::Column::Connected.eq(1i8))
        .count(&state.db)
        .await?;
    Ok(n as i64)
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
    // v3 分区拦截:user_id 已知,事务前精确确保 user_identity 段
    let _ = partition::ensure_id_segments(state, "user_identity", &[user_id as u64]).await;
    // 旧邮箱路由删除需要旧主邮箱(JOIN pe.email = u.email 的等价拆分)
    let me = user::Entity::find_by_id(user_id as u64)
        .one(&state.db)
        .await?
        .ok_or(SvcError::NotFound)?;
    let txn = state.db.begin().await?;
    // STEP-3a 旧 email 凭证降级 is_primary=0 + connected=0
    user_identity::Entity::update_many()
        .col_expr(user_identity::Column::IsPrimary, Expr::value(0i8))
        .col_expr(user_identity::Column::Connected, Expr::value(0i8))
        .col_expr(user_identity::Column::UpdatedAt, Expr::value(now))
        .filter(user_identity::Column::UserId.eq(user_id as u64))
        .filter(user_identity::Column::Provider.eq(AuthProvider::Email.code() as i8))
        .filter(user_identity::Column::IsPrimary.eq(1i8))
        .exec(&txn)
        .await?;
    // 删旧 email 路由(原 JOIN DELETE:pe.user_id = u.id AND pe.email = u.email)
    identity_email::Entity::delete_many()
        .filter(identity_email::Column::UserId.eq(user_id as u64))
        .filter(identity_email::Column::Email.eq(me.email.clone()))
        .exec(&txn)
        .await?;
    // STEP-3b 新 email 凭证 is_primary=1(无则建)+ 路由 upsert
    let existing_new = user_identity::Entity::find()
        .filter(user_identity::Column::UserId.eq(user_id as u64))
        .filter(user_identity::Column::Provider.eq(AuthProvider::Email.code() as i8))
        .filter(user_identity::Column::ProviderUid.eq(normalized.clone()))
        .one(&txn)
        .await?;
    if existing_new.is_some() {
        user_identity::Entity::update_many()
            .col_expr(user_identity::Column::IsPrimary, Expr::value(1i8))
            .col_expr(user_identity::Column::Verified, Expr::value(1i8))
            .col_expr(user_identity::Column::Connected, Expr::value(1i8))
            .col_expr(user_identity::Column::UpdatedAt, Expr::value(now))
            .filter(user_identity::Column::UserId.eq(user_id as u64))
            .filter(user_identity::Column::Provider.eq(AuthProvider::Email.code() as i8))
            .filter(user_identity::Column::ProviderUid.eq(normalized.clone()))
            .exec(&txn)
            .await?;
    } else {
        user_identity::Entity::insert(user_identity::ActiveModel {
            user_id: Set(user_id as u64),
            provider: Set(AuthProvider::Email.code() as i8),
            provider_uid: Set(normalized.clone()),
            identifier: Set(Some(normalized.clone())),
            is_primary: Set(1),
            verified: Set(1),
            connected: Set(1),
            bound_at: Set(Some(now)),
            ..Default::default()
        })
        .exec_without_returning(&txn)
        .await?;
    }
    // 路由 upsert(他人不持有时插入;本人持有为幂等)
    identity_email::Entity::insert(identity_email::ActiveModel {
        email: Set(normalized.clone()),
        user_id: Set(user_id as u64),
        ..Default::default()
    })
    .on_conflict(
        sea_orm::sea_query::OnConflict::new()
            .update_column(identity_email::Column::UserId)
            .to_owned(),
    )
    .exec_without_returning(&txn)
    .await?;
    // STEP-3c user 主邮箱更新
    user::Entity::update_many()
        .col_expr(user::Column::Email, Expr::value(normalized.clone()))
        .col_expr(user::Column::EmailVerified, Expr::value(1i8))
        .col_expr(user::Column::UpdatedAt, Expr::value(now))
        .filter(user::Column::Id.eq(user_id as u64))
        .exec(&txn)
        .await?;
    txn.commit().await?;

    // STEP-4 旧邮箱通知 + 缓存失效
    user_query::invalidate_user(state, user_id).await;
    let st = state.clone();
    let mail_to = normalized.clone();
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
    user::Entity::update_many()
        .col_expr(
            user::Column::Status,
            Expr::value(UserStatus::Deleted.code() as i8),
        )
        .col_expr(user::Column::DeletedAt, Expr::value(now))
        .col_expr(user::Column::UpdatedAt, Expr::value(now))
        .filter(user::Column::Id.eq(user_id as u64))
        .exec(&state.db)
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
