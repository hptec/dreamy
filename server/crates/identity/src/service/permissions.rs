//! 管理员实时权限解析(Java PermissionAspect 经 gRPC 调用)。
//!
//! Redis-first:命中 `identity:perms:{adminId}`(TTL 60s,JSON 数组)零 DB 返回;
//! 未命中 DB 两步查(对齐 Java RoleService.effectivePermissionKeys:禁 JOIN,先
//! role_permission 取 id 再 IN 查 perm_code);超管角色(is_locked)应用层短路全量。
//! 变更即时生效语义保留:角色/权限字典/管理员变更路径(P3)主动 DEL 对应键。

use common::state::SharedState;
use redis::AsyncCommands;
use sea_orm::{ColumnTrait, EntityTrait, QueryFilter};

use crate::entity::{admin_user, permission, role, role_permission};
use crate::service::SvcError;

const PERM_TTL: u64 = 60;

pub fn cache_key(admin_id: i64) -> String {
    format!("identity:perms:{admin_id}")
}

/// 解析管理员权限码集合;admin 不存在 → 空列表(对齐 Java,不用 NOT_FOUND)
pub async fn resolve(state: &SharedState, admin_id: i64) -> Result<Vec<String>, SvcError> {
    let key = cache_key(admin_id);
    if let Some(mut conn) = state.redis.clone() {
        match conn.get::<_, Option<String>>(&key).await {
            Ok(Some(json)) => match serde_json::from_str::<Vec<String>>(&json) {
                Ok(keys) => return Ok(keys),
                Err(e) => tracing::warn!(error = %e, "[perms] 缓存反序列化失败,降级 DB"),
            },
            Ok(_) => {}
            Err(e) => tracing::warn!(error = %e, "[perms] Redis 读失败,降级 DB"),
        }
    }

    let keys = resolve_from_db(state, admin_id).await?;
    if let (Some(mut conn), true) = (state.redis.clone(), !keys.is_empty()) {
        let json = serde_json::to_string(&keys).unwrap_or_default();
        if let Err(e) = conn.set_ex::<_, _, ()>(&key, json, PERM_TTL).await {
            tracing::warn!(error = %e, "[perms] Redis 回填失败(不影响本次结果)");
        }
    }
    Ok(keys)
}

async fn resolve_from_db(state: &SharedState, admin_id: i64) -> Result<Vec<String>, SvcError> {
    let Some(admin) = admin_user::Entity::find_by_id(admin_id as u64)
        .one(&state.db)
        .await?
    else {
        return Ok(Vec::new());
    };
    let Some(role) = role::Entity::find_by_id(admin.role_id as u64)
        .one(&state.db)
        .await?
    else {
        return Ok(Vec::new());
    };
    // 超管角色(is_locked)短路:全量权限码
    if role.is_locked == 1 {
        let all = permission::Entity::find().all(&state.db).await?;
        return Ok(all.into_iter().map(|p| p.perm_code).collect());
    }
    // 两步查(对齐 Java:禁 JOIN)
    let role_perms = role_permission::Entity::find()
        .filter(role_permission::Column::RoleId.eq(role.id))
        .all(&state.db)
        .await?;
    let perm_ids: Vec<i64> = role_perms.into_iter().map(|rp| rp.permission_id).collect();
    if perm_ids.is_empty() {
        return Ok(Vec::new());
    }
    let perms = permission::Entity::find()
        .filter(permission::Column::Id.is_in(perm_ids))
        .all(&state.db)
        .await?;
    Ok(perms.into_iter().map(|p| p.perm_code).collect())
}

/// 失效单个管理员权限缓存(管理员改角色/禁用时调用)
pub async fn invalidate(state: &SharedState, admin_id: i64) {
    if let Some(mut conn) = state.redis.clone() {
        if let Err(e) = conn.del::<_, ()>(cache_key(admin_id)).await {
            tracing::warn!(error = %e, "[perms] 缓存失效 DEL 失败,等待 TTL(≤60s)");
        }
    }
}

/// 按角色失效(角色权限变更影响该角色全部管理员)
pub async fn invalidate_by_role(state: &SharedState, role_id: i64) {
    let admins = admin_user::Entity::find()
        .filter(admin_user::Column::RoleId.eq(role_id as u64))
        .all(&state.db)
        .await;
    match admins {
        Ok(admins) => {
            for admin in admins {
                invalidate(state, admin.id as i64).await;
            }
        }
        Err(e) => tracing::warn!(error = %e, "[perms] 按角色失效查询管理员失败"),
    }
}
