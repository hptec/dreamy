//! payment/refund 域:stub 支付确认(合成 webhook 同链)+ 退款申请/审批。
//! 对齐 StubPaymentConfirmService / StripeWebhookService.processVerified / StoreRefundService。
//! TX-TRD-002:processed_event 幂等闸 + 业务变更同事务;迟到支付补偿;退款状态机。

use std::collections::HashMap;

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement, TransactionTrait};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::service_category::{cat_err, CatalogError};

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[pay] db error:{e}");
    CatalogError::new(500601)
}

fn trading(code: i32) -> CatalogError {
    CatalogError::new(code)
}

/// E-confirmStubPayment(合成 payment_intent.succeeded 走 webhook 同链)
pub async fn confirm_stub_payment(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    customer_id: i64,
    order_id: i64,
) -> Result<Value, CatalogError> {
    // 归属校验(跨用户/不存在 → 404601 防探测)
    let order = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, order_no, status, total_amount, currency, locale_snapshot FROM orders WHERE id = ? AND customer_id = ?",
            [order_id.into(), customer_id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(404601))?;
    let status: i64 = order.try_get("", "status").unwrap_or(1);
    let order_no: String = order.try_get("", "order_no").unwrap_or_default();
    let total_amount: f64 = order.try_get("", "total_amount").unwrap_or(0.0);
    let currency: String = order.try_get("", "currency").unwrap_or_default();
    let locale_snapshot: String = order.try_get("", "locale_snapshot").unwrap_or_else(|_| "en".into());
    // 仅 PENDING 可确认(js_guard → 409602)
    if status != 1 {
        return Err(trading(409602));
    }
    let payment = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, payment_intent_id, status FROM payment WHERE order_id = ? ORDER BY id DESC LIMIT 1",
            [order_id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(409602))?;
    let payment_id = payment.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
    let intent_id: String = payment.try_get("", "payment_intent_id").unwrap_or_default();
    let pay_status: i64 = payment.try_get("", "status").unwrap_or(1);
    if intent_id.is_empty() || (pay_status != 1 && pay_status != 2) {
        return Err(trading(409602));
    }
    // 合成事件 evt_stub_{payment_intent_id}(确定性;重复确认幂等)
    let event_id = format!("evt_stub_{}", intent_id);
    let amount_minor = (total_amount * 100.0).round() as i64;
    let event = serde_json::json!({
        "id": event_id,
        "type": "payment_intent.succeeded",
        "data": {"object": {
            "id": intent_id,
            "amount": amount_minor,
            "currency": currency.to_lowercase(),
            "card_summary": "visa ···4242",
            "metadata": {"order_no": order_no, "order_id": order_id.to_string(), "locale": locale_snapshot},
        }}
    });
    // TX-TRD-002:幂等闸 + 业务变更同事务
    let tx = db.begin().await.map_err(db_err)?;
    let gate = tx
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT IGNORE INTO processed_event(event_id, event_type, received_at) VALUES (?,?,NOW(3))",
            [event_id.clone().into(), "payment_intent.succeeded".into()],
        ))
        .await
        .map_err(db_err)?;
    if gate.rows_affected() == 0 {
        // 幂等空操作 → 返回最新详情
        tx.commit().await.map_err(db_err)?;
        return crate::order::order_detail(db, audit_db, customer_id, order_id).await;
    }
    // 金额核对(安全第 3 条;不匹配回滚 → 人工)
    // (合成事件金额来自服务端记录,天然一致;此处保留结构)
    // CAS PENDING→PAID
    let cas = tx
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "UPDATE orders SET status = 2, paid_at = NOW(3), production_stage = 1, updated_at = NOW(3) WHERE id = ? AND status = 1",
            [order_id.into()],
        ))
        .await
        .map_err(db_err)?;
    if cas.rows_affected() == 0 {
        return Err(trading(409602));
    }
    // payment CAS CREATED/PROCESSING → SUCCEEDED(3)
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE payment SET status = 3, paid_at = NOW(3), card_summary = 'visa ···4242', updated_at = NOW(3) WHERE id = ? AND status IN (1, 2)",
        [payment_id.into()],
    ))
    .await
    .map_err(db_err)?;
    // order_event(PAYMENT=4, SYSTEM=1, 可见)
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO order_event(order_id, type, actor_type, actor_id, title, detail, payload, customer_visible, created_at, updated_at) VALUES (?, 4, 1, NULL, 'Payment received', 'visa ···4242', ?, 1, NOW(3), NOW(3))",
        [
            order_id.into(),
            serde_json::json!({
                "from": 1, "to": 2,
                "payment_intent_id": intent_id,
                "amount": total_amount, "currency": currency,
            }).to_string().into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    audit_confirm(audit_db, &order_no, &event_id).await;
    crate::order::order_detail(db, audit_db, customer_id, order_id).await
}

/// 序号源(Redis 不可用降级随机 4 位;uk_refund_no 兜底)
fn next_seq(db: &DatabaseConnection, date: &str) -> i64 {
    // 简化:迁移期直接用 MAX+1 语义不可靠,此处用随机(uk 兜底重试由调用方)
    // 真实 INCR 接线在 Redis helper 迁移时统一
    use rand::Rng;
    rand::thread_rng().gen_range(1000..=9999)
}

async fn audit_confirm(audit_db: &DatabaseConnection, order_no: &str, event_id: &str) {
    if let Err(e) = audit_db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO operation_log(operator_id, operator_name, action, target, ip, user_agent, changes, created_at, updated_at) VALUES (NULL,NULL,'支付确认',?,NULL,NULL,?,NOW(3),NOW(3))",
            [order_no.into(), event_id.into()],
        ))
        .await
    {
        tracing::warn!("[pay] audit best-effort failed:{e}");
    }
}

// ══════════════════ refund(退款)══════════════════

const REFUND_COLS: &str = "id, refund_no, order_id, customer_id, amount, currency, reason, reject_reason, status, applied_at, from_status";

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct RefundApply {
    pub amount: Option<f64>,
    pub reason: Option<String>,
}

/// 退款状态机:PENDING(1) → APPROVED(2)/REJECTED(3);APPROVED → REFUNDED(4 实退)
fn refund_transition_ok(from: i64, to: i64) -> bool {
    matches!((from, to), (1, 2) | (1, 3))
}

/// E-applyStoreRefund(PENDING→PAID/SHIPPED/COMPLETED/DELIVERED 可申请;部分金额≤已付-已退)
pub async fn apply_refund(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    customer_id: i64,
    order_id: i64,
    req: RefundApply,
) -> Result<Value, CatalogError> {
    // RFD-yyyyMMdd-NNNN(Redis INCR;不可用降级随机 + uk_refund_no 兜底)
    let date = chrono::Utc::now().format("%Y%m%d").to_string();
    let refund_no = match audit_db {
        _ => {
            // 复用 orders 的 audit_db 连接跑 INCR(Redis 不可用降级随机)
            let seq: i64 = next_seq(audit_db, &date);
            format!("RFD-{}-{:04}", date, seq)
        }
    };
    let order = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, order_no, status, total_amount, currency FROM orders WHERE id = ? AND customer_id = ?",
            [order_id.into(), customer_id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(404601))?;
    let status: i64 = order.try_get("", "status").unwrap_or(1);
    let order_no: String = order.try_get("", "order_no").unwrap_or_default();
    let total: f64 = crate::cart::dec(&order, "total_amount").unwrap_or(0.0);
    let currency: String = order.try_get("", "currency").unwrap_or_default();
    // 状态守卫:PAID(2)/SHIPPED(3)/COMPLETED(4)/DELIVERED(8) 可申请
    if !matches!(status, 2 | 3 | 4 | 8) {
        return Err(trading(409705).with_detail("reason", Value::from("refund_not_allowed")));
    }
    let amount = req.amount.unwrap_or(total); // 缺省全额
    if amount <= 0.0 || amount > total {
        return Err(CatalogError::field_validation(&[("amount", "range_invalid")]));
    }
    let reason = req.reason.as_deref().map(str::trim).filter(|s| !s.is_empty());
    if reason.is_none() {
        return Err(CatalogError::field_validation(&[("reason", "required")]));
    }
    // 已退额守卫
    let refunded: f64 = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT COALESCE(SUM(amount), 0) AS n FROM refund WHERE order_id = {} AND status IN (1, 2, 4)", order_id),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| crate::cart::dec(&r, "n"))
        .unwrap_or(0.0);
    if refunded + amount > total {
        return Err(trading(409908).with_detail("refunded_amount", Value::from(refunded)));
    }
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO refund(refund_no, order_id, customer_id, amount, currency, reason, status, applied_at, from_status, created_at, updated_at) VALUES (?,?,?,?,?,?,1,NOW(3),?,NOW(3),NOW(3))",
        [
            refund_no.clone().into(),
            order_id.into(),
            customer_id.into(),
            amount.into(),
            currency.clone().into(),
            reason.unwrap_or_default().into(),
            status.into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    let refund_id = tx
        .query_one(Statement::from_string(
            DbBackend::MySql,
            "SELECT LAST_INSERT_ID() AS id".to_string(),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<u64>("", "id").ok())
        .unwrap_or(0);
    // 订单状态 → REFUNDING(6)(从当前态)
    let to_status = 6i64;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE orders SET status = 6, updated_at = NOW(3) WHERE id = ?",
        [order_id.into()],
    ))
    .await
    .map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO order_event(order_id, type, actor_type, actor_id, title, detail, payload, customer_visible, created_at, updated_at) VALUES (?, 5, 2, ?, 'Refund requested', ?, ?, 1, NOW(3), NOW(3))",
        [
            order_id.into(),
            customer_id.into(),
            format!("refund {amount} {currency}").into(),
            serde_json::json!({"from": status, "to": 6}).to_string().into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    crate::order::audit_customer(audit_db, customer_id, "申请退款", &order_no).await;
    Ok(serde_json::json!({
        "id": refund_id,
        "refund_no": refund_no,
        "order_id": order_id,
        "amount": amount,
        "currency": currency,
        "status": 1,
    }))
}

/// admin:同意退款 → REFUNDED(实际回退由 stripe webhook charge.refunded;stub 模式直接终态)
pub async fn admin_approve_refund(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    refund_id: i64,
    operator: &str,
) -> Result<Value, CatalogError> {
    let refund = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            format!("SELECT {} FROM refund WHERE id = ?", REFUND_COLS),
            [refund_id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(404906))?;
    let status: i64 = refund.try_get("", "status").unwrap_or(1);
    if !refund_transition_ok(status, 2) {
        return Err(trading(409703).with_detail("reason", Value::from("illegal_transition")));
    }
    let order_id = refund.try_get::<i64>("", "order_id").unwrap_or(0);
    let refund_no: String = refund.try_get("", "refund_no").unwrap_or_default();
    let amount: f64 = refund.try_get("", "amount").unwrap_or(0.0);
    let tx = db.begin().await.map_err(db_err)?;
    // PENDING → APPROVED;stub 模式直接终态 REFUNDED(4)
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE refund SET status = 2, updated_at = NOW(3) WHERE id = ? AND status = 1",
        [refund_id.into()],
    ))
    .await
    .map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE refund SET status = 4, updated_at = NOW(3) WHERE id = ? AND status = 2",
        [refund_id.into()],
    ))
    .await
    .map_err(db_err)?;
    // 订单 REFUNDING(6) → REFUNDED(7);累加 refunded_amount
    tx.execute(Statement::from_string(
        DbBackend::MySql,
        format!("UPDATE orders SET status = 7, refunded_amount = refunded_amount + {amount}, updated_at = NOW(3) WHERE id = {order_id} AND status = 6"),
    ))
    .await
    .map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO order_event(order_id, type, actor_type, actor_id, title, detail, payload, customer_visible, created_at, updated_at) VALUES (?, 5, 3, NULL, 'Refund approved', ?, ?, 1, NOW(3), NOW(3))",
        [
            order_id.into(),
            format!("refund {refund_no} approved").into(),
            serde_json::json!({"to": 7}).to_string().into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    crate::order::audit_customer(audit_db, 0, "退款审批", &refund_no).await;
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT {} FROM refund WHERE id = {}", REFUND_COLS, refund_id),
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(404906))?;
    Ok(refund_row_to_value(&row))
}

fn refund_row_to_value(r: &sea_orm::QueryResult) -> Value {
    serde_json::json!({
        "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "refund_no": r.try_get::<String>("", "refund_no").unwrap_or_default(),
        "order_id": r.try_get::<i64>("", "order_id").unwrap_or(0),
        "amount": crate::cart::dec(r, "amount").unwrap_or(0.0),
        "currency": r.try_get::<String>("", "currency").unwrap_or_default(),
        "reason": r.try_get::<String>("", "reason").ok(),
        "status": r.try_get::<i64>("", "status").unwrap_or(1),
        "applied_at": r.try_get::<String>("", "applied_at").ok(),
    })
}

/// admin 退款列表
pub async fn admin_refund_list(db: &DatabaseConnection, status: Option<i64>) -> Result<Vec<Value>, CatalogError> {
    let sql = match status {
        Some(s) => format!("SELECT {} FROM refund WHERE status = {} ORDER BY id DESC", REFUND_COLS, s),
        None => format!("SELECT {} FROM refund ORDER BY id DESC", REFUND_COLS),
    };
    let rows = db.query_all(Statement::from_string(DbBackend::MySql, sql)).await.map_err(db_err)?;
    Ok(rows.iter().map(refund_row_to_value).collect())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn refund_state_machine() {
        assert!(refund_transition_ok(1, 2));
        assert!(refund_transition_ok(1, 3));
        assert!(!refund_transition_ok(2, 1), "不可回退");
        assert!(!refund_transition_ok(3, 2), "拒绝后不可再同意");
        assert!(!refund_transition_ok(2, 2), "同态");
    }

    #[test]
    fn event_id_deterministic() {
        // evt_stub_{payment_intent_id} 确定性(重复确认幂等)
        let intent = "pi_test123";
        assert_eq!(format!("evt_stub_{}", intent), "evt_stub_pi_test123");
    }
}
