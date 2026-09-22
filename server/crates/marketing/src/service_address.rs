//! 地址簿服务(对齐 Java AddressService;trading-api-detail §2,TASK-015)。
//! TX-TRD-008 默认地址切换:clearDefault + insert/update 同事务,「恒至多一个 is_default」;
//! 首条地址强制默认;删除不波及既有订单(address_snapshot 快照)。
//! V-TRD-011/012 校验 + country_code 目录匹配 + US/CA/AU region 规范化(422601 字段级)。

use sea_orm::{
    ActiveModelTrait, ColumnTrait, ConnectionTrait, DatabaseConnection, DatabaseTransaction,
    DbBackend, EntityTrait, PaginatorTrait, QueryFilter, QueryOrder, QueryTrait, Set,
    TransactionTrait,
};
use serde::{Deserialize, Serialize};

use crate::entity_trading::address;
use crate::trading_error::TradingError;

/// V-TRD-011/012 字段级收集(details = { fields: {...} };对齐 TradingFieldErrors)
#[derive(Default)]
pub struct FieldErrors {
    fields: Vec<(&'static str, &'static str)>,
}

impl FieldErrors {
    pub fn reject(&mut self, field: &'static str, reason: &'static str) {
        if !self.fields.iter().any(|(f, _)| *f == field) {
            self.fields.push((field, reason));
        }
    }
    pub fn throw_if_any(self) -> Result<(), TradingError> {
        if self.fields.is_empty() {
            return Ok(());
        }
        let map: serde_json::Map<String, serde_json::Value> = self
            .fields
            .iter()
            .map(|(f, r)| (f.to_string(), serde_json::json!(r)))
            .collect();
        Err(TradingError {
            code: 422601,
            details: Some(serde_json::json!({ "fields": map })),
        })
    }
}

fn require_text(v: Option<&str>, max: usize, field: &'static str, errors: &mut FieldErrors) -> Option<String> {
    let t = v.map(str::trim).filter(|s| !s.is_empty());
    match t {
        None => {
            errors.reject(field, "required");
            None
        }
        Some(s) if s.len() > max => {
            errors.reject(field, "too_long");
            None
        }
        Some(s) => Some(s.to_string()),
    }
}

fn check_max(v: Option<&str>, max: usize, field: &'static str, errors: &mut FieldErrors) -> Option<String> {
    match v.map(str::trim).filter(|s| !s.is_empty()) {
        Some(s) if s.len() > max => {
            errors.reject(field, "too_long");
            None
        }
        other => other.map(String::from),
    }
}

/// AddressUpsert(snake_case 输入;对齐 Java record 字段)
#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct AddressUpsert {
    pub receiver: Option<String>,
    pub phone: Option<String>,
    pub line: Option<String>,
    pub city: Option<String>,
    pub state: Option<String>,
    pub zip: Option<String>,
    pub country: Option<String>,
    pub country_code: Option<String>,
    pub region_code: Option<String>,
    pub is_default: Option<bool>,
}

/// 解析结果(country, country_code, region_code)
#[derive(Debug)]
struct Resolved {
    country: String,
    country_code: Option<String>,
    region_code: Option<String>,
}

fn validate(request: &AddressUpsert) -> Result<Resolved, TradingError> {
    use common::country_catalog as cc;
    let mut errors = FieldErrors::default();
    require_text(request.receiver.as_deref(), 64, "receiver", &mut errors);
    check_max(request.phone.as_deref(), 32, "phone", &mut errors);
    require_text(request.line.as_deref(), 255, "line", &mut errors);
    require_text(request.city.as_deref(), 64, "city", &mut errors);
    check_max(request.state.as_deref(), 64, "state", &mut errors);
    require_text(request.zip.as_deref(), 16, "zip", &mut errors);

    let country_text = request.country.as_deref().map(str::trim).filter(|s| !s.is_empty());
    let mut country_code: Option<String> = request
        .country_code
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(String::from);
    if let Some(code) = country_code.as_deref() {
        let up = code.to_uppercase();
        if !cc::is_known_code(&up) {
            errors.reject("country_code", "invalid_enum");
        }
        country_code = Some(up);
    } else if let Some(text) = country_text {
        match cc::resolve_code(text) {
            Some(c) => country_code = Some(c.to_string()),
            None => errors.reject("country_code", "required"),
        }
    }
    let country_text = match country_text {
        None => match country_code.as_deref().and_then(cc::by_code) {
            Some(k) => k.name.to_string(),
            None => {
                errors.reject("country", "required");
                String::new()
            }
        },
        Some(t) if t.len() > 64 => {
            errors.reject("country", "too_long");
            t.to_string()
        }
        Some(t) => t.to_string(),
    };
    let mut region_code: Option<String> = None;
    if let Some(code) = country_code.as_deref() {
        if cc::has_regions(code) {
            let region_input = request
                .region_code
                .as_deref()
                .map(str::trim)
                .filter(|s| !s.is_empty())
                .or_else(|| request.state.as_deref().map(str::trim).filter(|s| !s.is_empty()));
            if let Some(input) = region_input {
                region_code = cc::resolve_region_code(code, input).map(String::from);
                if region_code.is_none() {
                    errors.reject("region_code", "invalid_enum");
                }
            }
        }
    }
    errors.throw_if_any()?;
    Ok(Resolved {
        country: country_text,
        country_code,
        region_code,
    })
}

/// AddressDto(snake_case 输出;对齐 Java AddressDto 字段序)
#[derive(Debug, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct AddressDto {
    pub id: u64,
    pub receiver: String,
    pub phone: Option<String>,
    pub line: String,
    pub city: String,
    pub state: Option<String>,
    pub zip: String,
    pub country: String,
    pub is_default: bool,
    pub country_code: Option<String>,
    pub region_code: Option<String>,
}

fn toDto(a: &address::Model) -> AddressDto {
    AddressDto {
        id: a.id,
        receiver: a.receiver.clone(),
        phone: a.phone.clone(),
        line: a.line.clone(),
        city: a.city.clone(),
        state: a.state.clone(),
        zip: a.zip.clone(),
        country: a.country.clone(),
        is_default: a.is_default,
        country_code: a.country_code.clone(),
        region_code: a.region_code.clone(),
    }
}

/// E-listAddresses
pub async fn list(db: &DatabaseConnection, customer_id: i64) -> Result<Vec<AddressDto>, TradingError> {
    let rows = address::Entity::find()
        .filter(address::Column::CustomerId.eq(customer_id))
        .order_by_desc(address::Column::Id)
        .all(db)
        .await
        .map_err(db_err)?;
    Ok(rows.iter().map(toDto).collect())
}

async fn clear_default(tx: &DatabaseTransaction, customer_id: i64) -> Result<(), TradingError> {
    tx.execute(sea_orm::Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE address SET is_default = 0 WHERE customer_id = ? AND is_default = 1",
        [customer_id.into()],
    ))
    .await
    .map_err(db_err)?;
    Ok(())
}

fn db_err(e: sea_orm::DbErr) -> TradingError {
    tracing::error!("[trd] address db error:{e}");
    TradingError::new(500601)
}

/// E-createAddress(201;TX-TRD-008 同事务)
pub async fn create(
    db: &DatabaseConnection,
    customer_id: i64,
    request: AddressUpsert,
) -> Result<AddressDto, TradingError> {
    let resolved = validate(&request)?;
    let tx = db.begin().await.map_err(db_err)?;
    let first_address = address::Entity::find()
        .filter(address::Column::CustomerId.eq(customer_id))
        .count(&tx)
        .await
        .map_err(db_err)?
        == 0;
    let is_default = first_address || request.is_default == Some(true);
    if is_default {
        clear_default(&tx, customer_id).await?;
    }
    let now = chrono::Local::now().naive_local();
    let row = address::ActiveModel {
        customer_id: Set(customer_id),
        receiver: Set(request.receiver.as_deref().map(str::trim).unwrap_or_default().to_string()),
        phone: Set(request.phone.as_deref().map(str::trim).filter(|s| !s.is_empty()).map(String::from)),
        line: Set(request.line.as_deref().map(str::trim).unwrap_or_default().to_string()),
        city: Set(request.city.as_deref().map(str::trim).unwrap_or_default().to_string()),
        state: Set(request.state.as_deref().map(str::trim).filter(|s| !s.is_empty()).map(String::from)),
        zip: Set(request.zip.as_deref().map(str::trim).unwrap_or_default().to_string()),
        country: Set(resolved.country.clone()),
        country_code: Set(resolved.country_code.clone()),
        region_code: Set(resolved.region_code.clone()),
        is_default: Set(is_default),
        created_at: Set(now),
        updated_at: Set(now),
        ..Default::default()
    };
    let inserted = row.insert(&tx).await.map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    let mut dto = toDto(&inserted);
    dto.is_default = is_default;
    Ok(dto)
}

/// E-updateAddress(404602 不存在;TX-TRD-008)
pub async fn update(
    db: &DatabaseConnection,
    customer_id: i64,
    id: u64,
    request: AddressUpsert,
) -> Result<AddressDto, TradingError> {
    let resolved = validate(&request)?;
    let existing = address::Entity::find()
        .filter(address::Column::Id.eq(id))
        .filter(address::Column::CustomerId.eq(customer_id))
        .one(db)
        .await
        .map_err(db_err)?
        .ok_or(TradingError::new(crate::trading_error::ADDRESS_NOT_FOUND))?;
    let tx = db.begin().await.map_err(db_err)?;
    let is_default = request.is_default == Some(true);
    if is_default {
        clear_default(&tx, customer_id).await?;
    }
    let mut am: address::ActiveModel = existing.into();
    am.receiver = Set(request.receiver.as_deref().map(str::trim).unwrap_or_default().to_string());
    am.phone = Set(request.phone.as_deref().map(str::trim).filter(|s| !s.is_empty()).map(String::from));
    am.line = Set(request.line.as_deref().map(str::trim).unwrap_or_default().to_string());
    am.city = Set(request.city.as_deref().map(str::trim).unwrap_or_default().to_string());
    am.state = Set(request.state.as_deref().map(str::trim).filter(|s| !s.is_empty()).map(String::from));
    am.zip = Set(request.zip.as_deref().map(str::trim).unwrap_or_default().to_string());
    am.country = Set(resolved.country);
    am.country_code = Set(resolved.country_code);
    am.region_code = Set(resolved.region_code);
    am.is_default = Set(is_default);
    am.updated_at = Set(chrono::Local::now().naive_local());
    let saved = am.update(&tx).await.map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    Ok(toDto(&saved))
}

/// E-deleteAddress(affected=0 → 404602;删除默认地址不自动指定新默认)
pub async fn delete(db: &DatabaseConnection, customer_id: i64, id: u64) -> Result<(), TradingError> {
    let res = db
        .execute(sea_orm::Statement::from_sql_and_values(
            DbBackend::MySql,
            "DELETE FROM address WHERE id = ? AND customer_id = ?",
            [id.into(), customer_id.into()],
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(TradingError::new(crate::trading_error::ADDRESS_NOT_FOUND));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn base() -> AddressUpsert {
        serde_json::from_value(serde_json::json!({
            "receiver": "Emma", "line": "1 Main St", "city": "Springfield",
            "zip": "12345", "country": "United States"
        })).unwrap()
    }

    #[tokio::test]
    async fn validate_resolves_country_and_region() {
        let mut req = base();
        req.state = Some("New York".into());
        let r = validate(&req).unwrap();
        assert_eq!(r.country_code.as_deref(), Some("US"));
        assert_eq!(r.region_code.as_deref(), Some("NY"));
        assert_eq!(r.country, "United States");
    }

    #[tokio::test]
    async fn validate_explicit_code_and_region_alias() {
        let mut req = base();
        req.country_code = Some("au".into());
        req.region_code = Some("Canberra".into());
        let r = validate(&req).unwrap();
        assert_eq!(r.country_code.as_deref(), Some("AU"));
        assert_eq!(r.region_code.as_deref(), Some("ACT"));
    }

    #[tokio::test]
    async fn validate_unknown_country_rejects() {
        let mut req = base();
        req.country = Some("Atlantis".into());
        let err = validate(&req).unwrap_err();
        assert_eq!(err.code, 422601);
        let f = err.details.unwrap()["fields"].clone();
        assert_eq!(f["country_code"], "required");
    }

    #[tokio::test]
    async fn validate_bad_region_rejects() {
        let mut req = base();
        req.country_code = Some("US".into());
        req.region_code = Some("Atlantis".into());
        let err = validate(&req).unwrap_err();
        assert_eq!(err.details.unwrap()["fields"]["region_code"], "invalid_enum");
    }

    #[tokio::test]
    async fn validate_missing_required_collects_all() {
        let req: AddressUpsert = serde_json::from_value(serde_json::json!({})).unwrap();
        let err = validate(&req).unwrap_err();
        let f = err.details.unwrap()["fields"].clone();
        for k in ["receiver", "line", "city", "zip", "country"] {
            assert_eq!(f[k], "required", "{k}");
        }
    }

    #[tokio::test]
    async fn validate_country_from_code_when_text_missing() {
        let mut req = base();
        req.country = None;
        req.country_code = Some("JP".into());
        let r = validate(&req).unwrap();
        assert_eq!(r.country, "Japan");
        assert_eq!(r.country_code.as_deref(), Some("JP"));
        assert_eq!(r.region_code, None, "JP 无州省字典,留空");
    }
}
