//! 会话服务:Redis 权威主存(v2.2)+ DB 冷备降级。
//!
//! - `session:{token_id}` → 完整会话 JSON(TTL=refresh 有效期):校验 GET+判 access_exp,
//!   稳态零 DB(~0.1ms)
//! - `user_sessions:{user_id}` → SET[token_id]:admin 会话列表/按用户全撤销
//! - 撤销 = DEL + SREM + 冷备 UPDATE status=revoked(即时失效,无等待窗口)
//! - **key miss = invalid**(撤销/过期即删,无歧义);仅 Redis **连接故障**降级冷备表
//!   uk_token 点查(与 OTP 的差异:会话丢失自愈=重登录,无语义歧义)
//! - admin 侧维持 30s 缓存键(低频管理员量级,原实现保留)

use common::state::SharedState;
use redis::AsyncCommands;
use sea_orm::{ColumnTrait, EntityTrait, QueryFilter};
use serde::{Deserialize, Serialize};

use crate::entity::{admin_session, admin_user, user_session};
use crate::enums::{AdminStatus, SessionStatus};
use crate::service::SvcError;

const SESSION_TTL_MARGIN: i64 = 60; // TTL 加冗余,避免临界判失效

pub fn session_key(token_id: &str) -> String {
    format!("session:{token_id}")
}

pub fn user_sessions_key(user_id: i64) -> String {
    format!("user_sessions:{user_id}")
}

/// 会话权威数据(Redis JSON 载体;id=冷备表自增,REST 契约数字 session_id)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SessionData {
    pub id: i64,
    pub user_id: i64,
    pub method: i8,
    pub refresh_token_id: Option<String>,
    /// epoch 秒
    pub access_exp: i64,
    /// epoch 秒
    pub refresh_exp: i64,
    pub device: Option<String>,
    pub browser: Option<String>,
    pub ip: Option<String>,
    pub location: Option<String>,
}

/// 写入会话主存(登录/刷新调用;冷备表 INSERT 由登录流程同步完成取回 id)
pub async fn write_session(state: &SharedState, token_id: &str, data: &SessionData) {
    let Some(mut conn) = state.redis.clone() else {
        tracing::warn!("[session] Redis 缺席,会话仅落冷备(降级运行)");
        return;
    };
    let now = chrono::Utc::now().timestamp();
    let ttl = (data.refresh_exp - now + SESSION_TTL_MARGIN).max(1) as u64;
    let json = match serde_json::to_string(data) {
        Ok(v) => v,
        Err(e) => {
            tracing::error!(error = %e, "[session] 会话 JSON 序列化失败");
            return;
        }
    };
    if let Err(e) = conn
        .set_ex::<_, _, ()>(session_key(token_id), &json, ttl)
        .await
    {
        tracing::warn!(error = %e, "[session] Redis 写失败(冷备仍在,降级可查)");
        return;
    }
    // 集合索引:成员无独立 TTL,靠集合键 TTL + 读取时探活过滤
    let sk = user_sessions_key(data.user_id);
    let _: Result<i64, _> = conn.sadd(&sk, token_id).await;
    let _: Result<(), _> = conn.expire(&sk, ttl as i64).await;
}

/// 撤销单个会话(登出/单会话强制下线):DEL + SREM + 冷备 UPDATE,即时生效
pub async fn revoke_store(state: &SharedState, token_id: &str, user_id: i64) {
    if let Some(mut conn) = state.redis.clone() {
        if let Err(e) = conn.del::<_, ()>(session_key(token_id)).await {
            tracing::warn!(error = %e, "[session] 撤销 DEL 失败(降级:冷备 status=revoked 仍生效)");
        }
        let _: Result<i64, _> = conn.srem(user_sessions_key(user_id), token_id).await;
    }
    mark_revoked(state, token_id).await;
}

/// 仅撤销 Redis 主存(刷新场景专用):旧 access 即时失效,但**不**标冷备 revoked——
/// 冷备行将由刷新的乐观锁 UPDATE 原位更新为新链;若在此标 revoked,
/// 该行对下一次刷新的 status=Active 检查即失配,旋转链断链(第二次刷新必 40102)
pub async fn revoke_store_soft(state: &SharedState, token_id: &str, user_id: i64) {
    if let Some(mut conn) = state.redis.clone() {
        if let Err(e) = conn.del::<_, ()>(session_key(token_id)).await {
            tracing::warn!(error = %e, "[session] 刷新撤销 DEL 失败(旧 access 残留至 TTL)");
        }
        let _: Result<i64, _> = conn.srem(user_sessions_key(user_id), token_id).await;
    }
}

/// 撤销某用户全部会话(禁用/强制全下线):遍历集合 DEL + 冷备批量 UPDATE
pub async fn revoke_all_for_user(state: &SharedState, user_id: i64) {
    if let Some(mut conn) = state.redis.clone() {
        let tokens: Vec<String> = conn
            .smembers(user_sessions_key(user_id))
            .await
            .unwrap_or_default();
        for t in &tokens {
            let _: Result<(), _> = conn.del(session_key(t)).await;
        }
        let _: Result<(), _> = conn.del(user_sessions_key(user_id)).await;
    }
    mark_all_revoked(state, user_id).await;
}

async fn mark_revoked(state: &SharedState, token_id: &str) {
    use sea_orm::ActiveModelTrait;
    let row = user_session::Entity::find()
        .filter(user_session::Column::TokenId.eq(token_id))
        .one(&state.db)
        .await;
    if let Ok(Some(row)) = row {
        let mut am: user_session::ActiveModel = row.into();
        am.status = sea_orm::Set(SessionStatus::Revoked.code() as i8);
        if let Err(e) = am.update(&state.db).await {
            tracing::warn!(error = %e, "[session] 冷备撤销标记失败");
        }
    }
}

async fn mark_all_revoked(state: &SharedState, user_id: i64) {
    use sea_orm::QueryFilter as _;
    // 批量 UPDATE 走表达式(SeaORM update_many)
    let _ = user_session::Entity::update_many()
        .col_expr(
            user_session::Column::Status,
            sea_orm::sea_query::Expr::value(SessionStatus::Revoked.code() as i8),
        )
        .filter(user_session::Column::UserId.eq(user_id as u64))
        .filter(user_session::Column::Status.eq(SessionStatus::Active.code() as i8))
        .exec(&state.db)
        .await;
}

/// store 会话有效性(热路径):GET 主存 → 判 access_exp;
/// key miss = invalid;仅 Redis 连接故障降级冷备 uk_token 点查
pub async fn validate_store(state: &SharedState, token_id: &str) -> Result<bool, SvcError> {
    if token_id.trim().is_empty() {
        return Err(SvcError::InvalidArg("token_id 为空".into()));
    }
    match state.redis.as_ref() {
        Some(conn) => {
            let mut conn = conn.clone();
            match conn.get::<_, Option<String>>(session_key(token_id)).await {
                // 主存命中:判 access_exp(撤销/过期已由 DEL/TTL 保证存在性)
                Ok(Some(json)) => match serde_json::from_str::<SessionData>(&json) {
                    Ok(data) => Ok(data.access_exp > chrono::Utc::now().timestamp()),
                    Err(e) => {
                        tracing::warn!(error = %e, "[session] 主存 JSON 解析失败,按无效处理");
                        Ok(false)
                    }
                },
                // miss = 撤销/过期/不存在,一律 invalid(无歧义)
                Ok(None) => Ok(false),
                // 连接故障 → 冷备降级(DB 为真)
                Err(e) => {
                    tracing::warn!(error = %e, "[session] Redis 连接故障,降级冷备表");
                    validate_store_from_db(state, token_id).await
                }
            }
        }
        // Redis 未配置(降级部署) → 冷备为真
        None => validate_store_from_db(state, token_id).await,
    }
}

async fn validate_store_from_db(state: &SharedState, token_id: &str) -> Result<bool, SvcError> {
    let row = user_session::Entity::find()
        .filter(user_session::Column::TokenId.eq(token_id))
        .filter(user_session::Column::Status.eq(SessionStatus::Active.code() as i8))
        .one(&state.db)
        .await?;
    Ok(row
        .and_then(|r| r.access_expires_at)
        .map(|exp| exp.and_utc().timestamp() > chrono::Utc::now().timestamp())
        .unwrap_or(false))
}

// ===== admin 侧(低频,维持 30s 缓存键 + DB 双查,v1 语义) =====

pub fn admin_key(token_id: &str) -> String {
    format!("admin:session:valid:{token_id}")
}

// ===== refresh 旋转链证据(V4 重用检测) =====

fn rotated_key(old_jti: &str) -> String {
    format!("session:rotated:{old_jti}")
}

/// 旋转时登记旧 refresh jti → user_id(TTL=新 refresh 有效期):
/// 该 jti 再次出现即盗用信号,持有整链撤销依据
pub async fn mark_rotated(state: &SharedState, old_jti: &str, user_id: i64, ttl_secs: i64) {
    if let Some(mut conn) = state.redis.clone() {
        let _: Result<(), _> = conn
            .set_ex::<_, _, ()>(rotated_key(old_jti), user_id.to_string(), ttl_secs.max(1) as u64)
            .await;
    }
}

/// 旧 refresh jti 是否已被旋转过(返回属主 user_id)
pub async fn rotated_owner(state: &SharedState, old_jti: &str) -> Option<i64> {
    let mut conn = state.redis.clone()?;
    let v: Option<String> = conn.get(rotated_key(old_jti)).await.ok().flatten();
    v.and_then(|s| s.parse().ok())
}

pub async fn revoke_admin(state: &SharedState, token_id: &str) {
    if let Some(mut conn) = state.redis.clone() {
        if let Err(e) = conn.del::<_, ()>(admin_key(token_id)).await {
            tracing::warn!(error = %e, "[session] admin 撤销 DEL 失败,等待 TTL(≤30s)");
        }
    }
}

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
        if let Some(mut conn) = state.redis.clone() {
            if let Err(e) = conn.set_ex::<_, _, ()>(&key, "1", 30).await {
                tracing::warn!(error = %e, "[session] Redis 回填失败(不影响本次结果)");
            }
        }
    }
    Ok(AdminValidity {
        valid: true,
        admin_active,
    })
}
