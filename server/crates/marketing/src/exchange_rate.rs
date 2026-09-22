//! exchangerate 域:汇率 admin(列表/手动改价/历史/refresh)。

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement};
use serde_json::{json, Value};

use crate::service_category::CatalogError;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[fx] db error:{e}");
    CatalogError::new(500601)
}

pub fn not_found() -> CatalogError {
    CatalogError::new(404501)
}

pub async fn list(db: &DatabaseConnection) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, currency, rate, source, manual_override, synced_at FROM exchange_rate ORDER BY currency ASC",
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            json!({
                "currency": r.try_get::<String>("", "currency").unwrap_or_default(),
                "rate": crate::cart::dec(r, "rate").unwrap_or(1.0),
                "source": r.try_get::<i64>("", "source").unwrap_or(1),
                "manual_override": r.try_get::<bool>("", "manual_override").unwrap_or(false),
                "synced_at": r.try_get::<String>("", "synced_at").ok(),
            })
        })
        .collect())
}

/// 手动改价(manual_override=1;rate>0)
pub async fn put_rate(db: &DatabaseConnection, currency: &str, rate: f64, operator: i64) -> Result<Value, CatalogError> {
    if rate <= 0.0 {
        return Err(CatalogError::field_validation(&[("rate", "must_be_positive")]));
    }
    let cur = currency.trim().to_uppercase();
    let res = db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "UPDATE exchange_rate SET rate = ?, manual_override = 1, updated_by = ?, synced_at = NOW(3), updated_at = NOW(3) WHERE currency = ?",
            [rate.into(), operator.into(), cur.into()],
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(not_found());
    }
    let row = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT currency, rate, manual_override, synced_at FROM exchange_rate WHERE currency = ?",
            [currency.trim().to_uppercase().into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    Ok(json!({
        "currency": row.try_get::<String>("", "currency").unwrap_or_default(),
        "rate": crate::cart::dec(&row, "rate").unwrap_or(1.0),
        "manual_override": row.try_get::<bool>("", "manual_override").unwrap_or(false),
    }))
}

/// 历史(sales_refreshed_at 风格:exchange_rate 无历史表 → 返回 synced_at 快照;真实历史随 operation_log)
pub async fn history(db: &DatabaseConnection, currency: &str) -> Result<Vec<Value>, CatalogError> {
    let cur = currency.trim().to_uppercase();
    let row = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT rate, synced_at FROM exchange_rate WHERE currency = ?",
            [cur.clone().into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    Ok(vec![json!({
        "currency": cur.clone(),
        "rate": crate::cart::dec(&row, "rate").unwrap_or(1.0),
        "synced_at": row.try_get::<String>("", "synced_at").ok(),
    })])
}

/// refresh(stub:重置 manual_override 并刷新 synced_at;真实源随外部集成 client 迁移接线)
pub async fn refresh(db: &DatabaseConnection, operator: i64) -> Result<Vec<Value>, CatalogError> {
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE exchange_rate SET manual_override = 0, updated_by = ?, synced_at = NOW(3), updated_at = NOW(3)",
        [operator.into()],
    ))
    .await
    .map_err(db_err)?;
    list(db).await
}
