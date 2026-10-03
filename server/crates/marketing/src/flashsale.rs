//! flashsale 域:限时特卖 admin CRUD(name/discount/窗口/status)+ store 活动列表(E-MKT-09)。

use std::collections::{HashMap, HashSet};

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

/// E-MKT-09 store 活动列表(Java listStoreActive:status=ACTIVE(3) ORDER BY end_at ASC,DEC-MKT-3)。
/// Rust 无 SCHED-MKT-01 翻转任务(admin create 落 status=2),叠加窗口实时判定(CV-MKT-011):
/// SCHEDULED(2) 且窗口进行中一并返回;已结束(4)/草稿(1)/未开始不出现。
/// 商品卡片对齐 CatalogQueryPort.listProductRefs(MAP-MKT-012/CV-MKT-006):仅 published(status=2),
/// 缺失/下架静默剔除,主图=gallery 首图,保持挂载顺序;批查防 N+1(NP-MKT-002)。
pub async fn store_list(db: &DatabaseConnection) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, name, discount, start_at, end_at, status FROM flash_sale \
             WHERE status = 3 OR (status = 2 AND start_at <= NOW(3) AND end_at > NOW(3)) \
             ORDER BY end_at ASC",
        ))
        .await
        .map_err(db_err)?;
    if rows.is_empty() {
        return Ok(vec![]);
    }
    let sale_ids: Vec<i64> = rows
        .iter()
        .map(|r| r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0))
        .collect();
    let id_list = sale_ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
    // flash_sale_product.id ASC 即挂载顺序(RM-MKT-131)
    let mounts_rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT flash_sale_id, product_id FROM flash_sale_product WHERE flash_sale_id IN ({id_list}) ORDER BY id ASC"
            ),
        ))
        .await
        .map_err(db_err)?;
    let mut mounts: HashMap<i64, Vec<i64>> = HashMap::new();
    let mut seen: HashSet<i64> = HashSet::new();
    let mut pid_order: Vec<i64> = vec![];
    for m in &mounts_rows {
        let sid = m.try_get::<u64>("", "flash_sale_id").map(|v| v as i64).unwrap_or(0);
        let pid = m.try_get::<u64>("", "product_id").map(|v| v as i64).unwrap_or(0);
        if seen.insert(pid) {
            pid_order.push(pid);
        }
        mounts.entry(sid).or_default().push(pid);
    }
    // 商品卡片一次批查(主图子查询口径与 cart::assemble_cart 一致)
    let mut cards: HashMap<i64, Value> = HashMap::new();
    if !pid_order.is_empty() {
        let pid_list = pid_order.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
        let prows = db
            .query_all(Statement::from_string(
                DbBackend::MySql,
                format!(
                    "SELECT p.id, p.slug, p.name, p.price, \
                     (SELECT url FROM product_image i WHERE i.product_id = p.id AND i.kind = 1 ORDER BY i.sort ASC, i.id ASC LIMIT 1) AS image_url \
                     FROM product p WHERE p.id IN ({pid_list}) AND p.status = 2"
                ),
            ))
            .await
            .map_err(db_err)?;
        for r in prows {
            let pid = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            cards.insert(
                pid,
                serde_json::json!({
                    "id": pid,
                    "slug": r.try_get::<String>("", "slug").unwrap_or_default(),
                    "name": r.try_get::<String>("", "name").unwrap_or_default(),
                    "price": crate::cart::dec(&r, "price").unwrap_or(0.0),
                    "image_url": r.try_get::<String>("", "image_url").ok(),
                }),
            );
        }
    }
    Ok(rows
        .iter()
        .map(|r| {
            let sid = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            let products = mounts
                .get(&sid)
                .map(|ids| ids.iter().filter_map(|p| cards.get(p).cloned()).collect::<Vec<_>>())
                .unwrap_or_default();
            serde_json::json!({
                "id": sid,
                "name": r.try_get::<String>("", "name").unwrap_or_default(),
                "discount": r.try_get::<String>("", "discount").unwrap_or_default(),
                "start_at": r.try_get::<String>("", "start_at").ok(),
                "end_at": r.try_get::<String>("", "end_at").ok(),
                "products": products,
            })
        })
        .collect())
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
