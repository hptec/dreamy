//! tax 域:税率规则 CRUD + 窗口重叠守卫(order-flow-complete F)。
//! 对齐 TaxRuleService;错误码 404906/422907/422601。政策(policy)表与 calculator 随 checkout 域接线。

use std::collections::HashMap;

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement, TransactionTrait};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use common::country_catalog as cc;

use crate::service_category::CatalogError;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[tax] db error:{e}");
    CatalogError::new(500601)
}

fn not_found() -> CatalogError {
    CatalogError::new(404906)
}

fn overlap_err(mut details: serde_json::Map<String, Value>) -> CatalogError {
    let mut e = CatalogError::new(422907);
    e.details = Some(Value::Object(details));
    e
}

fn field_err(fields: Vec<(&'static str, &'static str)>) -> CatalogError {
    CatalogError::field_validation(&fields)
}

async fn audit(audit_db: &DatabaseConnection, operator: &str, action: &str, target: &str, changes: &str) {
    let oid: Option<i64> = operator.parse().ok();
    if let Err(e) = audit_db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO operation_log(operator_id, operator_name, action, target, ip, user_agent, changes, created_at, updated_at) VALUES (?,NULL,?,?,NULL,NULL,?,NOW(3),NOW(3))",
            [oid.into(), action.into(), target.into(), changes.into()],
        ))
        .await
    {
        tracing::warn!("[tax] audit best-effort failed:{e}");
    }
}

/// 窗口重叠(NULL 边界视为无穷)
pub fn windows_overlap(a_from: Option<&str>, a_to: Option<&str>, b_from: Option<&str>, b_to: Option<&str>) -> bool {
    let min = String::new();
    let max = "9999-12-31";
    let af = a_from.unwrap_or(&min);
    let at = a_to.unwrap_or(max);
    let bf = b_from.unwrap_or(&min);
    let bt = b_to.unwrap_or(max);
    af <= bt && bf <= at
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct RuleUpsert {
    pub country_code: Option<String>,
    pub region: Option<String>,
    pub tax_type: Option<i64>,
    pub rate_scaled: Option<i64>,
    pub applies_to_shipping: Option<bool>,
    pub threshold_usd: Option<f64>,
    pub effective_from: Option<String>,
    pub effective_to: Option<String>,
    pub enabled: Option<bool>,
    pub label: Option<String>,
}

#[derive(Debug)]
struct ValidRule {
    country_code: String,
    region: String,
    tax_type: i64,
    rate_scaled: i64,
    applies_to_shipping: bool,
    threshold_usd: Option<f64>,
    effective_from: Option<String>,
    effective_to: Option<String>,
    enabled: bool,
    label: Option<String>,
}

fn validate(req: &RuleUpsert) -> Result<ValidRule, CatalogError> {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let cc_raw = req.country_code.as_deref().map(str::trim).filter(|s| !s.is_empty());
    let country_code = match cc_raw {
        None => {
            fields.push(("country_code", "required"));
            String::new()
        }
        Some(c) => {
            let up = c.to_uppercase();
            if !cc::is_known_code(&up) {
                fields.push(("country_code", "invalid_enum"));
            }
            up
        }
    };
    // region:US/CA/AU 规范化州码;其他仅 ≤8 大写字符;空串=国家级
    let region = match req.region.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
        None => String::new(),
        Some(r) if cc::has_regions(&country_code) => match cc::resolve_region_code(&country_code, r) {
            Some(code) => code.to_string(),
            None => {
                fields.push(("region", "invalid_enum"));
                r.to_uppercase()
            }
        },
        Some(r) => {
            let up = r.to_uppercase();
            if up.len() > 8 {
                fields.push(("region", "too_long"));
            }
            up
        }
    };
    let tax_type = match req.tax_type {
        Some(t @ 1..=4) => t,
        Some(_) => {
            fields.push(("tax_type", "invalid_enum"));
            1
        }
        None => {
            fields.push(("tax_type", "required"));
            1
        }
    };
    let rate_scaled = match req.rate_scaled {
        None => {
            fields.push(("rate_scaled", "required"));
            0
        }
        Some(r) if !(0..=10000).contains(&r) => {
            fields.push(("rate_scaled", "range_invalid"));
            0
        }
        Some(r) => r,
    };
    if req.threshold_usd.is_some_and(|t| t < 0.0) {
        fields.push(("threshold_usd", "range_invalid"));
    }
    if let (Some(f), Some(t)) = (&req.effective_from, &req.effective_to) {
        if t < f {
            fields.push(("effective_to", "range_invalid"));
        }
    }
    let label = match req.label.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
        Some(l) if l.len() > 64 => {
            fields.push(("label", "too_long"));
            None
        }
        other => other.map(String::from),
    };
    if !fields.is_empty() {
        return Err(field_err(fields));
    }
    Ok(ValidRule {
        country_code,
        region,
        tax_type,
        rate_scaled,
        applies_to_shipping: req.applies_to_shipping.unwrap_or(false),
        threshold_usd: req.threshold_usd,
        effective_from: req.effective_from.clone(),
        effective_to: req.effective_to.clone(),
        enabled: req.enabled.unwrap_or(true),
        label,
    })
}

fn key(r: &ValidRule) -> String {
    format!(
        "{}/{}",
        r.country_code,
        if r.region.is_empty() { "*".to_string() } else { r.region.clone() }
    )
}

fn row_to_dto(r: &sea_orm::QueryResult) -> Value {
    serde_json::json!({
        "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "country_code": r.try_get::<String>("", "country_code").unwrap_or_default(),
        "region": r.try_get::<String>("", "region").ok(),
        "tax_type": r.try_get::<i64>("", "tax_type").ok(),
        "rate_scaled": r.try_get::<i64>("", "rate_scaled").ok(),
        "applies_to_shipping": r.try_get::<bool>("", "applies_to_shipping").unwrap_or(false),
        "threshold_usd": r.try_get::<f64>("", "threshold_usd").ok(),
        "effective_from": r.try_get::<String>("", "effective_from").ok(),
        "effective_to": r.try_get::<String>("", "effective_to").ok(),
        "enabled": r.try_get::<bool>("", "enabled").unwrap_or(false),
        "label": r.try_get::<String>("", "label").ok(),
        "updated_at": r.try_get::<String>("", "updated_at").ok(),
    })
}

const RULE_COLS: &str = "id, country_code, region, tax_type, rate_scaled, applies_to_shipping, threshold_usd, effective_from, effective_to, enabled, label, updated_at";

/// E-CAT list(countryCode 筛选;大写归一)
pub async fn list(db: &DatabaseConnection, country_code: Option<String>) -> Result<Vec<Value>, CatalogError> {
    let sql = match country_code.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
        Some(c) => format!(
            "SELECT {} FROM tax_rule WHERE country_code = '{}' ORDER BY id ASC",
            RULE_COLS,
            c.to_uppercase().replace('\'', "")
        ),
        None => format!("SELECT {} FROM tax_rule ORDER BY id ASC", RULE_COLS),
    };
    let rows = db.query_all(Statement::from_string(DbBackend::MySql, sql)).await.map_err(db_err)?;
    Ok(rows.iter().map(row_to_dto).collect())
}

/// 同 key 窗口重叠检查(NULL 边界无穷;排除自身)
async fn assert_no_overlap(
    db: &DatabaseConnection,
    v: &ValidRule,
    exclude_id: Option<i64>,
    all_details: serde_json::Map<String, Value>,
) -> Result<(), CatalogError> {
    let exclude = exclude_id.map(|i| format!(" AND id != {}", i)).unwrap_or_default();
    let sql = format!(
        "SELECT {}, id AS conflict_id FROM tax_rule WHERE country_code = '{}' AND region = '{}' AND tax_type = {}{}",
        RULE_COLS, v.country_code, v.region, v.tax_type, exclude
    );
    let rows = db.query_all(Statement::from_string(DbBackend::MySql, sql)).await.map_err(db_err)?;
    for other in &rows {
        let of: Option<String> = other.try_get("", "effective_from").ok();
        let ot: Option<String> = other.try_get("", "effective_to").ok();
        if windows_overlap(v.effective_from.as_deref(), v.effective_to.as_deref(), of.as_deref(), ot.as_deref()) {
            let mut d = all_details.clone();
            d.insert(
                "conflict_rule_id".into(),
                Value::from(other.try_get::<u64>("", "conflict_id").map(|x| x as i64).unwrap_or(0)),
            );
            return Err(overlap_err(d));
        }
    }
    Ok(())
}

fn key_details(v: &ValidRule) -> serde_json::Map<String, Value> {
    let mut m = serde_json::Map::new();
    m.insert("country_code".into(), Value::from(v.country_code.clone()));
    m.insert("region".into(), Value::from(v.region.clone()));
    m.insert("tax_type".into(), Value::from(v.tax_type));
    m
}

/// 创建(TX;uk 冲突=重叠)
pub async fn create(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    req: RuleUpsert,
    operator: &str,
) -> Result<Value, CatalogError> {
    let v = validate(&req)?;
    let details = key_details(&v);
    assert_no_overlap(db, &v, None, details).await?;
    let tx = db.begin().await.map_err(db_err)?;
    let res = tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO tax_rule(country_code, region, tax_type, rate_scaled, applies_to_shipping, threshold_usd, effective_from, effective_to, enabled, label, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,NOW(3),NOW(3))",
        [
            v.country_code.clone().into(),
            v.region.clone().into(),
            v.tax_type.into(),
            v.rate_scaled.into(),
            v.applies_to_shipping.into(),
            v.threshold_usd.into(),
            v.effective_from.clone().into(),
            v.effective_to.clone().into(),
            v.enabled.into(),
            v.label.clone().into(),
        ],
    ));
    let res = match res.await {
        Ok(r) => r,
        Err(e) => {
            if e.to_string().contains("Duplicate") || e.to_string().contains("1062") {
                return Err(overlap_err(key_details(&v)));
            }
            return Err(db_err(e));
        }
    };
    let id = res.last_insert_id();
    tx.commit().await.map_err(db_err)?;
    audit(
        audit_db,
        operator,
        "税率规则变更",
        &key(&v),
        &format!(r#"{{"op":"create","rate_scaled":{}}}"#, v.rate_scaled),
    )
    .await;
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT {} FROM tax_rule WHERE id = {}", RULE_COLS, id),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    Ok(row_to_dto(&row))
}

/// 编辑
pub async fn update(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    req: RuleUpsert,
    operator: &str,
) -> Result<Value, CatalogError> {
    let existing = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT {} FROM tax_rule WHERE id = {}", RULE_COLS, id),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    let before: i64 = existing.try_get("", "rate_scaled").unwrap_or(0);
    let v = validate(&req)?;
    let details = key_details(&v);
    assert_no_overlap(db, &v, Some(id), details).await?;
    let tx = db.begin().await.map_err(db_err)?;
    let res = tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE tax_rule SET country_code=?, region=?, tax_type=?, rate_scaled=?, applies_to_shipping=?, threshold_usd=?, effective_from=?, effective_to=?, enabled=?, label=?, updated_at=NOW(3) WHERE id=?",
        [
            v.country_code.clone().into(),
            v.region.clone().into(),
            v.tax_type.into(),
            v.rate_scaled.into(),
            v.applies_to_shipping.into(),
            v.threshold_usd.into(),
            v.effective_from.clone().into(),
            v.effective_to.clone().into(),
            v.enabled.into(),
            v.label.clone().into(),
            id.into(),
        ],
    ));
    if let Err(e) = res.await {
        if e.to_string().contains("1062") {
            return Err(overlap_err(key_details(&v)));
        }
        return Err(db_err(e));
    }
    tx.commit().await.map_err(db_err)?;
    audit(
        audit_db,
        operator,
        "税率规则变更",
        &key(&v),
        &format!(r#"{{"op":"update","before":{before},"after":{}}}"#, v.rate_scaled),
    )
    .await;
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT {} FROM tax_rule WHERE id = {}", RULE_COLS, id),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    Ok(row_to_dto(&row))
}

/// enabled Toggle(幂等)
pub async fn set_enabled(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    enabled: Option<bool>,
    operator: &str,
) -> Result<Value, CatalogError> {
    let Some(enabled) = enabled else {
        return Err(field_err(vec![("enabled", "required")]));
    };
    let existing = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT {} FROM tax_rule WHERE id = {}", RULE_COLS, id),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    let current: bool = existing.try_get("", "enabled").unwrap_or(false);
    if current != enabled {
        let cc: String = existing.try_get("", "country_code").unwrap_or_default();
        let region: String = existing.try_get("", "region").unwrap_or_default();
        let tt: i64 = existing.try_get("", "tax_type").unwrap_or(1);
        let tx = db.begin().await.map_err(db_err)?;
        tx.execute(Statement::from_string(
            DbBackend::MySql,
            format!("UPDATE tax_rule SET enabled = {}, updated_at = NOW(3) WHERE id = {}", enabled as i8, id),
        ))
        .await
        .map_err(db_err)?;
        tx.commit().await.map_err(db_err)?;
        audit(
            audit_db,
            operator,
            "税率规则变更",
            &format!("{}/{}", cc, if region.is_empty() { "*".into() } else { region }),
            &format!(r#"{{"op":"enabled","enabled":{}}}"#, enabled),
        )
        .await;
    }
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT {} FROM tax_rule WHERE id = {}", RULE_COLS, id),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    Ok(row_to_dto(&row))
}

/// 删除
pub async fn delete(db: &DatabaseConnection, audit_db: &DatabaseConnection, id: i64, operator: &str) -> Result<(), CatalogError> {
    let existing = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT {} FROM tax_rule WHERE id = {}", RULE_COLS, id),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    let cc: String = existing.try_get("", "country_code").unwrap_or_default();
    let region: String = existing.try_get("", "region").unwrap_or_default();
    let tt: i64 = existing.try_get("", "tax_type").unwrap_or(1);
    let tx = db.begin().await.map_err(db_err)?;
    let res = tx
        .execute(Statement::from_string(
            DbBackend::MySql,
            format!("DELETE FROM tax_rule WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(not_found());
    }
    tx.commit().await.map_err(db_err)?;
    audit(
        audit_db,
        operator,
        "税率规则变更",
        &format!("{}/{}", cc, if region.is_empty() { "*".into() } else { region }),
        r#"{"op":"delete"}"#,
    )
    .await;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn windows_overlap_null_is_infinity() {
        assert!(windows_overlap(None, None, Some("2026-01-01"), Some("2026-12-31")));
        assert!(windows_overlap(Some("2026-01-01"), None, None, None));
        assert!(!windows_overlap(
            Some("2026-01-01"),
            Some("2026-06-30"),
            Some("2026-07-01"),
            Some("2026-12-31")
        ));
        assert!(windows_overlap(
            Some("2026-01-01"),
            Some("2026-06-30"),
            Some("2026-06-30"),
            Some("2026-12-31")
        ), "边界相接=重叠");
    }

    #[test]
    fn validate_country_and_rate() {
        let req = RuleUpsert {
            country_code: Some("us".into()),
            region: Some("New York".into()),
            tax_type: Some(3),
            rate_scaled: Some(850),
            applies_to_shipping: None,
            threshold_usd: None,
            effective_from: None,
            effective_to: None,
            enabled: None,
            label: None,
        };
        let v = validate(&req).unwrap();
        assert_eq!(v.country_code, "US");
        assert_eq!(v.region, "NY", "州码规范化");
        assert!(v.enabled, "缺省 true");
    }

    #[test]
    fn validate_rate_range() {
        let req = RuleUpsert {
            country_code: Some("US".into()),
            region: None,
            tax_type: Some(3),
            rate_scaled: Some(10001),
            applies_to_shipping: None,
            threshold_usd: None,
            effective_from: None,
            effective_to: None,
            enabled: None,
            label: None,
        };
        let err = validate(&req).unwrap_err();
        assert_eq!(err.details.unwrap()["fields"]["rate_scaled"], "range_invalid");
    }

    #[test]
    fn validate_region_too_long_other_country() {
        let req = RuleUpsert {
            country_code: Some("FR".into()),
            region: Some("TOOLONGREG".into()),
            tax_type: Some(1),
            rate_scaled: Some(2000),
            applies_to_shipping: None,
            threshold_usd: None,
            effective_from: None,
            effective_to: None,
            enabled: None,
            label: None,
        };
        let err = validate(&req).unwrap_err();
        assert_eq!(err.details.unwrap()["fields"]["region"], "too_long");
    }
}
