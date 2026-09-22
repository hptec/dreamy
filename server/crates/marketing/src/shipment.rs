//! shipment 域:发货单(admin 创建/发货/事件/签收/取消)+ 游客查单。
//! 对齐 AdminShipmentService;ShipmentStatus 1..6;错误码 404907。

use std::collections::HashMap;

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement, TransactionTrait};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::service_category::CatalogError;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[ship] db error:{e}");
    CatalogError::new(500601)
}

pub fn not_found() -> CatalogError {
    CatalogError::new(404907)
}

pub fn trading(code: i32) -> CatalogError {
    CatalogError::new(code)
}

pub fn field_err(fields: Vec<(&'static str, &'static str)>) -> CatalogError {
    CatalogError::field_validation(&fields)
}

/// 对齐 Java nextStatus:DELIVERED(4)/CANCELLED(6) 终态;EXCEPTION(5) 可从任意非终态进入/恢复;
/// 其余按 rank 单调(rank: 1<2<3<4),target 不高于 current → 停留 current
fn next_status(current: i64, target: i64) -> i64 {
    if current == 4 || current == 6 {
        return current;
    }
    if target == 5 {
        return 5;
    }
    if current == 5 {
        return target;
    }
    let rank = |s: i64| match s {
        1 => 1,
        2 => 2,
        3 => 3,
        4 => 4,
        _ => 0,
    };
    if rank(target) > rank(current) {
        target
    } else {
        current
    }
}

async fn audit(audit_db: &DatabaseConnection, operator: &str, action: &str, target: &str) {
    let oid: Option<i64> = operator.parse().ok();
    if let Err(e) = audit_db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO operation_log(operator_id, operator_name, action, target, ip, user_agent, changes, created_at, updated_at) VALUES (?,NULL,?,?,NULL,NULL,NULL,NOW(3),NOW(3))",
            [oid.into(), action.into(), target.into()],
        ))
        .await
    {
        tracing::warn!("[ship] audit best-effort failed:{e}");
    }
}

async fn inserted_id(tx: &sea_orm::DatabaseTransaction) -> Result<u64, CatalogError> {
    tx.query_one(Statement::from_string(
        DbBackend::MySql,
        "SELECT LAST_INSERT_ID() AS id".to_string(),
    ))
    .await
    .map_err(db_err)?
    .and_then(|r| r.try_get::<u64>("", "id").ok())
    .ok_or(CatalogError::new(500601))
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct ShipmentLineIn {
    pub order_line_id: Option<i64>,
    pub qty: Option<i64>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct ShipmentCreate {
    pub carrier_code: Option<String>,
    pub tracking_no: Option<String>,
    pub lines: Option<Vec<ShipmentLineIn>>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct ShipmentDto {
    pub id: i64,
    pub shipment_no: String,
    pub carrier_code: String,
    pub carrier_name: String,
    pub tracking_no: Option<String>,
    pub status: i64,
    pub shipped_at: Option<String>,
    pub delivered_at: Option<String>,
    pub last_event_at: Option<String>,
    pub last_event_desc: Option<String>,
    pub lines: Vec<Value>,
    pub events: Vec<Value>,
}

fn row_to_dto(r: &sea_orm::QueryResult, lines: Vec<Value>, events: Vec<Value>) -> Value {
    serde_json::json!({
        "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "shipment_no": r.try_get::<String>("", "shipment_no").unwrap_or_default(),
        "carrier_code": r.try_get::<String>("", "carrier_code").unwrap_or_default(),
        "carrier_name": r.try_get::<String>("", "carrier_name").unwrap_or_default(),
        "tracking_no": r.try_get::<String>("", "tracking_no").ok(),
        "tracking_url": r.try_get::<String>("", "tracking_url").ok(),
        "status": r.try_get::<i64>("", "status").unwrap_or(1),
        "shipped_at": r.try_get::<String>("", "shipped_at").ok(),
        "delivered_at": r.try_get::<String>("", "delivered_at").ok(),
        "last_event_at": r.try_get::<String>("", "last_event_at").ok(),
        "last_event_desc": r.try_get::<String>("", "last_event_desc").ok(),
        "lines": lines,
        "events": events,
    })
}

async fn load_lines(db: &DatabaseConnection, shipment_id: i64) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT olp.product_name, olp.sku_code, sl.qty FROM shipment_line sl JOIN order_line olp ON olp.id = sl.order_line_id WHERE sl.shipment_id = {}",
                shipment_id
            ),
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            serde_json::json!({
                "product_name": r.try_get::<String>("", "product_name").unwrap_or_default(),
                "sku_code": r.try_get::<String>("", "sku_code").ok(),
                "qty": r.try_get::<i64>("", "qty").unwrap_or(0),
            })
        })
        .collect())
}

async fn load_events(db: &DatabaseConnection, shipment_id: i64) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT occurred_at, status, location, description FROM shipment_event WHERE shipment_id = {} ORDER BY occurred_at DESC",
                shipment_id
            ),
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            serde_json::json!({
                "occurred_at": r.try_get::<String>("", "occurred_at").ok(),
                "status": r.try_get::<i64>("", "status").unwrap_or(1),
                "location": r.try_get::<String>("", "location").ok(),
                "description": r.try_get::<String>("", "description").ok(),
            })
        })
        .collect())
}

async fn find_dto(db: &DatabaseConnection, id: i64) -> Result<Value, CatalogError> {
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT id, shipment_no, carrier_code, carrier_name, tracking_no, tracking_url, status, shipped_at, delivered_at, last_event_at, last_event_desc FROM shipment WHERE id = {}",
                id
            ),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    let lines = load_lines(db, id).await?;
    let events = load_events(db, id).await?;
    Ok(row_to_dto(&row, lines, events))
}

/// E-adminCreateShipment(TX:INSERT shipment + lines;订单 SHIPPED)
pub async fn create(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    order_id: i64,
    req: ShipmentCreate,
    operator: &str,
) -> Result<Value, CatalogError> {
    let carrier_code = req
        .carrier_code
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .ok_or_else(|| field_err(vec![("carrier_code", "required")]))?;
    let tracking_no = req.tracking_no.as_deref().map(str::trim).filter(|s| !s.is_empty());
    let lines = req.lines.unwrap_or_default();
    if lines.is_empty() {
        return Err(field_err(vec![("lines", "required")]));
    }
    // 订单存在且 PAID/可发货
    let order = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, order_no, status FROM orders WHERE id = ?",
            [order_id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(not_found())?;
    let order_status: i64 = order.try_get("", "status").unwrap_or(1);
    let order_no: String = order.try_get("", "order_no").unwrap_or_default();
    if !matches!(order_status, 2 | 6) {
        return Err(trading(409703).with_detail("reason", Value::from("illegal_transition")));
    }
    // carrier 存在
    let carrier = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT name FROM carrier WHERE code = ?",
            [carrier_code.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(|| field_err(vec![("carrier_code", "invalid_enum")]))?;
    let carrier_name: String = carrier.try_get("", "name").unwrap_or_default();
    let tx = db.begin().await.map_err(db_err)?;
    let shipment_no = format!("SHP-{}", uuid::Uuid::new_v4().simple().to_string()[..12].to_string());
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO shipment(order_id, shipment_no, carrier_code, carrier_name, tracking_no, status, shipped_at, created_at, updated_at) VALUES (?,?,?,?,?,2,NOW(3),NOW(3),NOW(3))",
        [
            order_id.into(),
            shipment_no.clone().into(),
            carrier_code.into(),
            carrier_name.clone().into(),
            tracking_no.clone().into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    let id = inserted_id(&tx).await?;
    for l in &lines {
        tx.execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO shipment_line(shipment_id, order_line_id, qty, created_at, updated_at) VALUES (?,?,?,NOW(3),NOW(3))",
            [
                (id as i64).into(),
                l.order_line_id.unwrap_or(0).into(),
                l.qty.unwrap_or(0).into(),
            ],
        ))
        .await
        .map_err(db_err)?;
    }
    // 订单 SHIPPED(2→3)+ shipped_at
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE orders SET status = 3, shipped_at = NOW(3), updated_at = NOW(3) WHERE id = ? AND status IN (2, 6)",
        [order_id.into()],
    ))
    .await
    .map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO order_event(order_id, type, actor_type, actor_id, title, detail, payload, customer_visible, created_at, updated_at) VALUES (?, 3, 3, NULL, 'Order shipped', ?, ?, 1, NOW(3), NOW(3))",
        [
            order_id.into(),
            format!("carrier {} tracking {}", carrier_code, tracking_no.unwrap_or_default()).into(),
            serde_json::json!({"from": 2, "to": 3}).to_string().into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "创建发货单", &shipment_no).await;
    find_dto(db, id as i64).await
}

/// E-adminPatchShipment(carrier/tracking 修正)
pub async fn patch(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    carrier_code: Option<String>,
    tracking_no: Option<String>,
    operator: &str,
) -> Result<Value, CatalogError> {
    let existing = find_dto(db, id).await?;
    let sets: Vec<String> = [
        carrier_code.as_ref().map(|_| "carrier_code = ?".to_string()),
        tracking_no.as_ref().map(|_| "tracking_no = ?".to_string()),
    ]
    .into_iter()
    .flatten()
    .collect();
    if sets.is_empty() {
        return Ok(existing);
    }
    let sql = format!(
        "UPDATE shipment SET {}, updated_at = NOW(3) WHERE id = {}",
        sets.join(", "), id
    );
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        sql,
        [
            carrier_code.clone().into(),
            tracking_no.clone().into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    audit(audit_db, operator, "修正发货单", &existing["shipment_no"].as_str().unwrap_or("")).await;
    find_dto(db, id).await
}

/// E-adminAddShipmentEvent(状态流转 + last_event)
pub async fn add_event(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    status: Option<i64>,
    occurred_at: Option<String>,
    location: Option<String>,
    description: Option<String>,
    operator: &str,
) -> Result<Value, CatalogError> {
    let Some(new_status) = status.filter(|s| (1..=6).contains(s)) else {
        return Err(field_err(vec![("status", "invalid_enum")]));
    };
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT id, status, shipment_no FROM shipment WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    let cur: i64 = row.try_get("", "status").unwrap_or(1);
    let shipment_no: String = row.try_get("", "shipment_no").unwrap_or_default();
    let next = next_status(cur, new_status);
    let _ = next; // 单调推进:target 不高于 current 时停留 current(幂等)
    let occurred = occurred_at.unwrap_or_else(|| chrono::Local::now().format("%Y-%m-%d %H:%M:%S%.3f").to_string());
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO shipment_event(shipment_id, occurred_at, status, location, description, source, created_at, updated_at) VALUES (?,?,?,?,?,1,NOW(3),NOW(3))",
        [
            id.into(),
            occurred.clone().into(),
            new_status.into(),
            location.clone().into(),
            description.clone().into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE shipment SET status = ?, last_event_at = ?, last_event_desc = ?, updated_at = NOW(3) WHERE id = ?",
        [new_status.into(), occurred.clone().into(), description.clone().into(), id.into()],
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "添加物流事件", &shipment_no).await;
    find_dto(db, id).await
}

/// E-adminDeliverShipment(DELIVERED;订单 COMPLETED 走 auto_complete)
pub async fn deliver(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    operator: &str,
) -> Result<Value, CatalogError> {
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT id, shipment_no, order_id, status FROM shipment WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    let cur: i64 = row.try_get("", "status").unwrap_or(1);
    let order_id = row.try_get::<u64>("", "order_id").map(|v| v as i64).unwrap_or(0);
    let shipment_no: String = row.try_get("", "shipment_no").unwrap_or_default();
    if cur == 4 {
        // 幂等:已签收直接返回
        return find_dto(db, id).await;
    }
    if !matches!(cur, 1 | 2 | 3 | 5) {
        return Err(trading(409703).with_detail("reason", Value::from("illegal_transition")));
    }
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE shipment SET status = 4, delivered_at = NOW(3), updated_at = NOW(3) WHERE id = ?",
        [id.into()],
    ))
    .await
    .map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE orders SET delivered_at = NOW(3), updated_at = NOW(3) WHERE id = ?",
        [order_id.into()],
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "签收发货单", &shipment_no).await;
    find_dto(db, id).await
}

/// E-adminCancelShipment(仅未发货/未签收)
pub async fn cancel(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    operator: &str,
) -> Result<Value, CatalogError> {
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT id, shipment_no, status FROM shipment WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    let cur: i64 = row.try_get("", "status").unwrap_or(1);
    let shipment_no: String = row.try_get("", "shipment_no").unwrap_or_default();
    if cur == 4 || cur == 6 {
        return Err(trading(409703).with_detail("reason", Value::from("illegal_transition")));
    }
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE shipment SET status = 6, updated_at = NOW(3) WHERE id = ?",
        [id.into()],
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "作废发货单", &shipment_no).await;
    find_dto(db, id).await
}

/// 游客查单(订单号+邮箱;脱敏)。user 表在主库,orders/shipment 在业务库——跨库两步查询。
pub async fn track(
    db: &DatabaseConnection,
    main_db: &DatabaseConnection,
    order_no: &str,
    email: &str,
) -> Result<Vec<Value>, CatalogError> {
    // 主库:user email → id
    let user = main_db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id FROM user WHERE email = ?",
            [email.to_lowercase().into()],
        ))
        .await
        .map_err(db_err)?;
    let Some(user) = user else {
        return Ok(vec![]);
    };
    let user_id = user.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
    // 业务库:shipment + orders(customer_id 校验)
    let rows = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT s.id FROM shipment s JOIN orders o ON o.id = s.order_id \
             WHERE o.order_no = ? AND o.customer_id = ? AND s.status != 6",
            [order_no.into(), user_id.into()],
        ))
        .await
        .map_err(db_err)?;
    let mut out = vec![];
    for r in &rows {
        let sid = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
        out.push(find_dto(db, sid).await?);
    }
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn next_status_java_semantics() {
        assert_eq!(next_status(1, 2), 2, "待揽收→运输中");
        assert_eq!(next_status(2, 3), 3, "运输中→派送中");
        assert_eq!(next_status(3, 4), 4, "派送中→签收");
        assert_eq!(next_status(2, 4), 4, "跳级:运输中直接签收(rank 单调)");
        assert_eq!(next_status(2, 5), 5, "任意非终态进异常");
        assert_eq!(next_status(5, 3), 3, "异常恢复");
        assert_eq!(next_status(4, 6), 4, "DELIVERED 终态");
        assert_eq!(next_status(6, 2), 6, "CANCELLED 终态");
        assert_eq!(next_status(2, 1), 2, "rank 不升则停留");
    }
}
