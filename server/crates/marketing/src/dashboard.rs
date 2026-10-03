//! dashboard/analytics 域:E-ANA-01~03 只读聚合(与 Java AnalyticsReadMapper/Aggregator/Assembler 同口径)。
//! 支付口径:PAID_STATUSES=(2,3,4,6,7) + paid_at 落窗(DEC-ANA-3);金额口径:USD = 金额 / COALESCE(exchange_rate, 1)(决策14)。
//! 窗口一律 UTC 日历;响应键 snake_case(前端 portal-admin client.ts keysToCamel 递归转换)。
//! Java 缓存层(CACHE-ANA-001/002)本实现不含——直接回源聚合,数据量小可接受。

use std::collections::HashMap;

use chrono::{Datelike, Duration, NaiveDate, NaiveDateTime, Utc};
use rust_decimal::prelude::ToPrimitive;
use rust_decimal::{Decimal, RoundingStrategy};
use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement};
use serde_json::{json, Value};

use crate::service_category::CatalogError;

/// 支付口径状态集(DEC-ANA-3):2=paid 3=shipped 4=completed 6=refunding 7=refunded
const PAID_STATUSES: &str = "(2,3,4,6,7)";
/// top_products 取前 6(契约 maxItems=6,MAP-ANA-004)
const TOP_PRODUCTS_LIMIT: i64 = 6;
/// range 合法集(V-ANA-002/003);缺省 30d
const ALLOWED_RANGES: [&str; 3] = ["7d", "30d", "90d"];
const DEFAULT_RANGE: &str = "30d";
/// Java AnalyticsErrorCode.INVALID_RANGE:422001 {field:"range", allowed:[...]}
const ERR_INVALID_RANGE: i32 = 422001;
/// 本域 DB 内部错误码(沿用既有 500601)
const ERR_DB: i32 = 500601;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[ana] db error:{e}");
    CatalogError::new(ERR_DB)
}

fn sql(s: String) -> Statement {
    Statement::from_string(DbBackend::MySql, s)
}

/// Java setScale(n, HALF_UP) 同构(half away from zero)
fn half_up(d: Decimal, dp: u32) -> Decimal {
    d.round_dp_with_strategy(dp, RoundingStrategy::MidpointAwayFromZero)
}

fn d2f(d: Decimal) -> f64 {
    d.to_f64().unwrap_or(0.0)
}

/// datetime(3) 字面量(仅由内部窗口计算生成,非用户输入)
fn fmt_dt(dt: NaiveDateTime) -> String {
    dt.format("%Y-%m-%d %H:%M:%S%.3f").to_string()
}

/// RangeWindow.of:N 日窗口 = [今日(UTC)-(N-1)天 00:00, 现在],含今日共 N 个日桶
fn window_of(days: i64) -> (NaiveDate, String, String) {
    let now = Utc::now().naive_utc();
    let from = (now.date() - Duration::days(days - 1))
        .and_hms_opt(0, 0, 0)
        .expect("00:00:00 is a valid time");
    (now.date(), fmt_dt(from), fmt_dt(now))
}

/// KPI 窗口 = 本月 UTC:当月 1 日 00:00 至当前(DEC-ANA-2/3)
fn month_window() -> (String, String) {
    let now = Utc::now().naive_utc();
    let month_start = now
        .date()
        .with_day(1)
        .expect("day 1 is always valid")
        .and_hms_opt(0, 0, 0)
        .expect("00:00:00 is a valid time");
    (fmt_dt(month_start), fmt_dt(now))
}

/// V-ANA-002/003:range ∈ {7d,30d,90d},缺省(null)30d;枚举外(含空串/大小写变体 30D)→ 422001
fn parse_range(raw: Option<&str>) -> Result<(String, i64), CatalogError> {
    let range = raw.unwrap_or(DEFAULT_RANGE);
    if !ALLOWED_RANGES.contains(&range) {
        return Err(CatalogError {
            code: ERR_INVALID_RANGE,
            details: Some(json!({"field": "range", "allowed": ["7d", "30d", "90d"]})),
        });
    }
    let days = range[..range.len() - 1].parse::<i64>().unwrap_or(30);
    Ok((range.to_string(), days))
}

// ───────────────────────── read model 直查(RM-ANA-001~009,仅 SELECT) ─────────────────────────

/// RM-ANA-001 sumPaidGmvUsd:窗口内支付订单 GMV(USD 折算)
async fn sum_paid_gmv_usd(db: &DatabaseConnection, from: &str, to: &str) -> Result<Decimal, CatalogError> {
    let row = db
        .query_one(sql(format!(
            "SELECT COALESCE(SUM(o.total_amount / COALESCE(o.exchange_rate, 1)), 0) AS gmv_usd \
             FROM orders o \
             WHERE o.paid_at >= '{from}' AND o.paid_at < '{to}' AND o.status IN {PAID_STATUSES}"
        )))
        .await
        .map_err(db_err)?
        .ok_or_else(|| CatalogError::new(ERR_DB))?;
    Ok(row
        .try_get::<Decimal>("", "gmv_usd")
        .unwrap_or(Decimal::ZERO))
}

async fn count_rows(db: &DatabaseConnection, query: String) -> Result<i64, CatalogError> {
    let row = db
        .query_one(sql(query))
        .await
        .map_err(db_err)?
        .ok_or_else(|| CatalogError::new(ERR_DB))?;
    Ok(row.try_get::<i64>("", "c").unwrap_or(0))
}

/// RM-ANA-002 countPaidOrders:同 sumPaidGmvUsd 口径 COUNT(*)
async fn count_paid_orders(db: &DatabaseConnection, from: &str, to: &str) -> Result<i64, CatalogError> {
    count_rows(
        db,
        format!(
            "SELECT COUNT(*) AS c FROM orders o \
             WHERE o.paid_at >= '{from}' AND o.paid_at < '{to}' AND o.status IN {PAID_STATUSES}"
        ),
    )
    .await
}

/// RM-ANA-003 countApprovedRefunds:窗口内已同意退款单(DEC-ANA-3 refund_rate 分子)
async fn count_approved_refunds(db: &DatabaseConnection, from: &str, to: &str) -> Result<i64, CatalogError> {
    count_rows(
        db,
        format!(
            "SELECT COUNT(*) AS c FROM refund r \
             WHERE r.status = 2 AND r.applied_at >= '{from}' AND r.applied_at < '{to}'"
        ),
    )
    .await
}

/// RM-ANA-004 gmvTrendDaily:按日 GROUP BY(缺日补零由 fill_trend 完成,SQL 不造日历表)
async fn gmv_trend_daily(
    db: &DatabaseConnection,
    from: &str,
    to: &str,
) -> Result<Vec<(String, Decimal)>, CatalogError> {
    let rows = db
        .query_all(sql(format!(
            "SELECT DATE_FORMAT(o.paid_at, '%Y-%m-%d') AS day, \
                    SUM(o.total_amount / COALESCE(o.exchange_rate, 1)) AS gmv_usd \
             FROM orders o \
             WHERE o.paid_at >= '{from}' AND o.paid_at < '{to}' AND o.status IN {PAID_STATUSES} \
             GROUP BY DATE_FORMAT(o.paid_at, '%Y-%m-%d') ORDER BY day ASC"
        )))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .filter_map(|r| {
            let day = r.try_get::<String>("", "day").ok()?;
            let gmv = r.try_get::<Decimal>("", "gmv_usd").unwrap_or(Decimal::ZERO);
            Some((day, gmv))
        })
        .collect())
}

/// RM-ANA-008 categorySales:order_line × catalog 三层溯根聚合(断链行 root 为 NULL → 落 Other 桶)
async fn category_sales_rows(
    db: &DatabaseConnection,
    from: &str,
    to: &str,
) -> Result<Vec<(i64, String, Decimal)>, CatalogError> {
    let rows = db
        .query_all(sql(format!(
            "SELECT root.id AS root_category_id, root.name AS root_category_name, \
                    SUM(ol.unit_price * ol.qty / COALESCE(o.exchange_rate, 1)) AS amount_usd \
             FROM order_line ol \
             JOIN orders o ON o.id = ol.order_id \
               AND o.paid_at >= '{from}' AND o.paid_at < '{to}' AND o.status IN {PAID_STATUSES} \
             LEFT JOIN product p ON p.id = ol.product_id \
             LEFT JOIN category c1 ON c1.id = p.category_id \
             LEFT JOIN category c2 ON c2.id = c1.parent_id \
             LEFT JOIN category c3 ON c3.id = c2.parent_id \
             LEFT JOIN category root ON root.id = COALESCE(c3.id, c2.id, c1.id) \
             GROUP BY root.id, root.name ORDER BY amount_usd DESC"
        )))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            // category.id 为 BIGINT UNSIGNED;NULL root → (0, "Other")(MAP-ANA-003)
            let cid = r
                .try_get::<u64>("", "root_category_id")
                .map(|v| v as i64)
                .unwrap_or(0);
            let name = if cid == 0 {
                "Other".to_string()
            } else {
                r.try_get::<String>("", "root_category_name")
                    .unwrap_or_else(|_| "Other".to_string())
            };
            let amount = r
                .try_get::<Decimal>("", "amount_usd")
                .unwrap_or(Decimal::ZERO);
            (cid, name, amount)
        })
        .collect())
}

/// RM-ANA-009 topProducts:窗口内按 SUM(qty) DESC 取前 6(name/img 取 order_line 快照,DEC-ANA-8)
async fn top_products_rows(
    db: &DatabaseConnection,
    from: &str,
    to: &str,
) -> Result<Vec<(i64, String, Option<String>, i64, Decimal)>, CatalogError> {
    let rows = db
        .query_all(sql(format!(
            "SELECT ol.product_id AS product_id, \
                    MAX(ol.product_name) AS product_name, \
                    MAX(ol.img) AS img, \
                    CAST(SUM(ol.qty) AS SIGNED) AS sales, \
                    SUM(ol.unit_price * ol.qty / COALESCE(o.exchange_rate, 1)) AS amount_usd \
             FROM order_line ol \
             JOIN orders o ON o.id = ol.order_id \
               AND o.paid_at >= '{from}' AND o.paid_at < '{to}' AND o.status IN {PAID_STATUSES} \
             GROUP BY ol.product_id ORDER BY sales DESC, amount_usd DESC LIMIT {TOP_PRODUCTS_LIMIT}"
        )))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            (
                r.try_get::<i64>("", "product_id").unwrap_or(0),
                r.try_get::<String>("", "product_name").unwrap_or_default(),
                r.try_get::<String>("", "img").ok(),
                r.try_get::<i64>("", "sales").unwrap_or(0),
                r.try_get::<Decimal>("", "amount_usd").unwrap_or(Decimal::ZERO),
            )
        })
        .collect())
}

// ───────────────────────── 纯映射(Java AnalyticsAssembler 同构,可单测) ─────────────────────────

/// MAP-ANA-001 KPI 组装:gmv 2 位;avg_order_value = gmv/order_count(分母 0→0.00);
/// refund_rate = approved 退款数 ÷ 支付订单数 × 100(1 位,分母 0→0)
fn build_kpis(gmv_raw: Decimal, order_count: i64, approved_refunds: i64) -> (Decimal, Decimal, Decimal) {
    let gmv = half_up(gmv_raw, 2);
    let avg = if order_count == 0 {
        Decimal::ZERO
    } else {
        half_up(gmv / Decimal::from(order_count), 2)
    };
    let refund_rate = if order_count == 0 {
        Decimal::ZERO
    } else {
        half_up(
            Decimal::from(approved_refunds) * Decimal::from(100) / Decimal::from(order_count),
            1,
        )
    };
    (gmv, avg, refund_rate)
}

/// MAP-ANA-002 趋势补零:窗口 N 桶缺日 value=0;labels "M-D" 格式(如 10-3,不补零);
/// labels.length == values.length == N
fn fill_trend(first_day: NaiveDate, days: i64, rows: &[(String, Decimal)]) -> (Vec<String>, Vec<f64>) {
    let by_day: HashMap<&str, Decimal> = rows.iter().map(|(d, v)| (d.as_str(), *v)).collect();
    let mut labels = Vec::with_capacity(days as usize);
    let mut values = Vec::with_capacity(days as usize);
    for i in 0..days {
        let day = first_day + Duration::days(i);
        labels.push(format!("{}-{}", day.month(), day.day()));
        let key = format!("{:04}-{:02}-{:02}", day.year(), day.month(), day.day());
        let gmv = by_day.get(key.as_str()).copied().unwrap_or(Decimal::ZERO);
        values.push(d2f(half_up(gmv, 2)));
    }
    (labels, values)
}

/// MAP-ANA-003 category_sales:断链行归并 {0,"Other"};Σ≤0 → 空数组;
/// share 1 位,尾差并入最大项(排序后首项)使 Σ=100.0;按 amount DESC
fn build_category_sales(rows: Vec<(i64, String, Decimal)>) -> Vec<Value> {
    if rows.is_empty() {
        return Vec::new();
    }
    let mut names: HashMap<i64, String> = HashMap::new();
    let mut amounts: HashMap<i64, Decimal> = HashMap::new();
    for (cid, name, amount) in rows {
        names.entry(cid).or_insert(name);
        *amounts.entry(cid).or_insert(Decimal::ZERO) += amount;
    }
    let total: Decimal = amounts.values().copied().sum();
    if total <= Decimal::ZERO {
        return Vec::new();
    }
    let mut sorted: Vec<(i64, Decimal)> = amounts.into_iter().collect();
    sorted.sort_by(|a, b| b.1.cmp(&a.1));
    let names_ref = &names;
    let mut share_sum = Decimal::ZERO;
    let items: Vec<(i64, String, Decimal, Decimal)> = sorted
        .into_iter()
        .map(|(cid, amount)| {
            let share = half_up(amount * Decimal::from(100) / total, 1);
            share_sum += share;
            (
                cid,
                names_ref.get(&cid).cloned().unwrap_or_else(|| "Other".to_string()),
                half_up(amount, 2),
                share,
            )
        })
        .collect();
    // 尾差并入最大项(首项)使 Σ=100.0
    let mut items = items;
    let diff = half_up(Decimal::from(100), 1) - share_sum;
    if diff != Decimal::ZERO {
        if let Some(top) = items.first_mut() {
            top.3 += diff;
        }
    }
    items
        .into_iter()
        .map(|(cid, name, amount, share)| {
            json!({"category_id": cid, "category_name": name, "amount": d2f(amount), "share": d2f(share)})
        })
        .collect()
}

/// MAP-ANA-004 top_products:img 空串/NULL → image_url=null;amount 2 位
fn build_top_products(rows: Vec<(i64, String, Option<String>, i64, Decimal)>) -> Vec<Value> {
    rows.into_iter()
        .map(|(pid, name, img, sales, amount)| {
            json!({
                "product_id": pid,
                "name": name,
                "image_url": img.filter(|s| !s.trim().is_empty()),
                "sales": sales,
                "amount": d2f(half_up(amount, 2)),
            })
        })
        .collect()
}

// ───────────────────────── 端点编排 ─────────────────────────

/// STEP-ANA-02 KPI(窗口=本月 UTC,DEC-ANA-2/3;dashboard 与 overview 共用,与 range 无关)
async fn month_kpis(db: &DatabaseConnection) -> Result<Value, CatalogError> {
    let (from, to) = month_window();
    let gmv_raw = sum_paid_gmv_usd(db, &from, &to).await?;
    let order_count = count_paid_orders(db, &from, &to).await?;
    let approved = count_approved_refunds(db, &from, &to).await?;
    let (gmv, avg, refund_rate) = build_kpis(gmv_raw, order_count, approved);
    Ok(json!({
        "gmv_month": d2f(gmv),
        "order_count": order_count,
        "avg_order_value": d2f(avg),
        "refund_rate": d2f(refund_rate),
    }))
}

/// E-ANA-01 getAdminDashboard:KPI(本月) + todos 三计数(全量现态,非窗口,RM-ANA-005~007)
/// + gmv_trend 固定近 30 天(前端 14/30 天按钮客户端切片,DEC-ANA-4)
pub async fn dashboard(db: &DatabaseConnection) -> Result<Value, CatalogError> {
    let kpis = month_kpis(db).await?;
    let pending_refunds = count_rows(db, "SELECT COUNT(*) AS c FROM refund WHERE status = 1".to_string()).await?;
    let pending_reviews = count_rows(db, "SELECT COUNT(*) AS c FROM review WHERE status = 1".to_string()).await?;
    let unshipped_orders = count_rows(db, "SELECT COUNT(*) AS c FROM orders WHERE status = 2".to_string()).await?;
    let (first_day, from, to) = window_of(30);
    let trend_rows = gmv_trend_daily(db, &from, &to).await?;
    let (labels, values) = fill_trend(first_day, 30, &trend_rows);
    Ok(json!({
        "kpis": kpis,
        "todos": {
            "pending_refund_count": pending_refunds,
            "pending_review_count": pending_reviews,
            "unshipped_order_count": unshipped_orders,
        },
        "gmv_trend": {"labels": labels, "values": values},
    }))
}

/// E-ANA-02 getAdminAnalyticsOverview:KPI 固定本月与 range 无关;趋势/品类/Top 按 range 窗口
pub async fn overview(db: &DatabaseConnection, range_raw: Option<&str>) -> Result<Value, CatalogError> {
    let (_range, days) = parse_range(range_raw)?;
    let kpis = month_kpis(db).await?;
    let (first_day, from, to) = window_of(days);
    let trend_rows = gmv_trend_daily(db, &from, &to).await?;
    let (labels, values) = fill_trend(first_day, days, &trend_rows);
    let cat_rows = category_sales_rows(db, &from, &to).await?;
    let top_rows = top_products_rows(db, &from, &to).await?;
    Ok(json!({
        "kpis": kpis,
        "gmv_trend": {"labels": labels, "values": values},
        "category_sales": build_category_sales(cat_rows),
        "top_products": build_top_products(top_rows),
    }))
}

/// E-ANA-03 getAdminAnalyticsTraffic:range 校验(契约 V-ANA-003 必查)后走 GA4 降级链;
/// Java 经 Ga4TrafficPort 调 GA4,本实现无 GA4 数据源 → 恒为 DEC-ANA-5 ④ unavailable 形态
/// (200 + source_status="unavailable" + 三流量字段与 fetched_at 全 null,契约 required 仅 source_status)
pub async fn traffic(_db: &DatabaseConnection, range_raw: Option<&str>) -> Result<Value, CatalogError> {
    let (_range, _days) = parse_range(range_raw)?;
    Ok(json!({
        "source_status": "unavailable",
        "fetched_at": null,
        "traffic_sources": null,
        "device_share": null,
        "funnel": null,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_range_defaults_and_whitelist() {
        assert_eq!(parse_range(None).unwrap().1, 30);
        assert_eq!(parse_range(Some("7d")).unwrap().1, 7);
        assert_eq!(parse_range(Some("90d")).unwrap().1, 90);
        // 非法:空串/大小写变体/枚举外 → 422001
        assert_eq!(parse_range(Some("")).unwrap_err().code, ERR_INVALID_RANGE);
        assert_eq!(parse_range(Some("30D")).unwrap_err().code, ERR_INVALID_RANGE);
        assert_eq!(parse_range(Some("14d")).unwrap_err().code, ERR_INVALID_RANGE);
        let e = parse_range(Some("14d")).unwrap_err();
        assert_eq!(e.details.as_ref().unwrap()["field"], "range");
        assert_eq!(e.details.as_ref().unwrap()["allowed"][1], "30d");
    }

    #[test]
    fn kpis_match_java_halving() {
        // gmv=100.00,count=3,approved=1 → avg=33.33(HALF_UP),refund_rate=33.3
        let (gmv, avg, rate) = build_kpis(Decimal::new(10000, 2), 3, 1);
        assert_eq!(gmv, Decimal::new(10000, 2));
        assert_eq!(avg, Decimal::new(3333, 2));
        assert_eq!(rate, Decimal::new(333, 1));
        // 分母 0 → 0.00 / 0.0
        let (gmv, avg, rate) = build_kpis(Decimal::ZERO, 0, 0);
        assert_eq!(gmv, Decimal::ZERO);
        assert_eq!(avg, Decimal::ZERO);
        assert_eq!(rate, Decimal::ZERO);
    }

    #[test]
    fn fill_trend_zero_fills_and_md_labels() {
        let first = NaiveDate::from_ymd_opt(2026, 10, 1).unwrap();
        let rows = vec![("2026-10-02".to_string(), Decimal::from(12))];
        let (labels, values) = fill_trend(first, 3, &rows);
        assert_eq!(labels, vec!["10-1", "10-2", "10-3"]);
        assert_eq!(values, vec![0.0, 12.0, 0.0]);
    }

    #[test]
    fn category_sales_share_folds_remainder_into_top() {
        // 3×33.3=99.9 → 尾差 0.1 并入首项,Σ=100.0
        let rows = vec![
            (1, "A".to_string(), Decimal::from(1)),
            (2, "B".to_string(), Decimal::from(1)),
            (3, "C".to_string(), Decimal::from(1)),
        ];
        let items = build_category_sales(rows);
        let sum: f64 = items.iter().filter_map(|v| v["share"].as_f64()).sum();
        assert!((sum - 100.0).abs() < 1e-9, "share sum={sum}");
        assert_eq!(items[0]["share"].as_f64(), Some(33.4));
        assert_eq!(items[0]["category_id"], 1);
    }

    #[test]
    fn category_sales_empty_when_total_zero() {
        assert!(build_category_sales(Vec::new()).is_empty());
        assert!(build_category_sales(vec![(0, "Other".to_string(), Decimal::ZERO)]).is_empty());
    }

    #[test]
    fn top_products_blank_img_becomes_null() {
        let rows = vec![
            (5, "P5".to_string(), Some("".to_string()), 3, Decimal::from(9)),
            (6, "P6".to_string(), Some("  ".to_string()), 2, Decimal::from(8)),
            (7, "P7".to_string(), Some("a.png".to_string()), 1, Decimal::from(7)),
        ];
        let items = build_top_products(rows);
        assert!(items[0]["image_url"].is_null());
        assert!(items[1]["image_url"].is_null());
        assert_eq!(items[2]["image_url"], "a.png");
        assert_eq!(items[0]["sales"], 3);
    }
}
