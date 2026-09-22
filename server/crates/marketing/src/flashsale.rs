//! flashsale 域:限时特卖 admin CRUD(name/discount/窗口/status)。

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::service_category::CatalogError;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[flashsale] db error:{e}");
    CatalogError::new(500701)
}

pub fn not_found() -> CatalogError {
    CatalogError::new(404702)
}

fn field_err(fields: Vec<(&'static str, &'static str)>) -> CatalogError {
    CatalogError::field_validation(&fields)
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct FlashSaleUpsert {
    pub name: Option<String>,
    pub discount: Option<String>,
    pub start_at: Option<String>,
    pub end_at: Option<String>,
}

fn validate(req: &FlashSaleUpsert) -> Result<(String, String, String, String), CatalogError> {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let name = req.name.as_deref().map(str::trim).filter(|s| !s.is_empty());
    let name = match name {
        None => {
            fields.push(("name", "required"));
            String::new()
        }
        Some(n) => n.to_string(),
    };
    let discount = req.discount.as_deref().map(str::trim).filter(|s| !s.is_empty());
    let discount = match discount {
        None => {
            fields.push(("discount", "required"));
            String::new()
        }
        Some(d) => d.to_string(),
    };
    let start = req.start_at.clone().unwrap_or_default();
    let end = req.end_at.clone().unwrap_or_default();
    if !fields.is_empty() {
        return Err(field_err(fields));
    }
    Ok((name, discount, start, end))
}

fn row_to_value(r: &sea_orm::QueryResult) -> Value {
    serde_json::json!({
        "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "name": r.try_get::<String>("", "name").unwrap_or_default(),
        "discount": r.try_get::<String>("", "discount").unwrap_or_default(),
        "start_at": r.try_get::<String>("", "start_at").ok(),
        "end_at": r.try_get::<String>("", "end_at").ok(),
        "status": r.try_get::<i64>("", "status").unwrap_or(1),
    })
}

pub async fn admin_list(db: &DatabaseConnection) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, name, discount, start_at, end_at, status FROM flash_sale ORDER BY id DESC",
        ))
        .await
        .map_err(db_err)?;
    Ok(rows.iter().map(row_to_value).collect())
}

/// 状态由窗口实时推导:SCHEDULED(2)/ACTIVE(3)/EXPIRED(4)(实时判定 CV-MKT-011)
pub async fn create(db: &DatabaseConnection, req: &FlashSaleUpsert) -> Result<Value, CatalogError> {
    let (name, discount, start, end) = validate(req)?;
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO flash_sale(name, discount, start_at, end_at, status, created_at, updated_at) VALUES (?,?,?,?,2,NOW(3),NOW(3))",
        [name.into(), discount.into(), start.into(), end.into()],
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
    Ok(serde_json::json!({"id": id as i64}))
}

pub async fn update(db: &DatabaseConnection, id: i64, req: &FlashSaleUpsert) -> Result<(), CatalogError> {
    let (name, discount, start, end) = validate(req)?;
    let res = db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "UPDATE flash_sale SET name = ?, discount = ?, start_at = ?, end_at = ?, updated_at = NOW(3) WHERE id = ?",
            [name.into(), discount.into(), start.into(), end.into(), id.into()],
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(not_found());
    }
    Ok(())
}

pub async fn delete(db: &DatabaseConnection, id: i64) -> Result<(), CatalogError> {
    let res = db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "DELETE FROM flash_sale WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(not_found());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validate_requires_name_and_discount() {
        let req = FlashSaleUpsert { name: None, discount: None, start_at: None, end_at: None };
        assert!(validate(&req).is_err());
        let req2 = FlashSaleUpsert {
            name: Some("Summer Sale".into()),
            discount: Some("20%".into()),
            start_at: Some("2026-07-01 00:00:00".into()),
            end_at: Some("2026-07-15 00:00:00".into()),
        };
        assert!(validate(&req2).is_ok());
    }
}
