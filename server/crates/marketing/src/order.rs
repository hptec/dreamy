//! order 域(交易核心):E-createOrder 下单事务(TX-TRD-001)+ 详情/取消/列表。
//! 对齐 OrderCreateService / StoreOrderService / OrderNoGenerator / SkuStockAdapter。
//! Stripe createPaymentIntent 在事务提交后调用(stub 模式本批:payment 记录 stub intent)。

use std::collections::HashMap;

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement, TransactionTrait};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

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
            "product_id": l.try_get::<i64>("", "product_id").unwrap_or(0),
            "sku_id": l.try_get::<i64>("", "sku_id").ok(),
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

// ══════════════════ 订单动作(order-flow-complete §3.1)══════════════════
// 状态码:1=PENDING 2=PAID 3=SHIPPED 4=COMPLETED 5=CANCELLED 6=REFUNDING 7=REFUNDED 8=DELIVERED

/// E-paymentIntent(Java StoreOrderService.retryPayment 口径:未支付订单复用/重建 stub PI)。
/// 错误:404601 不存在/跨用户(防探测);CANCELLED 且 expires_at 已过 → 410601;
/// CANCELLED 未过期/非 PENDING → 409602(STEP-TRD-02);PENDING 已过期 → 内联超时取消后
/// 410601(STEP-TRD-03)。stub 模式语义对齐 StubStripeClient:PI 状态以 payment 行为准
/// (status 1/2=requires_confirmation 可复用;3=succeeded 重建 rebind);client_secret =
/// {intent_id}_secret_stub(即取即用不落库)。STRIPE_MODE 随 credential.mode 下发(缺省 stub;
/// real 未接线,行为仍为 stub)。
pub async fn payment_intent(
    db: &DatabaseConnection,
    customer_id: i64,
    id: i64,
) -> Result<Value, CatalogError> {
    let order = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, order_no, status, total_amount, currency, (expires_at IS NOT NULL AND expires_at < NOW(3)) AS expired FROM orders WHERE id = ? AND customer_id = ?",
            [id.into(), customer_id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(404601))?;
    let status: i64 = order.try_get("", "status").unwrap_or(1);
    let expired: i64 = order.try_get("", "expired").unwrap_or(0);
    if status == 5 {
        // cancelled 且超时取消 → 410601;其余 → 409602
        return if expired != 0 { Err(trading(410601)) } else { Err(trading(409602)) };
    }
    if status != 1 {
        return Err(trading(409602));
    }
    // STEP-TRD-03 pending 已过期(调度器未及时取消)→ 内联超时取消 → 410601
    if expired != 0 {
        cancel_pending_timeout(db, id).await?;
        return Err(trading(410601));
    }
    let total_amount = crate::cart::dec(&order, "total_amount").unwrap_or(0.0);
    let currency: String = order.try_get("", "currency").unwrap_or_default();
    let payment = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, payment_intent_id, status FROM payment WHERE order_id = ? ORDER BY id DESC LIMIT 1",
            [id.into()],
        ))
        .await
        .map_err(db_err)?;
    // STEP-TRD-04 复用或重建 PI(RM-TRD-044)
    let mode = std::env::var("STRIPE_MODE").unwrap_or_else(|_| "stub".into());
    let (intent_id, client_secret) = match payment {
        Some(p) => {
            let pay_id = p.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            let existing: String = p.try_get("", "payment_intent_id").unwrap_or_default();
            let pay_status: i64 = p.try_get("", "status").unwrap_or(1);
            if !existing.is_empty() && (pay_status == 1 || pay_status == 2) {
                // 复用:stub 未确认 PI 恒为 requires_confirmation
                let secret = format!("{existing}_secret_stub");
                (existing, secret)
            } else {
                // 重建 + rebind(检索不可用/已成功但单仍 pending)
                let nid = format!("pi_stub_{}", uuid::Uuid::new_v4().simple());
                db.execute(Statement::from_sql_and_values(
                    DbBackend::MySql,
                    "UPDATE payment SET payment_intent_id = ?, updated_at = NOW(3) WHERE id = ?",
                    [nid.clone().into(), pay_id.into()],
                ))
                .await
                .map_err(db_err)?;
                let secret = format!("{nid}_secret_stub");
                (nid, secret)
            }
        }
        None => {
            let nid = format!("pi_stub_{}", uuid::Uuid::new_v4().simple());
            db.execute(Statement::from_sql_and_values(
                DbBackend::MySql,
                "INSERT INTO payment(order_id, provider, payment_intent_id, amount, currency, status, created_at, updated_at) VALUES (?,'stripe',?,?,?,1,NOW(3),NOW(3))",
                [id.into(), nid.clone().into(), total_amount.into(), currency.into()],
            ))
            .await
            .map_err(db_err)?;
            let secret = format!("{nid}_secret_stub");
            (nid, secret)
        }
    };
    Ok(serde_json::json!({
        "payment_intent_id": intent_id,
        "client_secret": client_secret,
        "mode": mode,
    }))
}

/// 超时取消内联(对齐 OrderCancelService.cancelPending TIMEOUT 口径:CAS + 回补库存 + 事件;
/// 与既有 cancel_order 同款语句;CAS 不中不阻断 410601 返回)
async fn cancel_pending_timeout(db: &DatabaseConnection, order_id: i64) -> Result<(), CatalogError> {
    let tx = db.begin().await.map_err(db_err)?;
    let cas = tx
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "UPDATE orders SET status = 5, updated_at = NOW(3) WHERE id = ? AND status = 1",
            [order_id.into()],
        ))
        .await
        .map_err(db_err)?;
    if cas.rows_affected() > 0 {
        let lines = tx
            .query_all(Statement::from_string(
                DbBackend::MySql,
                format!("SELECT sku_id, qty FROM order_line WHERE order_id = {order_id} AND sku_id IS NOT NULL"),
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
            "INSERT INTO order_event(order_id, type, actor_type, actor_id, title, detail, payload, customer_visible, created_at, updated_at) VALUES (?, 1, 1, NULL, 'Order cancelled', 'timeout cancelled', ?, 1, NOW(3), NOW(3))",
            [
                order_id.into(),
                serde_json::json!({"from": 1, "to": 5}).to_string().into(),
            ],
        ))
        .await
        .map_err(db_err)?;
    }
    tx.commit().await.map_err(db_err)?;
    Ok(())
}

/// E-confirmDelivery(SHIPPED(3)/DELIVERED(8) → COMPLETED(4);CAS,非法态 409602)。
/// SHIPPED 起跳同写 delivered_at;与自动签收/自动完成互不阻塞(STATE-5:谁先 CAS 成功谁生效)。
pub async fn confirm_delivery(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    customer_id: i64,
    id: i64,
) -> Result<Value, CatalogError> {
    let order = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, status, order_no FROM orders WHERE id = ? AND customer_id = ?",
            [id.into(), customer_id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(404601))?;
    let status: i64 = order.try_get("", "status").unwrap_or(1);
    let order_no: String = order.try_get("", "order_no").unwrap_or_default();
    if status != 3 && status != 8 {
        return Err(trading(409602));
    }
    let tx = db.begin().await.map_err(db_err)?;
    let cas = if status == 3 {
        tx.execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "UPDATE orders SET status = 4, completed_at = NOW(3), delivered_at = NOW(3), updated_at = NOW(3) WHERE id = ? AND status = 3",
            [id.into()],
        ))
        .await
        .map_err(db_err)?
    } else {
        tx.execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "UPDATE orders SET status = 4, completed_at = NOW(3), updated_at = NOW(3) WHERE id = ? AND status = 8",
            [id.into()],
        ))
        .await
        .map_err(db_err)?
    };
    if cas.rows_affected() == 0 {
        return Err(trading(409602)); // 竞态:自动签收/自动完成已先 CAS
    }
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO order_event(order_id, type, actor_type, actor_id, title, detail, payload, customer_visible, created_at, updated_at) VALUES (?, 1, 2, ?, 'Delivery confirmed', 'delivery confirmed by customer', ?, 1, NOW(3), NOW(3))",
        [
            id.into(),
            customer_id.into(),
            serde_json::json!({"from": status, "to": 4}).to_string().into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, &customer_id.to_string(), "确认收货", &order_no).await;
    order_detail(db, audit_db, customer_id, id).await
}

/// E-reorder(行商品回购物车;复用 cart::add_item 校验口径:下架 404501 / 缺货 409601 /
/// 定制不再开放 422604 / 字段 422601 → 跳过并记 skipped[reason_code],Java 码透传)。
pub async fn reorder(
    db: &DatabaseConnection,
    customer_id: i64,
    id: i64,
) -> Result<Value, CatalogError> {
    let exists = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id FROM orders WHERE id = ? AND customer_id = ?",
            [id.into(), customer_id.into()],
        ))
        .await
        .map_err(db_err)?;
    if exists.is_none() {
        return Err(trading(404601)); // 防探测:跨用户/不存在同码
    }
    let lines = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, product_id, sku_id, qty, custom_size_data FROM order_line WHERE order_id = ? ORDER BY id ASC",
            [id.into()],
        ))
        .await
        .map_err(db_err)?;
    let mut added: i64 = 0;
    let mut skipped: Vec<Value> = vec![];
    for l in &lines {
        let line_id = l.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
        let product_id = l.try_get::<i64>("", "product_id").unwrap_or(0);
        let sku_id = l.try_get::<i64>("", "sku_id").ok();
        let qty = l.try_get::<i64>("", "qty").unwrap_or(0);
        // custom_size_data 行(StoreCartService.fromMap 对齐):JSON 对象 → CustomSizeData;
        // 列 NULL/损坏 → None(定制行 sku_id 为 NULL → 走 422604 跳过,不误入现货分支)
        let custom_size_data: Option<crate::cart::CustomSizeData> =
            match l.try_get::<Value>("", "custom_size_data") {
                Ok(v) if v.is_object() => serde_json::from_value(v).ok(),
                _ => None,
            };
        let item = crate::cart::CartItemCreate {
            product_id: Some(product_id),
            sku_id,
            qty: Some(qty),
            custom_size_data,
        };
        match crate::cart::add_item(db, customer_id, item).await {
            Ok(_) => added += 1,
            Err(e) => skipped.push(serde_json::json!({"order_line_id": line_id, "reason_code": e.code})),
        }
    }
    Ok(serde_json::json!({"added_count": added, "skipped": skipped}))
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

// ══════════ admin 订单列表(Java AdminOrderService.pageList 移植) ══════════

/// 订单行跨库客户信息(dreamy_server.user 同实例 JOIN;匿名化用户 email NULL 天然排除)
const ADMIN_ORDER_COLS: &str = "o.id, o.order_no, o.customer_id, o.status, o.currency, o.wedding_date,  o.total_amount, o.payment_method, o.carrier, o.tracking_no, o.paid_at, o.shipped_at, o.completed_at,  o.created_at, o.updated_at";

pub async fn order_page_admin(
    db: &DatabaseConnection,
    page: i64,
    page_size: i64,
    status: Option<i64>,
    keyword: Option<&str>,
    currency: Option<&str>,
    from: Option<&str>,
    to: Option<&str>,
    production_stage: Option<i64>,
    wedding_before: Option<&str>,
    has_shipment: Option<bool>,
) -> Result<(Vec<Value>, i64), CatalogError> {
    let mut conds: Vec<String> = vec![];
    if let Some(s) = status {
        conds.push(format!("o.status = {}", s));
    }
    if let Some(c) = currency {
        conds.push(format!("o.currency = '{}'", c.replace('\'', "''")));
    }
    if let Some(s) = production_stage {
        conds.push(format!("o.production_stage = {}", s));
    }
    if let Some(f) = from {
        conds.push(format!("o.created_at >= '{}'", f.replace('\'', "''")));
    }
    if let Some(t) = to {
        conds.push(format!("o.created_at < '{}'", t.replace('\'', "''")));
    }
    if let Some(wb) = wedding_before {
        conds.push(format!("o.wedding_date < '{}'", wb.replace('\'', "''")));
    }
    // 关键字:order_no LIKE + 客户 email/name(跨库子查询,对齐 Java findUserIdsByNameOrEmailLike)
    if let Some(k) = keyword.filter(|s| !s.trim().is_empty()) {
        let k = k.replace('\'', "''");
        conds.push(format!(
            "(o.order_no LIKE '%{k}%' OR o.customer_id IN (SELECT id FROM dreamy_server.user WHERE email LIKE '%{k}%' OR name LIKE '%{k}%'))"
        ));
    }
    if let Some(want) = has_shipment {
        // Java:存在非 CANCELLED 包裹(近似:存在 shipment 行即可;shipment.rs cancel 置 status 可再细化)
        let op = if want { "EXISTS" } else { "NOT EXISTS" };
        conds.push(format!(
            "{} (SELECT 1 FROM shipment s WHERE s.order_id = o.id)",
            op
        ));
    }
    let where_clause = if conds.is_empty() { String::new() } else { format!(" WHERE {}", conds.join(" AND ")) };
    let total = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT COUNT(*) AS n FROM orders o{}", where_clause),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<i64>("", "n").ok())
        .unwrap_or(0);
    let offset = (page - 1) * page_size;
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT {} FROM orders o{} ORDER BY o.id DESC LIMIT {} OFFSET {}",
                ADMIN_ORDER_COLS, where_clause, page_size, offset
            ),
        ))
        .await
        .map_err(db_err)?;
    // item_count 批量聚合(SUM(qty) GROUP BY,缺失 → 0;RM-TRD-01c)
    let page_ids: Vec<i64> = rows.iter().filter_map(|r| r.try_get::<u64>("", "id").ok().map(|v| v as i64)).collect();
    eprintln!("[qa-itemcount] page_ids={:?}", page_ids);
    let mut item_counts: std::collections::HashMap<i64, i64> = std::collections::HashMap::new();
    if !page_ids.is_empty() {
        let list = page_ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
        for r in db
            .query_all(Statement::from_string(
                DbBackend::MySql,
                format!("SELECT order_id, COALESCE(SUM(qty), 0) AS n FROM order_line WHERE order_id IN ({}) GROUP BY order_id", list),
            ))
            .await
            .unwrap_or_default()
            .iter()
        {
            if let (Some(oid), Some(n)) = (
                r.try_get::<i64>("", "order_id").ok(),
                crate::cart::dec(r, "n").map(|v| v as i64),
            ) {
                item_counts.insert(oid, n);
            }
        }
    }
    let items = rows
        .iter()
        .map(|r| {
            let id = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            eprintln!("[qa-itemcount] lookup id={} -> {:?}", id, item_counts.get(&id));
            json!({
                "id": id,
                "order_no": r.try_get::<String>("", "order_no").unwrap_or_default(),
                "customer_id": r.try_get::<i64>("", "customer_id").unwrap_or(0),
                "item_count": item_counts.get(&id).copied().unwrap_or(0),
                "status": r.try_get::<i64>("", "status").unwrap_or(1),
                "currency": r.try_get::<String>("", "currency").unwrap_or_default(),
                "wedding_date": r.try_get::<String>("", "wedding_date").ok(),
                "total_amount": crate::cart::dec(r, "total_amount").unwrap_or(0.0),
                "payment_method": r.try_get::<String>("", "payment_method").ok(),
                "carrier": r.try_get::<String>("", "carrier").ok(),
                "tracking_no": r.try_get::<String>("", "tracking_no").ok(),
                "paid_at": r.try_get::<String>("", "paid_at").ok(),
                "shipped_at": r.try_get::<String>("", "shipped_at").ok(),
                "completed_at": r.try_get::<String>("", "completed_at").ok(),
                "created_at": r.try_get::<String>("", "created_at").ok(),
            })
        })
        .collect();
    Ok((items, total))
}

// ══════════ admin 订单详情/备注/状态/制作阶段(Java AdminOrderService §3.2)══════════

/// E-getAdminOrder:33 字段复合详情(无客户隔离;含全部事件+包裹+退款+地址快照)
pub async fn order_admin_detail(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
) -> Result<Value, CatalogError> {
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT id, order_no, customer_id, status, currency, exchange_rate, wedding_date, subtotal, shipping_fee, gift_wrap, gift_wrap_fee, discount_amount, total_amount, payment_method, carrier, tracking_no, expires_at, paid_at, shipped_at, completed_at, delivered_at, created_at, updated_at, tax_amount, tax_breakdown, incoterm, refunded_amount, amount_version, production_stage, shipping_service_level, estimated_delivery_from, estimated_delivery_to, address_snapshot FROM orders WHERE id = {}",
                id
            ),
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(404601))?;
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
                "SELECT id, type, title, detail, customer_visible, created_at FROM order_event WHERE order_id = {} ORDER BY id ASC",
                id
            ),
        ))
        .await
        .map_err(db_err)?;
    let shipments = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT id, shipment_no, carrier_code, carrier_name, tracking_no, tracking_url, status, shipped_at, delivered_at, last_event_desc, created_at FROM shipment WHERE order_id = {} ORDER BY id ASC",
                id
            ),
        ))
        .await
        .map_err(db_err)?;
    let refunds = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT id, refund_no, amount, currency, reason, reject_reason, status, return_tracking_no, applied_at FROM refund WHERE order_id = {} ORDER BY id ASC",
                id
            ),
        ))
        .await
        .map_err(db_err)?;
    let customer_id: i64 = row.try_get("", "customer_id").unwrap_or(0);
    let mut detail = order_detail(db, audit_db, customer_id, id).await?;
    if let Some(obj) = detail.as_object_mut() {
        obj.insert("customer_id".into(), json!(row.try_get::<i64>("", "customer_id").unwrap_or(0)));
        obj.insert("production_stage".into(), json!(row.try_get::<i64>("", "production_stage").unwrap_or(1)));
        obj.insert("incoterm".into(), json!(row.try_get::<i64>("", "incoterm").unwrap_or(1)));
        obj.insert("shipping_service_level".into(), json!(row.try_get::<i64>("", "shipping_service_level").unwrap_or(1)));
        obj.insert("delivered_at".into(), json!(row.try_get::<String>("", "delivered_at").ok()));
        obj.insert("estimated_delivery_from".into(), json!(row.try_get::<String>("", "estimated_delivery_from").ok()));
        obj.insert("estimated_delivery_to".into(), json!(row.try_get::<String>("", "estimated_delivery_to").ok()));
        obj.insert("address_snapshot".into(), row.try_get::<Value>("", "address_snapshot").unwrap_or(json!(null)));
        obj.insert(
            "events".into(),
            json!(events.iter().map(|e| json!({
                "id": e.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "type": e.try_get::<i64>("", "type").unwrap_or(1),
                "title": e.try_get::<String>("", "title").ok(),
                "detail": e.try_get::<String>("", "detail").ok(),
                "customer_visible": e.try_get::<bool>("", "customer_visible").unwrap_or(false),
                "created_at": e.try_get::<String>("", "created_at").ok(),
            })).collect::<Vec<_>>()),
        );
        obj.insert(
            "shipments".into(),
            json!(shipments.iter().map(|s| json!({
                "id": s.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "shipment_no": s.try_get::<String>("", "shipment_no").unwrap_or_default(),
                "carrier_code": s.try_get::<String>("", "carrier_code").ok(),
                "carrier_name": s.try_get::<String>("", "carrier_name").ok(),
                "tracking_no": s.try_get::<String>("", "tracking_no").ok(),
                "status": s.try_get::<i64>("", "status").unwrap_or(1),
                "shipped_at": s.try_get::<String>("", "shipped_at").ok(),
                "delivered_at": s.try_get::<String>("", "delivered_at").ok(),
            })).collect::<Vec<_>>()),
        );
        obj.insert(
            "refunds".into(),
            json!(refunds.iter().map(|rf| json!({
                "id": rf.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "refund_no": rf.try_get::<String>("", "refund_no").unwrap_or_default(),
                "amount": crate::cart::dec(rf, "amount").unwrap_or(0.0),
                "currency": rf.try_get::<String>("", "currency").ok(),
                "reason": rf.try_get::<String>("", "reason").ok(),
                "reject_reason": rf.try_get::<String>("", "reject_reason").ok(),
                "status": rf.try_get::<i64>("", "status").unwrap_or(1),
                "return_tracking_no": rf.try_get::<String>("", "return_tracking_no").ok(),
                "applied_at": rf.try_get::<String>("", "applied_at").ok(),
            })).collect::<Vec<_>>()),
        );
    }
    Ok(detail)
}

/// E-addOrderNote:order_event(NOTE=2, ADMIN, customer_visible 按选择;content ≤512)
pub async fn order_admin_add_note(
    db: &DatabaseConnection,
    id: i64,
    content: &str,
    customer_visible: bool,
    operator: &str,
) -> Result<Value, CatalogError> {
    let text = content.trim();
    if text.is_empty() || text.chars().count() > 512 {
        return Err(CatalogError::field_validation(&[("content", "1..=512 required")]));
    }
    db.query_one(Statement::from_string(
        DbBackend::MySql,
        format!("SELECT id FROM orders WHERE id = {}", id),
    ))
    .await
    .map_err(db_err)?
    .ok_or(trading(404601))?;
    let admin_id = operator.parse::<i64>().unwrap_or(0);
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO order_event(order_id, type, actor_type, actor_id, title, detail, payload, customer_visible, created_at, updated_at) VALUES (?, 2, 2, ?, 'Admin note', ?, NULL, ?, NOW(3), NOW(3))",
        [id.into(), admin_id.into(), text.into(), customer_visible.into()],
    ))
    .await
    .map_err(db_err)?;
    let ev = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT id, type, title, detail, customer_visible, created_at FROM order_event WHERE order_id = {} ORDER BY id DESC LIMIT 1", id),
        ))
        .await
        .map_err(db_err)?;
    Ok(match ev {
        Some(e) => json!({
            "id": e.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
            "type": e.try_get::<i64>("", "type").unwrap_or(2),
            "title": e.try_get::<String>("", "title").ok(),
            "detail": e.try_get::<String>("", "detail").ok(),
            "customer_visible": e.try_get::<bool>("", "customer_visible").unwrap_or(false),
            "created_at": e.try_get::<String>("", "created_at").ok(),
        }),
        None => json!(null),
    })
}

/// E-patchOrderStatus:仅 COMPLETED(4)/DELIVERED(8)/CANCELLED(5);CAS;取消回补+PAID 全额退款单
pub async fn order_admin_toggle_status(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    new_status: i64,
    operator: &str,
) -> Result<Value, CatalogError> {
    if !matches!(new_status, 4 | 5 | 8) {
        return Err(CatalogError::field_validation(&[("status", "invalid_enum")]));
    }
    let order = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT id, order_no, customer_id, status, total_amount, currency FROM orders WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(404601))?;
    let cur: i64 = order.try_get("", "status").unwrap_or(1);
    let customer_id: i64 = order.try_get("", "customer_id").unwrap_or(0);
    let admin_id = operator.parse::<i64>().unwrap_or(0);

    let tx = db.begin().await.map_err(db_err)?;
    match new_status {
        4 => {
            let from = if cur == 8 { 8 } else { 3 };
            let res = tx
                .execute(Statement::from_sql_and_values(
                    DbBackend::MySql,
                    "UPDATE orders SET status=4, completed_at=NOW(3), delivered_at=COALESCE(delivered_at, CASE WHEN ? = 3 THEN NOW(3) ELSE delivered_at END), updated_at=NOW(3) WHERE id=? AND status IN (3, 8)",
                    [from.into(), id.into()],
                ))
                .await
                .map_err(db_err)?;
            if res.rows_affected() == 0 {
                return Err(trading(409602));
            }
            tx.execute(Statement::from_string(
                DbBackend::MySql,
                format!(
                "INSERT INTO order_event(order_id, type, actor_type, actor_id, title, detail, payload, customer_visible, created_at, updated_at) VALUES ({}, 1, 2, {}, 'Order completed', 'status {} -> 4 (completed)', NULL, 1, NOW(3), NOW(3))",
                id, admin_id, from
            )))
            .await
            .map_err(db_err)?;
        }
        8 => {
            let res = tx
                .execute(Statement::from_sql_and_values(
                    DbBackend::MySql,
                    "UPDATE orders SET status=8, delivered_at=NOW(3), updated_at=NOW(3) WHERE id=? AND status = 3",
                    [id.into()],
                ))
                .await
                .map_err(db_err)?;
            if res.rows_affected() == 0 {
                return Err(trading(409602));
            }
            tx.execute(Statement::from_string(
                DbBackend::MySql,
                format!(
                "INSERT INTO order_event(order_id, type, actor_type, actor_id, title, detail, payload, customer_visible, created_at, updated_at) VALUES ({}, 1, 2, {}, 'Order delivered', 'status 3 -> 8 (delivered)', NULL, 1, NOW(3), NOW(3))",
                id, admin_id
            )))
            .await
            .map_err(db_err)?;
        }
        5 => {
            if !matches!(cur, 1 | 2) {
                return Err(trading(409602));
            }
            tx.execute(Statement::from_string(
                DbBackend::MySql,
                format!(
                "UPDATE orders SET status=5, updated_at=NOW(3) WHERE id={} AND status IN (1, 2)",
                id
            )))
            .await
            .map_err(db_err)?;
            let lines = tx
                .query_all(Statement::from_string(
                    DbBackend::MySql,
                    format!("SELECT sku_id, qty FROM order_line WHERE order_id = {} AND sku_id IS NOT NULL", id),
                ))
                .await
                .map_err(db_err)?;
            for l in &lines {
                if let (Some(sku_id), Some(qty)) = (l.try_get::<i64>("", "sku_id").ok(), l.try_get::<i64>("", "qty").ok()) {
                    tx.execute(Statement::from_sql_and_values(
                        DbBackend::MySql,
                        "UPDATE sku SET stock = stock + ?, version = version + 1 WHERE id = ?",
                        [qty.into(), sku_id.into()],
                    ))
                    .await
                    .map_err(db_err)?;
                }
            }
            if cur == 2 {
                let total: f64 = crate::cart::dec(&order, "total_amount").unwrap_or(0.0);
                let currency: String = order.try_get("", "currency").unwrap_or_default();
                let date = chrono::Utc::now().format("%Y%m%d").to_string();
                let seq: i64 = crate::payment::next_seq(audit_db, &date);
                let refund_no = format!("RFD-{}-{:04}", date, seq);
                // 全内联 SQL(受控值:refund_no/amount/currency 均为生成值,无用户输入)
                tx.execute(Statement::from_string(
                    DbBackend::MySql,
                    format!(
                        "INSERT INTO refund(refund_no, order_id, customer_id, amount, currency, reason, status, applied_at, from_status, created_at, updated_at) VALUES ('{}', {}, {}, {:.2}, '{}', 'Admin cancellation', 1, NOW(3), {}, NOW(3), NOW(3))",
                        refund_no, id, customer_id, total, currency, cur
                    ),
                ))
                .await
                .map_err(db_err)?;
                tx.execute(Statement::from_string(
                    DbBackend::MySql,
                    format!(
                        "INSERT INTO order_event(order_id, type, actor_type, actor_id, title, detail, payload, customer_visible, created_at, updated_at) VALUES ({}, 5, 2, {}, 'Refund created', 'admin cancellation full amount {:.2} {}', NULL, 1, NOW(3), NOW(3))",
                        id, admin_id, total, currency
                    ),
                ))
                .await
                .map_err(db_err)?;
            }
            tx.execute(Statement::from_string(
                DbBackend::MySql,
                format!(
                "INSERT INTO order_event(order_id, type, actor_type, actor_id, title, detail, payload, customer_visible, created_at, updated_at) VALUES ({}, 1, 2, {}, 'Order cancelled', 'status {} -> 5 (cancelled)', NULL, 1, NOW(3), NOW(3))",
                id, admin_id, cur
            )))
            .await
            .map_err(db_err)?;
        }
        _ => unreachable!(),
    }
    tx.commit().await.map_err(db_err)?;
    order_admin_detail(db, audit_db, id).await
}

/// E-patchProductionStage:仅 PAID;1→2→3→4 递进或回退一档;type=7 PRODUCTION 事件
pub async fn order_admin_production_stage(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    stage: i64,
    operator: &str,
) -> Result<Value, CatalogError> {
    if !(1..=4).contains(&stage) {
        return Err(CatalogError::field_validation(&[("stage", "invalid_enum")]));
    }
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT id, status, production_stage FROM orders WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(404601))?;
    let status: i64 = row.try_get("", "status").unwrap_or(1);
    let cur: i64 = row.try_get("", "production_stage").unwrap_or(1);
    if status != 2 {
        return Err(trading(409602).with_detail("reason", Value::from("only_paid_order")));
    }
    if (stage as i32 - cur as i32).abs() != 1 {
        return Err(trading(409602).with_detail("reason", Value::from("stage_step_violation")));
    }
    let admin_id = operator.parse::<i64>().unwrap_or(0);
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE orders SET production_stage=?, updated_at=NOW(3) WHERE id=?",
        [stage.into(), id.into()],
    ))
    .await
    .map_err(db_err)?;
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO order_event(order_id, type, actor_type, actor_id, title, detail, payload, customer_visible, created_at, updated_at) VALUES (?, 7, 2, ?, 'Production stage', ?, NULL, 0, NOW(3), NOW(3))",
        [id.into(), admin_id.into(), format!("stage {} -> {}", cur, stage).into()],
    ))
    .await
    .map_err(db_err)?;
    order_admin_detail(db, audit_db, id).await
}

// ══════════ admin ship/export/refund-create(Java AdminOrderService)══════════

/// E-shipOrder:{carrier, trackingNo} → 全部未发行行建一个包裹 → AdminOrderDetail
pub async fn order_admin_ship(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    carrier: &str,
    tracking_no: Option<&str>,
    operator: &str,
) -> Result<Value, CatalogError> {
    let lines = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT id, qty FROM order_line WHERE order_id = {} ORDER BY id ASC", id),
        ))
        .await
        .map_err(db_err)?;
    if lines.is_empty() {
        return Err(trading(404601));
    }
    let ship_lines: Vec<crate::shipment::ShipmentLineIn> = lines
        .iter()
        .filter_map(|l| {
            let lid = l.try_get::<u64>("", "id").ok().map(|v| v as i64);
            let qty = l.try_get::<i64>("", "qty").ok();
            lid.map(|order_line_id| crate::shipment::ShipmentLineIn {
                order_line_id: Some(order_line_id),
                qty,
            })
        })
        .collect();
    let req = crate::shipment::ShipmentCreate {
        carrier_code: Some(carrier.to_string()),
        tracking_no: tracking_no.map(String::from),
        lines: Some(ship_lines),
    };
    crate::shipment::create(db, audit_db, id, req, operator).await?;
    order_admin_detail(db, audit_db, id).await
}

/// E-createRefund(admin 发起部分退款):复用 apply_refund(状态守卫/已退额守卫/挂起单守卫)
pub async fn order_admin_refund_create(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    amount: Option<f64>,
    reason: Option<&str>,
) -> Result<Value, CatalogError> {
    let order = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT customer_id FROM orders WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(404601))?;
    let customer_id: i64 = order.try_get("", "customer_id").unwrap_or(0);
    let req = crate::payment::RefundApply {
        amount,
        reason: reason.map(String::from),
    };
    crate::payment::apply_refund(db, audit_db, customer_id, id, req).await
}

/// E-exportOrders:CSV keyset 500/上限 10000/BOM/截断标记
pub async fn order_admin_export(db: &DatabaseConnection) -> Result<(Vec<u8>, bool, String, usize), CatalogError> {
    const PAGE_SIZE: usize = 500;
    const MAX_ROWS: usize = 10000;
    let header = "id,order_no,customer_id,status,currency,total_amount,payment_method,carrier,tracking_no,created_at";
    let mut csv = String::from(header);
    csv.push('\n');
    let mut last_id: i64 = 0;
    let mut rows: usize = 0;
    let mut truncated = false;
    loop {
        let batch = db
            .query_all(Statement::from_string(
                DbBackend::MySql,
                format!(
                    "SELECT id, order_no, customer_id, status, currency, total_amount, payment_method, carrier, tracking_no, created_at FROM orders WHERE id > {} ORDER BY id ASC LIMIT {}",
                    last_id, PAGE_SIZE
                ),
            ))
            .await
            .map_err(db_err)?;
        if batch.is_empty() {
            break;
        }
        for r in &batch {
            if rows >= MAX_ROWS {
                truncated = true;
                break;
            }
            let esc = |s: String| s.replace(',', "\"").replace('\r', " ").replace('\n', " ");
            csv.push_str(&format!(
                "{},{},{},{},{},{},{},{},{},{}\n",
                r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                esc(r.try_get::<String>("", "order_no").unwrap_or_default()),
                r.try_get::<i64>("", "customer_id").unwrap_or(0),
                r.try_get::<i64>("", "status").unwrap_or(1),
                esc(r.try_get::<String>("", "currency").unwrap_or_default()),
                crate::cart::dec(r, "total_amount").unwrap_or(0.0),
                esc(r.try_get::<String>("", "payment_method").unwrap_or_default()),
                esc(r.try_get::<String>("", "carrier").unwrap_or_default()),
                esc(r.try_get::<String>("", "tracking_no").unwrap_or_default()),
                esc(r.try_get::<String>("", "created_at").unwrap_or_default()),
            ));
            last_id = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(last_id);
            rows += 1;
        }
        if truncated {
            break;
        }
    }
    if truncated {
        csv.push_str("# TRUNCATED AT 10000 ROWS\n");
    }
    let filename = format!("orders-{}.csv", chrono::Utc::now().format("%Y%m%d%H%M%S"));
    Ok((csv.into_bytes(), truncated, filename, rows))
}
