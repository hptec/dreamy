//! dashboard/analytics 域:KPI 汇总(read model 直查)。

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement};
use serde_json::{json, Value};

use crate::service_category::CatalogError;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[ana] db error:{e}");
    CatalogError::new(500601)
}

/// E-ANA-01 getAdminDashboard(订单 KPI + GMV 趋势)
pub async fn dashboard(db: &DatabaseConnection) -> Result<Value, CatalogError> {
    // KPI:今日/累计订单与 GMV(STATUS != 5 已取消剔除)
    let kpi = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            "SELECT \
              COUNT(*) AS total_orders, \
              COALESCE(SUM(total_amount), 0) AS total_gmv, \
              COALESCE(SUM(CASE WHEN DATE(created_at) = CURDATE() THEN 1 ELSE 0 END), 0) AS today_orders, \
              COALESCE(SUM(CASE WHEN DATE(created_at) = CURDATE() THEN total_amount ELSE 0 END), 0) AS today_gmv, \
              COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS pending_orders, \
              COALESCE(SUM(CASE WHEN status IN (2, 3) THEN 1 ELSE 0 END), 0) AS to_ship_orders \
             FROM orders WHERE status != 5",
        ))
        .await
        .map_err(db_err)?
        .ok_or(CatalogError::new(500601))?;
    // 近 7 日 GMV 趋势
    let trend_rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT DATE(created_at) AS d, COALESCE(SUM(total_amount), 0) AS gmv, COUNT(*) AS n \
             FROM orders WHERE status != 5 AND created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) \
             GROUP BY DATE(created_at) ORDER BY d ASC",
        ))
        .await
        .map_err(db_err)?;
    Ok(json!({
        "kpis": {
            "total_orders": kpi.try_get::<i64>("", "total_orders").unwrap_or(0),
            "total_gmv": crate::cart::dec(&kpi, "total_gmv").unwrap_or(0.0),
            "today_orders": kpi.try_get::<i64>("", "today_orders").unwrap_or(0),
            "today_gmv": crate::cart::dec(&kpi, "today_gmv").unwrap_or(0.0),
            "pending_orders": kpi.try_get::<i64>("", "pending_orders").unwrap_or(0),
            "to_ship_orders": kpi.try_get::<i64>("", "to_ship_orders").unwrap_or(0),
        },
        "gmv_trend": trend_rows.iter().map(|r| json!({
            "date": r.try_get::<String>("", "d").ok(),
            "gmv": crate::cart::dec(r, "gmv").unwrap_or(0.0),
            "orders": r.try_get::<i64>("", "n").unwrap_or(0),
        })).collect::<Vec<_>>(),
    }))
}

/// E-ANA-02 overview(分类销量 + top 商品)
pub async fn overview(db: &DatabaseConnection) -> Result<Value, CatalogError> {
    let cats = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT p.category_id AS cid, COALESCE(SUM(ol.qty * ol.unit_price), 0) AS amount \
             FROM order_line ol JOIN product p ON p.id = ol.product_id \
             GROUP BY p.category_id ORDER BY amount DESC LIMIT 10",
        ))
        .await
        .map_err(db_err)?;
    let tops = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT ol.product_id AS pid, ol.product_name AS name, SUM(ol.qty) AS sales, \
             COALESCE(SUM(ol.qty * ol.unit_price), 0) AS amount \
             FROM order_line ol GROUP BY ol.product_id, ol.product_name ORDER BY sales DESC LIMIT 10",
        ))
        .await
        .map_err(db_err)?;
    Ok(json!({
        "category_sales": cats.iter().map(|r| json!({
            "category_id": r.try_get::<i64>("", "cid").unwrap_or(0),
            "amount": crate::cart::dec(r, "amount").unwrap_or(0.0),
        })).collect::<Vec<_>>(),
        "top_products": tops.iter().map(|r| json!({
            "product_id": r.try_get::<i64>("", "pid").unwrap_or(0),
            "name": r.try_get::<String>("", "name").unwrap_or_default(),
            "sales": r.try_get::<i64>("", "sales").unwrap_or(0),
            "amount": crate::cart::dec(r, "amount").unwrap_or(0.0),
        })).collect::<Vec<_>>(),
    }))
}

/// E-ANA-03 traffic(browse_history 聚合;表可空时返回空)
pub async fn traffic(db: &DatabaseConnection) -> Result<Value, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT DATE(created_at) AS d, COUNT(*) AS pv, COUNT(DISTINCT customer_id) AS uv \
             FROM browse_history WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) \
             GROUP BY DATE(created_at) ORDER BY d ASC",
        ))
        .await;
    match rows {
        Err(_) => Ok(json!({ "daily": [] })),
        Ok(rows) => Ok(json!({
            "daily": rows.iter().map(|r| json!({
                "date": r.try_get::<String>("", "d").ok(),
                "pv": r.try_get::<i64>("", "pv").unwrap_or(0),
                "uv": r.try_get::<i64>("", "uv").unwrap_or(0),
            })).collect::<Vec<_>>(),
        })),
    }
}
