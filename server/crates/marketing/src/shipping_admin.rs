//! shippingrate/carrier admin 域:承运商/运费/选项 CRUD + countries + quote-preview。

use sea_orm::ConnectionTrait;
use serde_json::{json, Value};

use crate::service_category::CatalogError;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[shp-admin] db error:{e}");
    CatalogError::new(500601)
}

pub async fn carrier_list(db: &sea_orm::DatabaseConnection) -> Result<Vec<Value>, CatalogError> {
    let rows = db.query_all(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        "SELECT id, name, code, tracking_url_template, lead_time, status FROM carrier ORDER BY id ASC",
    )).await.map_err(db_err)?;
    Ok(rows.iter().map(|r| json!({
        "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "name": r.try_get::<String>("", "name").unwrap_or_default(),
        "code": r.try_get::<String>("", "code").unwrap_or_default(),
        "tracking_url_template": r.try_get::<String>("", "tracking_url_template").ok(),
        "lead_time": r.try_get::<String>("", "lead_time").ok(),
        "status": r.try_get::<i64>("", "status").unwrap_or(1),
    })).collect())
}

pub async fn option_list(db: &sea_orm::DatabaseConnection) -> Result<Vec<Value>, CatalogError> {
    let rows = db.query_all(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        "SELECT id, zone, carrier_code, service_level, fee_under, fee_over, threshold, transit_days_min, transit_days_max, enabled FROM shipping_option ORDER BY zone ASC, carrier_code ASC",
    )).await.map_err(db_err)?;
    Ok(rows.iter().map(|r| json!({
        "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "zone": r.try_get::<String>("", "zone").unwrap_or_default(),
        "carrier_code": r.try_get::<String>("", "carrier_code").unwrap_or_default(),
        "service_level": r.try_get::<i64>("", "service_level").unwrap_or(1),
        "fee_under": crate::cart::dec(r, "fee_under").unwrap_or(0.0),
        "fee_over": crate::cart::dec(r, "fee_over"),
        "threshold": crate::cart::dec(r, "threshold"),
        "transit_days_min": r.try_get::<i64>("", "transit_days_min").ok(),
        "transit_days_max": r.try_get::<i64>("", "transit_days_max").ok(),
        "enabled": r.try_get::<bool>("", "enabled").unwrap_or(true),
    })).collect())
}

pub async fn rate_list(db: &sea_orm::DatabaseConnection) -> Result<Vec<Value>, CatalogError> {
    let rows = db.query_all(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        "SELECT id, zone, fee_under, fee_over, threshold FROM shipping_rate ORDER BY id ASC",
    )).await.map_err(db_err)?;
    Ok(rows.iter().map(|r| json!({
        "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "zone": r.try_get::<String>("", "zone").unwrap_or_default(),
        "fee_under": crate::cart::dec(r, "fee_under").unwrap_or(0.0),
        "fee_over": crate::cart::dec(r, "fee_over"),
        "threshold": crate::cart::dec(r, "threshold"),
    })).collect())
}

pub async fn countries(db: &sea_orm::DatabaseConnection) -> Result<Vec<Value>, CatalogError> {
    // 支持配送的国家列表 = CountryCatalog 中有 shipping option zone 的
    let rows = db.query_all(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        "SELECT DISTINCT zone FROM shipping_option WHERE enabled = 1",
    )).await.map_err(db_err)?;
    let zones: Vec<String> = rows.iter().filter_map(|r| r.try_get("", "zone").ok()).collect();
    Ok(common::country_catalog::COUNTRIES
        .iter()
        .filter(|c| zones.iter().any(|z| z.eq_ignore_ascii_case(c.zone)))
        .map(|c| json!({"code": c.code, "name": c.name, "zone": c.zone}))
        .collect())
}

/// quote-preview(admin 试算;同 checkout 逻辑复用)
pub async fn quote_preview(
    db: &sea_orm::DatabaseConnection,
    country: &str,
    subtotal_usd: f64,
) -> Result<Vec<Value>, CatalogError> {
    let zone = crate::checkout::resolve_zone(None, Some(country));
    let quotes = crate::checkout::quote_options(db, &zone, subtotal_usd).await?;
    Ok(quotes.iter().map(|q| json!({
        "carrier": q.carrier, "carrier_code": q.carrier_code, "fee_usd": q.fee_usd,
        "service_level": q.service_level,
        "transit_days_min": q.transit_days_min, "transit_days_max": q.transit_days_max,
    })).collect())
}
