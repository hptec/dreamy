//! coupon 域:validate(可用性判定固定顺序)+ redeem(CAS 防超发)+ admin CRUD。
//! 对齐 CouponDomainServiceImpl;错误码 404702/422701/422702/422703。

use std::collections::HashMap;

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement, TransactionTrait};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::service_category::CatalogError;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[coupon] db error:{e}");
    CatalogError::new(500701)
}

fn trading(code: i32) -> CatalogError {
    CatalogError::new(code)
}

fn with_detail(mut e: CatalogError, k: &str, v: Value) -> CatalogError {
    let mut m = serde_json::Map::new();
    m.insert(k.into(), v);
    e.details = Some(Value::Object(m));
    e
}

const COUPON_NOT_FOUND: i32 = 404702;
const COUPON_INVALID: i32 = 422701;
const COUPON_MIN_AMOUNT: i32 = 422702;
const COUPON_EXHAUSTED: i32 = 422703;

pub fn http_status_coupon(code: i32) -> u16 {
    match code {
        404702 => 404,
        422701 | 422702 | 422703 => 422,
        _ => 500,
    }
}

/// 可用性判定顺序固定(①不存在/draft/未开始 ②过期 ③耗尽 ④门槛)
fn availability_reason(
    coupon: Option<&Value>,
    subtotal_usd: f64,
    now: &str,
) -> Option<i32> {
    let Some(c) = coupon else {
        return Some(COUPON_INVALID); // 不存在
    };
    let status = c["status"].as_i64().unwrap_or(1);
    if status == 1 || status == 2 {
        return Some(COUPON_INVALID); // draft/scheduled
    }
    let start_at = c["start_at"].as_str().unwrap_or("");
    let end_at = c["end_at"].as_str().unwrap_or("");
    if !start_at.is_empty() && now < start_at {
        return Some(COUPON_INVALID); // 未开始
    }
    if !end_at.is_empty() && now > end_at {
        return Some(COUPON_INVALID); // 已过期
    }
    let used = c["used_count"].as_i64().unwrap_or(0);
    let total = c["total_limit"].as_i64().unwrap_or(0);
    if total > 0 && used >= total {
        return Some(COUPON_EXHAUSTED); // 耗尽优先于门槛
    }
    let min = c["min_amount"].as_f64().unwrap_or(0.0);
    if min > 0.0 && subtotal_usd < min {
        return Some(COUPON_MIN_AMOUNT); // 未达门槛
    }
    None
}

/// 减免计算:DISCOUNT(百分比)/FIXED_AMOUNT(金额取小)/FREE_SHIPPING(0+标记)
fn compute_discount(c_type: i64, value: &str, subtotal_usd: f64) -> Option<(f64, bool)> {
    match c_type {
        1 => {
            let pct: f64 = value.trim().trim_end_matches('%').parse().ok()?;
            Some((round2(subtotal_usd * pct / 100.0), false))
        }
        2 => {
            let amt: f64 = value.parse().ok()?;
            Some((round2(amt.min(subtotal_usd)), false))
        }
        3 => Some((0.0, true)),
        _ => None,
    }
}

fn round2(x: f64) -> f64 {
    (x * 100.0).round() / 100.0
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct CouponQuote {
    pub valid: bool,
    pub coupon_id: Option<i64>,
    pub discount_usd: Option<f64>,
    pub free_shipping: bool,
    pub reason_code: Option<i32>,
}

/// validate(E-MKT-10 试算;quote 口径无效不阻断)
pub async fn validate(
    db: &DatabaseConnection,
    code: &str,
    subtotal_usd: f64,
) -> Result<CouponQuote, CatalogError> {
    let normalized = code.trim().to_uppercase();
    let row = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, code, name, type, value, min_amount, total_limit, used_count, start_at, end_at, status FROM coupon WHERE code = ?",
            [normalized.into()],
        ))
        .await
        .map_err(db_err)?;
    let coupon: Option<Value> = row.map(|r| {
        serde_json::json!({
            "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
            "type": r.try_get::<i64>("", "type").unwrap_or(1),
            "value": r.try_get::<String>("", "value").unwrap_or_default(),
            "min_amount": crate::cart::dec(&r, "min_amount").unwrap_or(0.0),
            "total_limit": r.try_get::<i64>("", "total_limit").unwrap_or(0),
            "used_count": r.try_get::<i64>("", "used_count").unwrap_or(0),
            "start_at": r.try_get::<String>("", "start_at").ok(),
            "end_at": r.try_get::<String>("", "end_at").ok(),
            "status": r.try_get::<i64>("", "status").unwrap_or(1),
        })
    });
    let now = chrono::Local::now().format("%Y-%m-%d %H:%M:%S").to_string();
    if let Some(reason) = availability_reason(coupon.as_ref(), subtotal_usd, &now) {
        return Ok(CouponQuote {
            valid: false,
            coupon_id: coupon.as_ref().and_then(|c| c["id"].as_i64()),
            discount_usd: None,
            free_shipping: false,
            reason_code: Some(reason),
        });
    }
    let Some(c) = coupon else {
        return Ok(CouponQuote { valid: false, coupon_id: None, discount_usd: None, free_shipping: false, reason_code: Some(COUPON_INVALID) });
    };
    let c_type = c["type"].as_i64().unwrap_or(1);
    let value = c["value"].as_str().unwrap_or_default();
    let Some((discount, free_shipping)) = compute_discount(c_type, value, subtotal_usd) else {
        return Ok(CouponQuote { valid: false, coupon_id: c["id"].as_i64(), discount_usd: None, free_shipping: false, reason_code: Some(COUPON_INVALID) });
    };
    Ok(CouponQuote {
        valid: true,
        coupon_id: c["id"].as_i64(),
        discount_usd: Some(discount),
        free_shipping,
        reason_code: None,
    })
}

/// redeem(下单事务内 CAS;防 TOCTOU 复跑判定)
pub async fn redeem(
    db: &DatabaseConnection,
    code: &str,
    subtotal_usd: f64,
) -> Result<i64, CatalogError> {
    let normalized = code.trim().to_uppercase();
    let row = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, type, value, min_amount, total_limit, used_count, start_at, end_at, status FROM coupon WHERE code = ?",
            [normalized.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(COUPON_INVALID))?;
    let coupon = serde_json::json!({
        "id": row.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "type": row.try_get::<i64>("", "type").unwrap_or(1),
        "value": row.try_get::<String>("", "value").unwrap_or_default(),
        "min_amount": crate::cart::dec(&row, "min_amount").unwrap_or(0.0),
        "total_limit": row.try_get::<i64>("", "total_limit").unwrap_or(0),
        "used_count": row.try_get::<i64>("", "used_count").unwrap_or(0),
        "start_at": row.try_get::<String>("", "start_at").ok(),
        "end_at": row.try_get::<String>("", "end_at").ok(),
        "status": row.try_get::<i64>("", "status").unwrap_or(1),
    });
    let now = chrono::Local::now().format("%Y-%m-%d %H:%M:%S").to_string();
    if let Some(reason) = availability_reason(Some(&coupon), subtotal_usd, &now) {
        return Err(trading(reason));
    }
    // RM-MKT-107 CAS:used_count < total_limit 才递增
    let coupon_id = coupon["id"].as_i64().unwrap_or(0);
    let res = db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "UPDATE coupon SET used_count = used_count + 1, updated_at = NOW(3) WHERE id = ? AND (total_limit = 0 OR used_count < total_limit)",
            [coupon_id.into()],
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(trading(COUPON_EXHAUSTED));
    }
    Ok(coupon_id)
}

pub async fn rollback_redeem(db: &DatabaseConnection, coupon_id: i64) -> Result<(), CatalogError> {
    if coupon_id == 0 {
        return Ok(());
    }
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE coupon SET used_count = GREATEST(used_count - 1, 0), updated_at = NOW(3) WHERE id = ?",
        [coupon_id.into()],
    ))
    .await
    .map_err(db_err)?;
    Ok(())
}

/// admin 列表
pub async fn admin_list(db: &DatabaseConnection) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, code, name, type, value, min_amount, total_limit, used_count, start_at, end_at, status FROM coupon ORDER BY id DESC",
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            serde_json::json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "code": r.try_get::<String>("", "code").unwrap_or_default(),
                "name": r.try_get::<String>("", "name").unwrap_or_default(),
                "type": r.try_get::<i64>("", "type").unwrap_or(1),
                "value": r.try_get::<String>("", "value").ok(),
                "min_amount": crate::cart::dec(r, "min_amount").unwrap_or(0.0),
                "total_limit": r.try_get::<i64>("", "total_limit").unwrap_or(0),
                "used_count": r.try_get::<i64>("", "used_count").unwrap_or(0),
                "start_at": r.try_get::<String>("", "start_at").ok(),
                "end_at": r.try_get::<String>("", "end_at").ok(),
                "status": r.try_get::<i64>("", "status").unwrap_or(1),
            })
        })
        .collect())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn coupon_json(status: i64, used: i64, total: i64, min: f64, start: &str, end: &str) -> Value {
        serde_json::json!({
            "id": 1, "type": 1, "value": "10%", "min_amount": min,
            "total_limit": total, "used_count": used,
            "start_at": start, "end_at": end, "status": status,
        })
    }

    #[test]
    fn availability_order_fixed() {
        let now = "2026-09-22 12:00:00";
        // ① draft
        assert_eq!(availability_reason(Some(&coupon_json(1, 0, 100, 0.0, "", "")), 100.0, now), Some(COUPON_INVALID));
        // ② 过期
        assert_eq!(availability_reason(Some(&coupon_json(3, 0, 100, 0.0, "2026-01-01 00:00:00", "2026-08-01 00:00:00")), 100.0, now), Some(COUPON_INVALID));
        // ③ 耗尽优先于门槛
        assert_eq!(availability_reason(Some(&coupon_json(3, 100, 100, 500.0, "", "")), 100.0, now), Some(COUPON_EXHAUSTED));
        // ④ 门槛
        assert_eq!(availability_reason(Some(&coupon_json(3, 0, 100, 500.0, "", "")), 100.0, now), Some(COUPON_MIN_AMOUNT));
        // 通过
        assert_eq!(availability_reason(Some(&coupon_json(3, 50, 100, 500.0, "", "")), 600.0, now), None);
    }

    #[test]
    fn discount_calculation_by_type() {
        assert_eq!(compute_discount(1, "10%", 1280.0), Some((128.0, false)));
        assert_eq!(compute_discount(2, "50.00", 100.0), Some((50.0, false)), "FIXED 取小");
        assert_eq!(compute_discount(2, "50.00", 30.0), Some((30.0, false)));
        assert_eq!(compute_discount(3, "", 100.0), Some((0.0, true)));
    }
}
