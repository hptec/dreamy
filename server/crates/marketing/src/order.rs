//! order 域(交易核心):E-createOrder 下单事务(TX-TRD-001)+ 详情/取消/列表。
//! 对齐 OrderCreateService / StoreOrderService / OrderNoGenerator / SkuStockAdapter。
//! Stripe createPaymentIntent 在事务提交后调用(stub 模式本批:payment 记录 stub intent)。

use std::collections::HashMap;

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement, TransactionTrait};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::checkout;
use crate::service_category::{cat_err, CatalogError};

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[order] db error:{e}");
    CatalogError::new(500601)
}

fn trading(code: i32) -> CatalogError {
    CatalogError::new(code)
}

fn field_err(fields: Vec<(&'static str, &'static str)>) -> CatalogError {
    CatalogError::field_validation(&fields)
}

pub(crate) async fn audit_customer(
    audit_db: &DatabaseConnection,
    operator_id: i64,
    action: &str,
    target: &str,
) {
    audit(audit_db, &operator_id.to_string(), action, target).await;
}

async fn audit(
    audit_db: &DatabaseConnection,
    operator_id: &str,
    action: &str,
    target: &str,
) {
    let oid: Option<i64> = operator_id.parse().ok();
    if let Err(e) = audit_db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO operation_log(operator_id, operator_name, action, target, ip, user_agent, changes, created_at, updated_at) VALUES (?,NULL,?,?,NULL,NULL,NULL,NOW(3),NOW(3))",
            [oid.into(), action.into(), target.into()],
        ))
        .await
    {
        tracing::warn!("[order] audit best-effort failed:{e}");
    }
}

/// 订单号 DRM-yyyyMMdd-NNNN(Redis INCR;不可用降级 4 位随机 + uk 兜底)
async fn next_order_no(db: &DatabaseConnection, redis: Option<&redis::aio::ConnectionManager>) -> String {
    let date = chrono::Utc::now().format("%Y%m%d").to_string();
    if let Some(redis) = redis {
        let mut conn = redis.clone();
        let key = format!("trading:orderno:{}", date);
        let r: Result<Option<i64>, _> = redis::cmd("INCR").arg(&key).query_async(&mut conn).await;
        if let Ok(Some(seq)) = r {
            let _ = redis::cmd("EXPIRE").arg(&key).arg(48 * 3600).query_async::<()>(&mut conn).await;
            return format!("DRM-{}-{:04}", date, seq);
        }
    }
    format!("DRM-{}-{:04}", date, rand_seq())
}

fn rand_seq() -> u32 {
    use rand::Rng;
    rand::thread_rng().gen_range(1000..=9999)
}

const ORDER_COLS: &str = "id, order_no, customer_id, status, currency, exchange_rate, wedding_date, subtotal, shipping_fee, gift_wrap, gift_wrap_fee, discount_amount, total_amount, coupon_id, payment_method, carrier, tracking_no, expires_at, paid_at, shipped_at, completed_at, tax_amount, tax_breakdown, incoterm, refunded_amount, amount_version, estimated_delivery_from, estimated_delivery_to, shipping_service_level, idempotency_key, created_at, updated_at";

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct OrderCreateRequest {
    pub idempotency_key: Option<String>,
    pub address_id: Option<i64>,
    pub currency: Option<String>,
    pub carrier: Option<String>,
    pub carrier_code: Option<String>,
    pub coupon_code: Option<String>,
    pub gift_wrap: Option<bool>,
    pub wedding_date: Option<String>,
    pub payment_method: Option<String>,
    pub locale: Option<String>,
    pub service_level: Option<i64>,
}

const PAYMENT_METHODS: [&str; 2] = ["card", "stripe"];

/// E-createOrder(TX-TRD-001 下单事务)
pub async fn create_order(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    redis: Option<&redis::aio::ConnectionManager>,
    customer_id: i64,
    req: &OrderCreateRequest,
) -> Result<Value, CatalogError> {
    // 入参验证
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let idem_key = req.idempotency_key.as_deref().map(str::trim).filter(|s| !s.is_empty());
    let idem_key = match idem_key {
        None => {
            fields.push(("idempotency_key", "required"));
            String::new()
        }
        Some(k) if k.len() > 64 => {
            fields.push(("idempotency_key", "too_long"));
            String::new()
        }
        Some(k) => k.to_string(),
    };
    if req.address_id.is_none() {
        fields.push(("address_id", "required"));
    }
    let carrier_input = req
        .carrier_code
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .or_else(|| req.carrier.as_deref().map(str::trim).filter(|s| !s.is_empty()));
    if carrier_input.is_none() {
        fields.push(("carrier", "required"));
    }
    let payment_method = req.payment_method.as_deref();
    if payment_method.is_none_or(|m| !PAYMENT_METHODS.contains(&m)) {
        fields.push(("payment_method", "invalid_enum"));
    }
    let locale = req.locale.as_deref().unwrap_or("en");
    if !fields.is_empty() {
        return Err(field_err(fields));
    }
    // STEP-TRD-01 幂等预检(uk_order_idem)
    if let Some(existing) = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id FROM orders WHERE idempotency_key = ?",
            [idem_key.clone().into()],
        ))
        .await
        .map_err(db_err)?
    {
        let oid = existing.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
        let mut e = trading(409906); // DUPLICATE_SUBMISSION
        e.details = Some(serde_json::json!({"order_id": oid}));
        return Err(e);
    }
    // STEP-TRD-02/03 全量服务端重算(strict)
    let quote_req = checkout::QuoteRequest {
        address_id: req.address_id,
        country: None,
        currency: req.currency.clone(),
        carrier: carrier_input.map(String::from),
        coupon_code: req.coupon_code.clone(),
        gift_wrap: Some(req.gift_wrap.unwrap_or(false)),
        wedding_date: req.wedding_date.clone(),
        service_level: req.service_level,
    };
    let quote = checkout::quote(db, customer_id, &quote_req).await?;
    // STEP-TRD-05 下单事务(expires_at 服务端计算绝对时间,避免 prepared 函数内 ? 的驱动 bug)
    let expires_minutes = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            "SELECT pending_timeout_minutes FROM checkout_config WHERE id = 1".to_string(),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<i64>("", "pending_timeout_minutes").ok())
        .unwrap_or(30);
    let expires_at = (chrono::Local::now() + chrono::Duration::minutes(expires_minutes))
        .format("%Y-%m-%d %H:%M:%S%.3f")
        .to_string();
    let tx = db.begin().await.map_err(db_err)?;
    // ① INSERT orders(订单号 ×3 重取)
    let order_no = next_order_no(db, redis).await;
    let expires_minutes = tx
        .query_one(Statement::from_string(
            DbBackend::MySql,
            "SELECT pending_timeout_minutes FROM checkout_config WHERE id = 1".to_string(),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<i64>("", "pending_timeout_minutes").ok())
        .unwrap_or(30);
    let addr_snapshot = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT JSON_OBJECT('receiver', receiver, 'phone', phone, 'line', line, 'city', city, 'state', state, 'zip', zip, 'country', country, 'country_code', country_code, 'region_code', region_code) AS snap FROM address WHERE id = ?",
            [req.address_id.unwrap_or(0).into()],
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<Value>("", "snap").ok());
    let insert_res = tx
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO orders SET order_no=?, customer_id=?, status=?, currency=?, locale_snapshot=?, exchange_rate=?, wedding_date=?, subtotal=?, shipping_fee=?, gift_wrap=?, gift_wrap_fee=?, discount_amount=?, total_amount=?, payment_method=?, address_snapshot=?, carrier=?, idempotency_key=?, expires_at=?, tax_amount=?, tax_breakdown=?, incoterm=?, refunded_amount=1, amount_version=2, estimated_delivery_from=?, estimated_delivery_to=?, shipping_service_level=?, created_at=NOW(3), updated_at=NOW(3)",
                [
                    order_no.clone().into(),
                    customer_id.into(),
                    1i64.into(), // PENDING
                    quote.currency.clone().into(),
                    locale.into(),
                    quote.exchange_rate.into(),
                    req.wedding_date.clone().into(),
                    quote.subtotal.into(),
                    quote.shipping_fee.into(),
                    (req.gift_wrap.unwrap_or(false) as i64).into(),
                    quote.gift_wrap_fee.into(),
                    quote.discount_amount.into(),
                    quote.total_amount.into(),
                    req.payment_method.clone().unwrap_or_default().into(),
                    addr_snapshot.map(|v| v.to_string()).into(),
                    quote.carrier_code.clone().unwrap_or_default().into(),
                    idem_key.clone().into(),
                    expires_at.into(),
                    quote.tax_amount.into(),
                    (!quote.tax_breakdown.is_empty())
                        .then(|| serde_json::json!(quote.tax_breakdown).to_string())
                        .into(),
                    quote.incoterm.unwrap_or(2).into(),
                    quote.estimated_delivery_from.clone().into(),
                    quote.estimated_delivery_to.clone().into(),
                    quote.service_level.into(),
                ],
            ))
        .await;
    let insert_res = match insert_res {
        Ok(r) => r,
        Err(e) => {
            if e.to_string().contains("uk_order_idem") || e.to_string().contains("1062") {
                // 幂等冲突:并发同键 → 409906
                let mut ex = trading(409906);
                if let Some(existing) = db
                    .query_one(Statement::from_sql_and_values(
                        DbBackend::MySql,
                        "SELECT id FROM orders WHERE idempotency_key = ?",
                        [idem_key.clone().into()],
                    ))
                    .await
                    .ok()
                    .flatten()
                {
                    let oid = existing.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
                    ex.details = Some(serde_json::json!({"order_id": oid}));
                }
                return Err(ex);
            }
            return Err(db_err(e));
        }
    };
    let order_id = insert_res.last_insert_id();
    // ② order_line 快照批插
    for line in &quote.lines_snapshot {
        tx.execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO order_line(order_id, product_id, sku_id, product_name, sku_code, color, size, qty, unit_price, img, custom_size_data, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,NOW(3),NOW(3))",
            [
                (order_id as i64).into(),
                line.product_id.into(),
                line.sku_id.into(),
                line.product_name.clone().into(),
                line.sku_code.clone().into(),
                line.color.clone().into(),
                line.size.clone().into(),
                line.qty.into(),
                line.unit_price.into(),
                line.img.clone().into(),
                line.custom_size_data.clone().map(|v| v.to_string()).into(),
            ],
        ))
        .await
        .map_err(db_err)?;
    }
    // ③ 现货行乐观锁 CAS ×3(定制行不扣)
    for line in &quote.lines_snapshot {
        if let (Some(sku_id), Some(version)) = (line.sku_id, line.sku_version) {
            let mut ok = false;
            for _ in 0..3 {
                let res = tx
                    .execute(Statement::from_sql_and_values(
                        DbBackend::MySql,
                        "UPDATE sku SET stock = stock - ?, version = version + 1 WHERE id = ? AND version = ? AND stock >= ?",
                        [line.qty.into(), sku_id.into(), version.into(), line.qty.into()],
                    ))
                    .await
                    .map_err(db_err)?;
                if res.rows_affected() > 0 {
                    ok = true;
                    break;
                }
                // 重读最新 version
                if let Some(latest) = tx
                    .query_one(Statement::from_sql_and_values(
                        DbBackend::MySql,
                        "SELECT stock, version FROM sku WHERE id = ?",
                        [sku_id.into()],
                    ))
                    .await
                    .map_err(db_err)?
                {
                    let stock = latest.try_get::<i64>("", "stock").unwrap_or(0);
                    if stock < line.qty {
                        break; // 库存不足 → 409601
                    }
                }
            }
            if !ok {
                return Err(trading(409601)); // 整体回滚
            }
        }
    }
    // ⑤ 清车
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "DELETE FROM cart_item WHERE customer_id = ?",
        [customer_id.into()],
    ))
    .await
    .map_err(db_err)?;
    // ⑥ order_event(STATUS_CHANGED → PENDING;type=1 actor_type=CUSTOMER=2;payload {from:null,to:1})
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO order_event(order_id, type, actor_type, actor_id, title, detail, payload, customer_visible, created_at, updated_at) VALUES (?, 1, 2, ?, 'Order placed', 'order placed', ?, 1, NOW(3), NOW(3))",
        [
            (order_id as i64).into(),
            customer_id.into(),
            serde_json::json!({"from": null, "to": 1}).to_string().into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, &customer_id.to_string(), "创建订单", &order_no).await;
    // STEP-TRD-06 PaymentIntent(stub 模式:记 created stub intent;real 模式随 payment 域接线)
    let (intent_id, client_secret) = (
        format!("pi_stub_{}", uuid::Uuid::new_v4().simple()),
        format!("pi_stub_secret_{}", uuid::Uuid::new_v4().simple()),
    );
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO payment(order_id, provider, payment_intent_id, amount, currency, status, created_at, updated_at) VALUES (?,?,?,?,?,1,NOW(3),NOW(3))",
        [
            (order_id as i64).into(),
            "stripe".into(),
            intent_id.clone().into(),
            quote.total_amount.into(),
            quote.currency.clone().into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    let order_detail = order_detail(db, audit_db, customer_id, order_id as i64).await?;
    Ok(serde_json::json!({
        "order": order_detail,
        "payment": {
            "payment_intent_id": intent_id,
            "client_secret": client_secret,
            "mode": "stub",
        }
    }))
}

/// 订单详情(store 口径;MAP-TRD-005)
pub async fn order_detail(
    db: &DatabaseConnection,
    _audit_db: &DatabaseConnection,
    customer_id: i64,
    id: i64,
) -> Result<Value, CatalogError> {
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT {} FROM orders WHERE id = {} AND customer_id = {}", ORDER_COLS, id, customer_id),
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(404906))?;
    let lines = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT id, product_id, sku_id, product_name, sku_code, color, size, qty, unit_price, img, custom_size_data FROM order_line WHERE order_id = {} ORDER BY id ASC",
                id
            ),
        ))
        .await
        .map_err(db_err)?;
    let events = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT type, title, detail, customer_visible, created_at FROM order_event WHERE order_id = {} AND customer_visible = 1 ORDER BY id ASC",
                id
            ),
        ))
        .await
        .map_err(db_err)?;
    Ok(serde_json::json!({
        "id": row.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "order_no": row.try_get::<String>("", "order_no").unwrap_or_default(),
        "status": row.try_get::<i64>("", "status").unwrap_or(1),
        "currency": row.try_get::<String>("", "currency").unwrap_or_default(),
        "exchange_rate": crate::cart::dec(&row, "exchange_rate").unwrap_or(1.0),
        "wedding_date": row.try_get::<String>("", "wedding_date").ok(),
        "subtotal": crate::cart::dec(&row, "subtotal").unwrap_or(0.0),
        "shipping_fee": crate::cart::dec(&row, "shipping_fee").unwrap_or(0.0),
        "gift_wrap": row.try_get::<bool>("", "gift_wrap").unwrap_or(false),
        "gift_wrap_fee": crate::cart::dec(&row, "gift_wrap_fee").unwrap_or(0.0),
        "discount_amount": crate::cart::dec(&row, "discount_amount").unwrap_or(0.0),
        "total_amount": crate::cart::dec(&row, "total_amount").unwrap_or(0.0),
        "payment_method": row.try_get::<String>("", "payment_method").ok(),
        "carrier": row.try_get::<String>("", "carrier").ok(),
        "tracking_no": row.try_get::<String>("", "tracking_no").ok(),
        "expires_at": row.try_get::<String>("", "expires_at").ok(),
        "paid_at": row.try_get::<String>("", "paid_at").ok(),
        "shipped_at": row.try_get::<String>("", "shipped_at").ok(),
        "completed_at": row.try_get::<String>("", "completed_at").ok(),
        "created_at": row.try_get::<String>("", "created_at").ok(),
        "tax_amount": crate::cart::dec(&row, "tax_amount").unwrap_or(0.0),
        "refunded_amount": crate::cart::dec(&row, "refunded_amount").unwrap_or(0.0),
        "amount_version": row.try_get::<i64>("", "amount_version").unwrap_or(2),
        "lines": lines.iter().map(|l| serde_json::json!({
            "product_id": l.try_get::<u64>("", "product_id").map(|v| v as i64).unwrap_or(0),
            "sku_id": l.try_get::<u64>("", "sku_id").ok().map(|v| v as i64),
            "product_name": l.try_get::<String>("", "product_name").unwrap_or_default(),
            "sku_code": l.try_get::<String>("", "sku_code").ok(),
            "color": l.try_get::<String>("", "color").ok(),
            "size": l.try_get::<String>("", "size").ok(),
            "qty": l.try_get::<i64>("", "qty").unwrap_or(0),
            "unit_price": crate::cart::dec(l, "unit_price").unwrap_or(0.0),
            "img": l.try_get::<String>("", "img").ok(),
            "custom_size_data": l.try_get::<Value>("", "custom_size_data").ok(),
        })).collect::<Vec<_>>(),
        "events": events.iter().map(|e| serde_json::json!({
            "type": e.try_get::<i64>("", "type").unwrap_or(1),
            "title": e.try_get::<String>("", "title").ok(),
            "detail": e.try_get::<String>("", "detail").ok(),
            "created_at": e.try_get::<String>("", "created_at").ok(),
        })).collect::<Vec<_>>(),
    }))
}

/// E-listStoreOrders(分页)
pub async fn order_page(
    db: &DatabaseConnection,
    customer_id: i64,
    page: i64,
    page_size: i64,
) -> Result<(Vec<Value>, i64), CatalogError> {
    let total = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT COUNT(*) AS n FROM orders WHERE customer_id = ?",
            [customer_id.into()],
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<i64>("", "n").ok())
        .unwrap_or(0);
    let offset = (page - 1) * page_size;
    let rows = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            format!(
                "SELECT {} FROM orders WHERE customer_id = ? ORDER BY id DESC LIMIT {} OFFSET {}",
                ORDER_COLS, page_size, offset
            ),
            [customer_id.into()],
        ))
        .await
        .map_err(db_err)?
        .iter()
        .map(|r| {
            serde_json::json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "order_no": r.try_get::<String>("", "order_no").unwrap_or_default(),
                "status": r.try_get::<i64>("", "status").unwrap_or(1),
                "total_amount": r.try_get::<f64>("", "total_amount").unwrap_or(0.0),
                "created_at": r.try_get::<String>("", "created_at").ok(),
            })
        })
        .collect();
    Ok((rows, total))
}

/// E-cancelStoreOrder(仅 PENDING 可取消;回补库存)
pub async fn cancel_order(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    customer_id: i64,
    id: i64,
) -> Result<(), CatalogError> {
    let order = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            format!("SELECT id, status, order_no FROM orders WHERE id = ? AND customer_id = ?"),
            [id.into(), customer_id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(404906))?;
    let status: i64 = order.try_get("", "status").unwrap_or(1);
    let order_no: String = order.try_get("", "order_no").unwrap_or_default();
    // PENDING(1) / PAID(2) 可取消(shippingrate 状态机:PAID→CANCELLED 合法)
    if status != 1 && status != 2 {
        return Err(CatalogError::new(409703).with_detail("reason", Value::from("illegal_transition")));
    }
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE orders SET status = 5, updated_at = NOW(3) WHERE id = ? AND status IN (1, 2)",
        [id.into()],
    ))
    .await
    .map_err(db_err)?;
    // 回补库存(现货行)
    let lines = tx
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT sku_id, qty FROM order_line WHERE order_id = {} AND sku_id IS NOT NULL", id),
        ))
        .await
        .map_err(db_err)?;
    for l in &lines {
        if let (Some(sku_id), Some(qty)) = (
            l.try_get::<i64>("", "sku_id").ok(),
            l.try_get::<i64>("", "qty").ok(),
        ) {
            tx.execute(Statement::from_sql_and_values(
                DbBackend::MySql,
                "UPDATE sku SET stock = stock + ?, version = version + 1 WHERE id = ?",
                [qty.into(), sku_id.into()],
            ))
            .await
            .map_err(db_err)?;
        }
    }
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO order_event(order_id, type, actor_type, actor_id, title, detail, payload, customer_visible, created_at, updated_at) VALUES (?, 1, 2, ?, 'Order cancelled', 'order cancelled', ?, 1, NOW(3), NOW(3))",
        [
            id.into(),
            customer_id.into(),
            serde_json::json!({"from": status, "to": 5}).to_string().into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, &customer_id.to_string(), "取消订单", &order_no).await;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn state_machine_topologies() {
        // PENDING → PAID/CANCELLED;PAID → SHIPPED/REFUNDING/CANCELLED;COMPLETED/CANCELLED/REFUNDED 终态
        assert!(transition_ok(1, 2));
        assert!(transition_ok(1, 5));
        assert!(transition_ok(2, 3));
        assert!(transition_ok(2, 5));
        assert!(transition_ok(3, 4) || transition_ok(3, 8));
        assert!(!transition_ok(4, 5), "COMPLETED 终态");
        assert!(!transition_ok(5, 1), "CANCELLED 终态");
    }

    fn transition_ok(from: i64, to: i64) -> bool {
        matches!(
            (from, to),
            (1, 2) | (1, 5) | (2, 3) | (2, 6) | (2, 5) | (3, 4) | (3, 8) | (3, 6) | (8, 4) | (8, 6) | (6, 7) | (6, 2) | (6, 3) | (6, 8)
        )
    }
}
