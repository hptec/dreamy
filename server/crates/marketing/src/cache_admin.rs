//! cache invalidation 域:任务列表/summary/targets/手动任务/重试。
//! 与 infra 两级缓存联动:任务执行 = 失效对应缓存前缀。

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement};
use serde::Deserialize;
use serde_json::{json, Value};

use crate::service_category::CatalogError;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[cache] db error:{e}");
    CatalogError::new(500601)
}

pub fn not_found() -> CatalogError {
    CatalogError::new(404501)
}

const TARGETS: &[&str] = &[
    "products", "categories", "collections", "banners", "content", "home_sections", "navigation", "footer", "announcements",
];

fn row_to_value(r: &sea_orm::QueryResult) -> Value {
    let targets: Option<String> = r.try_get("", "targets").ok();
    json!({
        "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "correlation_id": r.try_get::<String>("", "correlation_id").ok(),
        "trigger_mode": r.try_get::<String>("", "trigger_mode").unwrap_or_default(),
        "resource_type": r.try_get::<String>("", "resource_type").unwrap_or_default(),
        "resource_id": r.try_get::<String>("", "resource_id").ok(),
        "targets": targets.and_then(|t| serde_json::from_str::<Value>(&t).ok()),
        "status": r.try_get::<i64>("", "status").unwrap_or(1),
        "attempt_count": r.try_get::<i64>("", "attempt_count").unwrap_or(0),
        "error_message": r.try_get::<String>("", "error_message").ok(),
        "triggered_at": r.try_get::<String>("", "triggered_at").ok(),
        "completed_at": r.try_get::<String>("", "completed_at").ok(),
    })
}

pub async fn list_tasks(db: &DatabaseConnection, status: Option<i64>, page: i64, page_size: i64) -> Result<(Vec<Value>, i64), CatalogError> {
    let where_clause = status.map(|s| format!(" WHERE status = {}", s)).unwrap_or_default();
    let total = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT COUNT(*) AS n FROM cache_invalidation_task{}", where_clause),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<i64>("", "n").ok())
        .unwrap_or(0);
    let offset = (page - 1) * page_size;
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT id, correlation_id, trigger_mode, resource_type, resource_id, targets, status, attempt_count, error_message, triggered_at, completed_at \
                 FROM cache_invalidation_task{} ORDER BY id DESC LIMIT {} OFFSET {}",
                where_clause, page_size, offset
            ),
        ))
        .await
        .map_err(db_err)?;
    Ok((rows.iter().map(row_to_value).collect(), total))
}

pub async fn summary(db: &DatabaseConnection) -> Result<Value, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT status, COUNT(*) AS n FROM cache_invalidation_task GROUP BY status",
        ))
        .await
        .map_err(db_err)?;
    let mut m = serde_json::Map::new();
    for r in &rows {
        let s = r.try_get::<i64>("", "status").unwrap_or(0);
        let n = r.try_get::<i64>("", "n").unwrap_or(0);
        m.insert(format!("status_{}", s), json!(n));
    }
    Ok(Value::Object(m))
}

pub fn targets() -> Vec<String> {
    TARGETS.iter().map(|s| s.to_string()).collect()
}

/// 手动失效任务:创建记录 + 立即执行(清 Redis 前缀)
pub async fn create_manual_task(
    db: &DatabaseConnection,
    redis: Option<&redis::aio::ConnectionManager>,
    targets: &[String],
    reason: &str,
) -> Result<Value, CatalogError> {
    let valid: Vec<&String> = targets.iter().filter(|t| TARGETS.contains(&t.as_str())).collect();
    if valid.is_empty() {
        return Err(CatalogError::field_validation(&[("targets", "invalid")]));
    }
    let correlation_id = uuid::Uuid::new_v4().to_string();
    let targets_json = serde_json::json!(valid).to_string();
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO cache_invalidation_task(correlation_id, trigger_mode, trigger_point, resource_type, targets, details, triggered_by, triggered_at, scheduled_at, started_at, completed_at, status, attempt_count, max_attempts, created_at, updated_at) \
         VALUES (?, 'manual', 'admin_manual', 'cache_prefix', ?, ?, 'admin', NOW(3), NOW(3), NOW(3), NOW(3), 3, 1, 3, NOW(3), NOW(3))",
        [correlation_id.clone().into(), targets_json.clone().into(), json!({"reason": reason}).to_string().into()],
    ))
    .await
    .map_err(db_err)?;
    let id = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            "SELECT LAST_INSERT_ID() AS id".to_string(),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<u64>("", "id").ok())
        .unwrap_or(0);
    // 立即失效:DEL 缓存前缀
    if let Some(conn) = redis {
        let mut conn = conn.clone();
        for t in &valid {
            let pattern = format!("{}:*", t);
            if let Ok(keys) = redis::cmd("KEYS").arg(&pattern).query_async::<Vec<String>>(&mut conn).await {
                if !keys.is_empty() {
                    let _ = redis::cmd("DEL").arg(keys).query_async::<()>(&mut conn).await;
                }
            }
        }
    }
    Ok(json!({ "id": id as i64, "correlation_id": correlation_id, "status": 3 }))
}

/// 重试(仅失败任务;status 1=pending 2=running 3=success 4=failed)
pub async fn retry(db: &DatabaseConnection, redis: Option<&redis::aio::ConnectionManager>, id: i64) -> Result<bool, CatalogError> {
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT id, status, targets FROM cache_invalidation_task WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    let status: i64 = row.try_get("", "status").unwrap_or(1);
    if status != 4 {
        return Ok(false); // 仅失败任务可重试
    }
    let targets_str: Option<String> = row.try_get("", "targets").ok();
    if let (Some(conn), Some(ts)) = (redis, targets_str) {
        if let Ok(serde_json::Value::Array(arr)) = serde_json::from_str::<Value>(&ts) {
            let mut conn = conn.clone();
            for t in arr {
                if let Some(t) = t.as_str() {
                    let pattern = format!("{}:*", t);
                    if let Ok(keys) = redis::cmd("KEYS").arg(&pattern).query_async::<Vec<String>>(&mut conn).await {
                        if !keys.is_empty() {
                            let _ = redis::cmd("DEL").arg(keys).query_async::<()>(&mut conn).await;
                        }
                    }
                }
            }
        }
    }
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE cache_invalidation_task SET status = 3, completed_at = NOW(3), attempt_count = attempt_count + 1, updated_at = NOW(3) WHERE id = ?",
        [id.into()],
    ))
    .await
    .map_err(db_err)?;
    Ok(true)
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct ManualTaskRequest {
    pub targets: Option<Vec<String>>,
    pub reason: Option<String>,
}
