//! 会话有效性校验(热路径,Java 两个 JwtFilter 每请求经 gRPC 调用)。
//!
//! Redis-first(用户性能指令):
//! - 命中 `store:session:valid:{jti}`(Java 既有键格式,TTL 30s)→ 零 DB 返回
//! - 未命中 → DB 校验(语义与 Java SessionValidator 一字不差:status=ACTIVE,不查过期列——时间效力由 JWT exp 把关)
//! - 校验通过回填 Redis;撤销路径(P2/P3 登出/强制下线/禁用)DEL 键保证即时失效
//! - Redis 任何故障降级 DB,只 WARN 不失败

use common::state::SharedState;
use redis::AsyncCommands;
use sea_orm::{ColumnTrait, EntityTrait, PaginatorTrait, QueryFilter};

use crate::entity::{admin_session, admin_user, user_session};
use crate::enums::{AdminStatus, SessionStatus};
use crate::service::SvcError;

/// store 会话键(与 Java SessionValidityCache 既有格式一致,滚动兼容)
pub fn store_key(token_id: &str) -> String {
    format!("store:session:valid:{token_id}")
}

/// admin 会话键(与 Java AdminSessionValidityCache 一致)
pub fn admin_key(token_id: &str) -> String {
    format!("admin:session:valid:{token_id}")
}

/// 撤销时主动失效(P2/P3 登出/强制下线/禁用调用;DEL 即时生效,无等待窗口)
pub async fn revoke_store(state: &SharedState, token_id: &str) {
    if let Some(mut conn) = state.redis.clone() {
        if let Err(e) = conn.del::<_, ()>(store_key(token_id)).await {
            tracing::warn!(error = %e, "[session] 撤销 DEL 失败,等待 TTL(≤30s)自然失效");
        }
    }
}

/// admin 会话撤销失效(同上)
pub async fn revoke_admin(state: &SharedState, token_id: &str) {
    if let Some(mut conn) = state.redis.clone() {
        if let Err(e) = conn.del::<_, ()>(admin_key(token_id)).await {
            tracing::warn!(error = %e, "[session] 撤销 DEL 失败,等待 TTL(≤30s)自然失效");
        }
    }
}

/// store 会话有效性(热路径)
pub async fn validate_store(state: &SharedState, token_id: &str) -> Result<bool, SvcError> {
    if token_id.trim().is_empty() {
        return Err(SvcError::InvalidArg("token_id 为空".into()));
    }
    let key = store_key(token_id);
    if let Some(mut conn) = state.redis.clone() {
        match conn.get::<_, Option<String>>(&key).await {
            Ok(Some(v)) if v == "1" => return Ok(true),
            Ok(_) => {}
            Err(e) => tracing::warn!(error = %e, "[session] Redis 读失败,降级 DB"),
        }
    }
    let valid = user_session::Entity::find()
        .filter(user_session::Column::TokenId.eq(token_id))
        .filter(user_session::Column::Status.eq(SessionStatus::Active.code() as i8))
        .count(&state.db)
        .await?
        > 0;
    if valid {
        backfill(&key, state).await;
    }
    Ok(valid)
}

/// admin 会话有效性(含管理员状态复核,对齐 Java isAdminSessionValid 双查语义)
#[derive(Debug, Clone, Copy)]
pub struct AdminValidity {
    pub valid: bool,
    pub admin_active: bool,
}

pub async fn validate_admin(
    state: &SharedState,
    token_id: &str,
) -> Result<AdminValidity, SvcError> {
    if token_id.trim().is_empty() {
        return Err(SvcError::InvalidArg("token_id 为空".into()));
    }
    let key = admin_key(token_id);
    if let Some(mut conn) = state.redis.clone() {
        match conn.get::<_, Option<String>>(&key).await {
            Ok(Some(v)) if v == "1" => {
                return Ok(AdminValidity {
                    valid: true,
                    admin_active: true,
                })
            }
            Ok(_) => {}
            Err(e) => tracing::warn!(error = %e, "[session] Redis 读失败,降级 DB"),
        }
    }
    let session = admin_session::Entity::find()
        .filter(admin_session::Column::TokenId.eq(token_id))
        .filter(admin_session::Column::Status.eq(SessionStatus::Active.code() as i8))
        .one(&state.db)
        .await?;
    let Some(session) = session else {
        return Ok(AdminValidity {
            valid: false,
            admin_active: false,
        });
    };
    let admin = admin_user::Entity::find_by_id(session.admin_id as u64)
        .one(&state.db)
        .await?;
    let admin_active = admin
        .as_ref()
        .map(|a| a.status == AdminStatus::Active.code() as i8)
        .unwrap_or(false);
    if admin_active {
        backfill(&key, state).await;
    }
    Ok(AdminValidity {
        valid: true,
        admin_active,
    })
}

async fn backfill(key: &str, state: &SharedState) {
    if let Some(mut conn) = state.redis.clone() {
        if let Err(e) = conn.set_ex::<_, _, ()>(key, "1", 30).await {
            tracing::warn!(error = %e, "[session] Redis 回填失败(不影响本次结果)");
        }
    }
}
