//! gateway 域:AI 网关配置 CRUD + sync-models/test(stub 执行)。

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement};
use serde::Deserialize;
use serde_json::{json, Value};

use crate::service_category::CatalogError;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[gw] db error:{e}");
    CatalogError::new(500601)
}

pub fn not_found() -> CatalogError {
    CatalogError::new(404501)
}

pub async fn list(db: &DatabaseConnection) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, name, gateway_type, base_url, default_model, enabled, created_at FROM external_gateway_config ORDER BY id DESC",
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "name": r.try_get::<String>("", "name").unwrap_or_default(),
                "provider": r.try_get::<i64>("", "gateway_type").unwrap_or(1),
                "base_url": r.try_get::<String>("", "base_url").ok(),
                "model": r.try_get::<String>("", "default_model").ok(),
                "enabled": r.try_get::<bool>("", "enabled").unwrap_or(false),
            })
        })
        .collect())
}

pub async fn get(db: &DatabaseConnection, id: i64) -> Result<Value, CatalogError> {
    let all = list(db).await?;
    all.into_iter().find(|c| c["id"].as_i64() == Some(id)).ok_or_else(not_found)
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct GatewayUpsert {
    pub name: Option<String>,
    pub provider: Option<String>,
    pub base_url: Option<String>,
    pub model: Option<String>,
    pub enabled: Option<bool>,
}

fn validate(req: &GatewayUpsert) -> Result<(String, String), CatalogError> {
    let name = req.name.as_deref().map(str::trim).filter(|s| !s.is_empty());
    let provider = req.provider.as_deref().map(str::trim).filter(|s| !s.is_empty());
    match (name, provider) {
        (Some(n), Some(p)) => Ok((n.to_string(), p.to_string())),
        (None, _) => Err(CatalogError::field_validation(&[("name", "required")])),
        (_, None) => Err(CatalogError::field_validation(&[("provider", "required")])),
    }
}

pub async fn create(db: &DatabaseConnection, req: &GatewayUpsert) -> Result<Value, CatalogError> {
    let (name, provider) = validate(req)?;
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO external_gateway_config(name, gateway_type, base_url, api_key_encrypted, default_model, enabled, version, created_at, updated_at) VALUES (?,?,?,?,?,?,1,NOW(3),NOW(3))",
        [
            name.into(),
            provider.parse::<i64>().unwrap_or(1).into(),
            req.base_url.clone().into(),
            String::new().into(),
            req.model.clone().into(),
            (req.enabled.unwrap_or(false) as i64).into(),
        ],
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
    get(db, id as i64).await
}

pub async fn update(db: &DatabaseConnection, id: i64, req: &GatewayUpsert) -> Result<Value, CatalogError> {
    let (name, provider) = validate(req)?;
    let res = db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "UPDATE external_gateway_config SET name = ?, gateway_type = ?, base_url = ?, default_model = ?, enabled = ?, version = version + 1, updated_at = NOW(3) WHERE id = ?",
            [
                name.into(),
                provider.parse::<i64>().unwrap_or(1).into(),
                req.base_url.clone().into(),
                req.model.clone().into(),
                (req.enabled.unwrap_or(false) as i64).into(),
                id.into(),
            ],
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(not_found());
    }
    get(db, id).await
}

pub async fn delete(db: &DatabaseConnection, id: i64) -> Result<(), CatalogError> {
    let res = db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "DELETE FROM external_gateway_config WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(not_found());
    }
    Ok(())
}

/// sync-models(stub:标记同步时间)
pub async fn sync_models(db: &DatabaseConnection, id: i64) -> Result<Value, CatalogError> {
    let _ = db.execute(Statement::from_string(
        DbBackend::MySql,
        format!("UPDATE external_gateway_config SET updated_at = NOW(3) WHERE id = {}", id),
    ))
    .await
    .map_err(db_err)?;
    Ok(json!({ "synced": true, "models": [] }))
}

/// test(stub:连通性假阳性,real 随外部集成接线)
pub async fn test(db: &DatabaseConnection, id: i64) -> Result<Value, CatalogError> {
    let cfg = get(db, id).await?;
    Ok(json!({ "ok": true, "provider": cfg["provider"], "latency_ms": 42 }))
}
