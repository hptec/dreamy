//! checkout 报价域(order-flow-complete §4.3):锁汇→行价→运费选项→礼品包装→券→税费→恒等式→预计送达。
//! 对齐 CheckoutQuoteService / ShippingQuoteService / TaxCalculator / Money 工具。
//! 券(coupon 域)未迁移:coupon_code 传参时 quote 口径按无效处理(discount=0),迁移后接线。

use std::collections::HashMap;

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, QueryResult, Statement};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use common::country_catalog as cc;

use crate::service_category::{cat_err, CatalogError};

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[checkout] db error:{e}");
    CatalogError::new(500601)
}

fn field_err(fields: Vec<(&'static str, &'static str)>) -> CatalogError {
    CatalogError::field_validation(&fields)
}

const CURRENCIES: [&str; 5] = ["USD", "EUR", "CAD", "AUD", "GBP"];
/// 金额 HALF_UP 2 位的除数/乘数(基准 scale=2)
const SCALE_DIVISOR: f64 = 10000.0;
const DEFAULT_DDU_NOTICE: &str = "Import duties/taxes may apply upon delivery and are the recipient's responsibility.";

fn round2(x: f64) -> f64 {
    (x * 100.0).round() / 100.0
}

/// f64 HALF_UP 2 位字符串(JSON 数字保真)
fn money(x: f64) -> Value {
    Value::from((x * 100.0).round() / 100.0)
}

// ══════════════════ 请求/响应 ══════════════════

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct QuoteRequest {
    pub address_id: Option<i64>,
    pub country: Option<String>,
    pub currency: Option<String>,
    pub carrier: Option<String>,
    pub coupon_code: Option<String>,
    pub gift_wrap: Option<bool>,
    pub wedding_date: Option<String>,
    pub service_level: Option<i64>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct QuoteResponse {
    pub currency: String,
    /// 下单快照源(createOrder 用;quote 契约不输出)
    #[serde(skip_serializing)]
    pub lines_snapshot: Vec<LineSnapshot>,
    pub exchange_rate: f64,
    pub subtotal: f64,
    pub shipping_options: Vec<Value>,
    pub shipping_fee: f64,
    pub gift_wrap_fee: f64,
    pub discount_amount: f64,
    pub total_amount: f64,
    pub coupon_valid: Option<bool>,
    pub coupon_reason_code: Option<i64>,
    pub lead_time_warning: Option<bool>,
    pub max_lead_time_days: Option<i64>,
    pub dye_lot_product_ids: Vec<i64>,
    pub tax_amount: f64,
    pub tax_breakdown: Vec<Value>,
    pub incoterm: Option<i64>,
    pub duties_notice: bool,
    pub duties_notice_text: Option<String>,
    pub exchange_rate_locked_note: bool,
    pub service_level: Option<i64>,
    pub carrier_code: Option<String>,
    pub estimated_delivery_from: Option<String>,
    pub estimated_delivery_to: Option<String>,
    pub production_days: i64,
    pub country_code: Option<String>,
    pub region_code: Option<String>,
}

/// 下单行快照(quote 内部产出;createOrder 消费)
#[derive(Debug, Clone)]
pub struct LineSnapshot {
    pub product_id: i64,
    pub sku_id: Option<i64>,
    pub sku_version: Option<i64>,
    pub product_name: String,
    pub sku_code: Option<String>,
    pub color: Option<String>,
    pub size: Option<String>,
    pub qty: i64,
    pub unit_price: f64,
    pub img: Option<String>,
    pub custom_size_data: Option<Value>,
}

// ══════════════════ shipping quote(shippingrate 域核心)══════════════════

pub struct OptionQuote {
    pub carrier: String,
    pub fee_usd: f64,
    pub lead_time: Option<String>,
    pub carrier_code: String,
    pub service_level: i64,
    pub transit_days_min: Option<i64>,
    pub transit_days_max: Option<i64>,
}

/// GeoZoneResolver.resolve:country_code 优先,country 文本兜底,失败 → REST
pub fn resolve_zone(country_code: Option<&str>, country_text: Option<&str>) -> String {
    if let Some(code) = country_code {
        if cc::is_known_code(code) {
            if let Some(k) = cc::by_code(code) {
                return k.zone.to_string();
            }
        }
    }
    if let Some(text) = country_text {
        if let Some(code) = cc::resolve_code(text) {
            if let Some(k) = cc::by_code(code) {
                return k.zone.to_string();
            }
        }
    }
    "REST".to_string()
}

/// 计费(DEC-SHP-3):threshold NULL 或 subtotal < threshold → fee_under(NULL 计 0);
/// subtotal >= threshold → fee_over(NULL 计 0)
fn compute_fee(fee_under: f64, fee_over: Option<f64>, threshold: Option<f64>, subtotal_usd: f64) -> f64 {
    match threshold {
        None => fee_under,
        Some(t) if subtotal_usd < t => fee_under,
        Some(_) => fee_over.unwrap_or(0.0),
    }
}

/// zone × carrier × level 报价(承运商 id ASC × STANDARD→EXPRESS 稳定序;REST 兜底)
pub async fn quote_options(
    db: &DatabaseConnection,
    zone: &str,
    subtotal_usd: f64,
) -> Result<Vec<OptionQuote>, CatalogError> {
    let carriers = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, name, code, lead_time FROM carrier WHERE status = 1 ORDER BY id ASC",
        ))
        .await
        .map_err(db_err)?;
    let options = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT zone, carrier_code, service_level, fee_under, fee_over, threshold, transit_days_min, transit_days_max \
             FROM shipping_option WHERE enabled = 1",
        ))
        .await
        .map_err(db_err)?;
    struct Opt {
        fee_under: f64,
        fee_over: Option<f64>,
        threshold: Option<f64>,
        transit_min: Option<i64>,
        transit_max: Option<i64>,
    }
    let mut index: HashMap<(String, String, i64), Opt> = HashMap::new();
    for o in &options {
        let z: String = o.try_get("", "zone").unwrap_or_default();
        let c: String = o.try_get("", "carrier_code").unwrap_or_default();
        let l: i64 = o.try_get("", "service_level").unwrap_or(1);
        index.entry((z, c, l)).or_insert(Opt {
            fee_under: crate::cart::dec(o, "fee_under").unwrap_or(0.0),
            fee_over: crate::cart::dec(o, "fee_over"),
            threshold: crate::cart::dec(o, "threshold"),
            transit_min: o.try_get("", "transit_days_min").ok(),
            transit_max: o.try_get("", "transit_days_max").ok(),
        });
    }
    let mut quotes = vec![];
    for carrier in &carriers {
        let name: String = carrier.try_get("", "name").unwrap_or_default();
        let code: String = carrier.try_get("", "code").unwrap_or_default();
        let lead_time: Option<String> = carrier.try_get("", "lead_time").ok();
        for level in [1i64, 2] {
            // 精确 zone → ANY 兜底 → REST zone → REST+ANY
            let key_exact = (zone.to_string(), code.clone(), level);
            let key_any = (zone.to_string(), "*".to_string(), level);
            let key_rest = ("REST".to_string(), code.clone(), level);
            let key_rest_any = ("REST".to_string(), "*".to_string(), level);
            let opt = index
                .get(&key_exact)
                .or_else(|| index.get(&key_any))
                .or_else(|| {
                    if zone.eq_ignore_ascii_case("REST") {
                        None
                    } else {
                        index.get(&key_rest).or_else(|| index.get(&key_rest_any))
                    }
                });
            let Some(o) = opt else { continue };
            let fee = compute_fee(o.fee_under, o.fee_over, o.threshold, subtotal_usd);
            quotes.push(OptionQuote {
                carrier: name.clone(),
                fee_usd: round2(fee),
                lead_time: lead_time.clone(),
                carrier_code: code.clone(),
                service_level: level,
                transit_days_min: o.transit_min,
                transit_days_max: o.transit_max,
            });
        }
    }
    Ok(quotes)
}

/// 选中:请求 carrier(code/name 不敏感)命中 → 该承运商内按等级(缺省 STANDARD),无该级取最便宜
pub fn select_quote<'a>(
    quotes: &'a [OptionQuote],
    requested_carrier: Option<&str>,
    requested_level: Option<i64>,
) -> Option<&'a OptionQuote> {
    let Some(carrier) = requested_carrier else {
        return None;
    };
    let hits: Vec<&OptionQuote> = quotes
        .iter()
        .filter(|q| {
            q.carrier.eq_ignore_ascii_case(carrier) || q.carrier_code.eq_ignore_ascii_case(carrier)
        })
        .collect();
    if hits.is_empty() {
        return None;
    }
    let level = requested_level.unwrap_or(1);
    hits.iter()
        .copied()
        .find(|q| q.service_level == level)
        .or_else(|| hits.iter().copied().min_by(|a, b| a.fee_usd.partial_cmp(&b.fee_usd).unwrap()))
}

/// pickSelected(quote 口径缺省:请求 carrier 未命中 → STANDARD 最便宜;EXPRESS → 最便宜 EXPRESS)
pub fn pick_selected<'a>(quotes: &'a [OptionQuote], requested_level: Option<i64>) -> Option<&'a OptionQuote> {
    if quotes.is_empty() {
        return None;
    }
    let level = requested_level.unwrap_or(1);
    let same_level: Vec<&OptionQuote> = quotes.iter().filter(|q| q.service_level == level).collect();
    let pool: &[&OptionQuote] = if same_level.is_empty() {
        &quotes.iter().collect::<Vec<_>>()
    } else {
        &same_level
    };
    pool.iter().copied().min_by(|a, b| a.fee_usd.partial_cmp(&b.fee_usd).unwrap())
}

// ══════════════════ 汇率(锁汇含 spread)══════════════════

pub async fn resolve_rate(db: &DatabaseConnection, currency: &str) -> Result<f64, CatalogError> {
    if currency == "USD" {
        return Ok(1.0);
    }
    let row = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT rate FROM exchange_rate WHERE currency = ?",
            [currency.into()],
        ))
        .await
        .map_err(db_err)?;
    row.and_then(|r| {
        // decimal(12,6) → sea_orm 返回 rust_decimal::Decimal;统一转 f64
        r.try_get::<rust_decimal::Decimal>("", "rate")
            .map(|d| d.to_string().parse::<f64>().unwrap_or(1.0))
            .ok()
    })
    .ok_or(CatalogError::new(422605))
}

pub fn apply_spread(rate: f64, spread_scaled: i64) -> f64 {
    if spread_scaled == 0 {
        return round6(rate);
    }
    round6(rate * (1.0 + spread_scaled as f64 / 10000.0))
}

fn round6(x: f64) -> f64 {
    (x * 1_000_000.0).round() / 1_000_000.0
}

// ══════════════════ 税费(TaxCalculator 核心)══════════════════

pub struct TaxQuote {
    pub tax_amount: f64,
    pub breakdown: Vec<Value>,
    pub incoterm: i64,
    pub duties_notice: bool,
    pub notice_text: Option<String>,
    pub policy_defined: bool,
}

/// 规则选择:country 匹配 + 生效窗口;region 精确覆盖国家级;每税种一条
async fn select_tax_rules(
    db: &DatabaseConnection,
    country_code: &str,
    region_code: Option<&str>,
    today: chrono::NaiveDate,
) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT country_code, region, tax_type, rate_scaled, applies_to_shipping, threshold_usd, label, effective_from, effective_to \
             FROM tax_rule WHERE enabled = 1",
        ))
        .await
        .map_err(db_err)?;
    let region = region_code.unwrap_or("").trim().to_uppercase();
    struct Chosen {
        applies_shipping: bool,
        threshold: Option<f64>,
        rate_scaled: i64,
        label: Option<String>,
        tax_type: i64,
    }
    let mut chosen: HashMap<i64, Chosen> = HashMap::new();
    let mut chosen_region_exact: HashMap<i64, bool> = HashMap::new();
    for r in &rows {
        let rule_cc: String = r.try_get("", "country_code").unwrap_or_default();
        if !rule_cc.eq_ignore_ascii_case(country_code) {
            continue;
        }
        // 生效窗口
        let from: Option<String> = r.try_get("", "effective_from").ok();
        let to: Option<String> = r.try_get("", "effective_to").ok();
        let today_s = today.format("%Y-%m-%d").to_string();
        if let Some(f) = &from {
            if today_s.as_str() < f.as_str() {
                continue;
            }
        }
        if let Some(t) = &to {
            if today_s.as_str() > t.as_str() {
                continue;
            }
        }
        let rule_region: String = r.try_get::<String>("", "region").unwrap_or_default().trim().to_uppercase();
        let country_level = rule_region.is_empty();
        let region_exact = !country_level && rule_region == region;
        if !country_level && !region_exact {
            continue;
        }
        let tax_type = r.try_get::<i64>("", "tax_type").unwrap_or(1);
        let existing_exact = chosen_region_exact.get(&tax_type).copied().unwrap_or(false);
        if chosen.get(&tax_type).is_none() || (region_exact && !existing_exact) {
            chosen.insert(
                tax_type,
                Chosen {
                    applies_shipping: r.try_get("", "applies_to_shipping").unwrap_or(false),
                    threshold: r.try_get("", "threshold_usd").ok(),
                    rate_scaled: r.try_get("", "rate_scaled").unwrap_or(0),
                    label: r.try_get("", "label").ok(),
                    tax_type,
                },
            );
            chosen_region_exact.insert(tax_type, region_exact);
        }
    }
    Ok(chosen
        .into_values()
        .map(|c| {
            serde_json::json!({
                "applies_to_shipping": c.applies_shipping,
                "threshold_usd": c.threshold,
                "rate_scaled": c.rate_scaled,
                "label": c.label,
                "tax_type": c.tax_type,
            })
        })
        .collect())
}

/// 税费计算(DDP 才计税;DDU 恒 0)
pub async fn tax_compute(
    db: &DatabaseConnection,
    country_code: Option<&str>,
    region_code: Option<&str>,
    subtotal_usd: f64,
    discount_usd: f64,
    shipping_usd: f64,
    rate: f64,
) -> Result<TaxQuote, CatalogError> {
    let Some(ccode) = country_code.map(str::trim).map(str::to_uppercase).filter(|s| !s.is_empty()) else {
        return Ok(TaxQuote {
            tax_amount: 0.0,
            breakdown: vec![],
            incoterm: 2,
            duties_notice: true,
            notice_text: Some(DEFAULT_DDU_NOTICE.to_string()),
            policy_defined: false,
        });
    };
    let policy = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT incoterm, duties_notice, notice_text FROM tax_destination_policy WHERE country_code = ?",
            [ccode.clone().into()],
        ))
        .await
        .map_err(db_err)?;
    let Some(p) = policy else {
        return Ok(TaxQuote {
            tax_amount: 0.0,
            breakdown: vec![],
            incoterm: 2,
            duties_notice: true,
            notice_text: Some(DEFAULT_DDU_NOTICE.to_string()),
            policy_defined: false,
        });
    };
    let incoterm: i64 = p.try_get("", "incoterm").unwrap_or(2);
    let notice = !p.try_get::<bool>("", "duties_notice").unwrap_or(true) == false || true; // 非 FALSE 即 true
    let notice = p.try_get::<Option<bool>>("", "duties_notice").ok().flatten().map(|n| n != false).unwrap_or(true);
    let text: Option<String> = p.try_get("", "notice_text").ok();
    let text = text.or_else(|| if notice { Some(DEFAULT_DDU_NOTICE.to_string()) } else { None });
    if incoterm != 1 {
        // DDU:不计税
        return Ok(TaxQuote {
            tax_amount: 0.0,
            breakdown: vec![],
            incoterm: 2,
            duties_notice: notice,
            notice_text: text,
            policy_defined: true,
        });
    }
    let today = chrono::Utc::now().date_naive();
    let rules = select_tax_rules(db, &ccode, region_code, today).await?;
    let goods_base_usd = (subtotal_usd - discount_usd).max(0.0);
    let mut breakdown = vec![];
    let mut total = 0.0;
    for r in &rules {
        let applies_shipping = r["applies_to_shipping"].as_bool().unwrap_or(false);
        let base_usd = if applies_shipping { goods_base_usd + shipping_usd } else { goods_base_usd };
        let threshold = r["threshold_usd"].as_f64();
        if let Some(t) = threshold {
            if base_usd < t {
                continue;
            }
        }
        let rate_scaled = r["rate_scaled"].as_i64().unwrap_or(0);
        let base_ccy = round2(base_usd * rate);
        let amount = round2(base_ccy * rate_scaled as f64 / SCALE_DIVISOR);
        let label = r["label"]
            .as_str()
            .filter(|l| !l.is_empty())
            .map(String::from)
            .unwrap_or_else(|| {
                let tt = r["tax_type"].as_i64().unwrap_or(1);
                match tt {
                    1 => "VAT".into(),
                    2 => "GST".into(),
                    3 => "Sales Tax".into(),
                    _ => "Duty".into(),
                }
            });
        breakdown.push(serde_json::json!({
            "tax_type": r["tax_type"],
            "label": label,
            "rate_scaled": rate_scaled,
            "base_amount": money(base_ccy),
            "amount": money(amount),
        }));
        total += amount;
    }
    Ok(TaxQuote {
        tax_amount: round2(total),
        breakdown,
        incoterm: 1,
        duties_notice: notice,
        notice_text: if notice { text } else { None },
        policy_defined: true,
    })
}

// ══════════════════ quote 主链 ═══════════════════

/// 制作周期 = max(商品 lead_time 最大值, 配置缺省 21)
fn production_days(lines: &[(Option<i64>, i64)], config_default: i64) -> i64 {
    let max_lead = lines.iter().filter_map(|(lead, _)| *lead).max().unwrap_or(0);
    std::cmp::max(max_lead, config_default)
}

/// E-quoteCheckout(strict=false 报价口径)
pub async fn quote(
    db: &DatabaseConnection,
    customer_id: i64,
    req: &QuoteRequest,
) -> Result<QuoteResponse, CatalogError> {
    let currency = req
        .currency
        .as_deref()
        .map(str::trim)
        .map(str::to_uppercase)
        .unwrap_or_default();
    let currency_out = currency.clone();
    if !CURRENCIES.contains(&currency.as_str()) {
        return Err(CatalogError::new(422605)); // CURRENCY_NOT_SUPPORTED
    }
    // V-TRD-016 address_id 与 country 至少其一
    let (address_country, address_cc, address_region) = if let Some(aid) = req.address_id {
        let addr = db
            .query_one(Statement::from_sql_and_values(
                DbBackend::MySql,
                "SELECT country, country_code, region_code FROM address WHERE id = ? AND customer_id = ?",
                [aid.into(), customer_id.into()],
            ))
            .await
            .map_err(db_err)?
            .ok_or(CatalogError::new(404602))?;
        (
            Some(addr.try_get::<String>("", "country").unwrap_or_default()),
            addr.try_get::<String>("", "country_code").ok(),
            addr.try_get::<String>("", "region_code").ok(),
        )
    } else if req.country.as_deref().map(str::trim).filter(|s| !s.is_empty()).is_some() {
        (Some(req.country.clone().unwrap()), None, None)
    } else {
        return Err(field_err(vec![("address_id", "required")]));
    };
    // STEP-TRD-01 cart + 快照
    let cart_rows = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, product_id, sku_id, qty, custom_size_data FROM cart_item WHERE customer_id = ? ORDER BY id ASC",
            [customer_id.into()],
        ))
        .await
        .map_err(db_err)?;
    if cart_rows.is_empty() {
        return Err(field_err(vec![("reason", "cart_empty")]));
    }
    // 汇率(锁汇)
    let config = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            "SELECT gift_wrap_fee_usd, exchange_rate_spread_scaled, production_days_default FROM checkout_config WHERE id = 1".to_string(),
        ))
        .await
        .map_err(db_err)?;
    let spread = config
        .as_ref()
        .and_then(|c| c.try_get::<i64>("", "exchange_rate_spread_scaled").ok())
        .unwrap_or(0);
    let gift_wrap_fee_usd = config
        .as_ref()
        .and_then(|c| crate::cart::dec(c, "gift_wrap_fee_usd"))
        .unwrap_or(0.0);
    let production_default = config
        .as_ref()
        .and_then(|c| c.try_get::<i64>("", "production_days_default").ok())
        .unwrap_or(21);
    let base_rate = resolve_rate(db, &currency).await?;
    let rate = if currency == "USD" { 1.0 } else { apply_spread(base_rate, spread) };
    // 行价(下架行 quote 口径剔除)
    let mut subtotal = 0.0f64;
    let mut subtotal_usd = 0.0f64;
    let mut lines: Vec<(Option<i64>, i64)> = vec![]; // (lead_time, qty)
    let mut priced_lines: Vec<Value> = vec![];
    let mut lines_snapshot: Vec<LineSnapshot> = vec![];
    for r in &cart_rows {
        let pid = r.try_get::<i64>("", "product_id").unwrap_or(0);
        let qty = r.try_get::<i64>("", "qty").unwrap_or(0);
        let sku_id = r.try_get::<i64>("", "sku_id").ok();
        let custom_data = r.try_get::<Value>("", "custom_size_data").ok();
        let Some(product) = get_product_brief_pub(db, pid).await? else {
            continue; // quote 口径:下架/缺失行剔除
        };
        if !product.published() {
            continue;
        }
        // 覆盖价优先,否则 USD×rate
        let unit_price = match &product.multi_currency_prices {
            Some(Value::Object(m)) => m
                .get(&currency)
                .and_then(|v| v.as_f64())
                .map(round2)
                .unwrap_or_else(|| round2(product.price * rate)),
            _ => round2(product.price * rate),
        };
        subtotal += unit_price * qty as f64;
        subtotal_usd += product.price * qty as f64;
        lines.push((product.lead_time_days, qty));
        priced_lines.push(serde_json::json!({
            "product_id": pid, "qty": qty, "unit_price": money(unit_price),
        }));
        // 快照行(sku 信息取快照)
        let (sku_code, color, size, sku_version) = if let Some(sid) = sku_id {
            match crate::cart::get_sku(db, sid).await? {
                Some(s) => (Some(s.sku_code.clone()), s.color.clone(), s.size.clone(), Some(s.version)),
                None => (None, None, None, None),
            }
        } else {
            (None, None, None, None)
        };
        let img = product_image_url(db, pid).await?;
        lines_snapshot.push(LineSnapshot {
            product_id: pid,
            sku_id,
            sku_version,
            product_name: product.name.clone(),
            sku_code,
            color,
            size,
            qty,
            unit_price,
            img,
            custom_size_data: custom_data.filter(|v| !v.is_null()),
        });
    }
    let subtotal = round2(subtotal);
    let subtotal_usd = round2(subtotal_usd);
    if priced_lines.is_empty() {
        return Err(field_err(vec![("reason", "cart_empty")]));
    }
    // STEP-TRD-03/04 运费选项
    let zone = resolve_zone(address_cc.as_deref(), address_country.as_deref());
    let quotes = quote_options(db, &zone, subtotal_usd).await?;
    let requested_level = req.service_level.filter(|l| *l == 1 || *l == 2);
    let selected = select_quote(&quotes, req.carrier.as_deref(), requested_level)
        .or_else(|| pick_selected(&quotes, requested_level));
    let mut shipping_options: Vec<Value> = vec![];
    let mut shipping_fee = 0.0f64;
    let mut shipping_fee_usd = 0.0f64;
    let mut selected_carrier_code: Option<String> = None;
    let mut selected_service_level: Option<i64> = None;
    let mut transit_min: Option<i64> = None;
    let mut transit_max: Option<i64> = None;
    let production_days = production_days(&lines, production_default);
    let today = chrono::Utc::now().date_naive();
    for q in &quotes {
        let is_selected = selected.map(|s| std::ptr::eq(s, q)).unwrap_or(false);
        let fee = round2(q.fee_usd * rate);
        let eta_from = q
            .transit_days_min
            .map(|t| (today + chrono::Duration::days(production_days + t)).format("%Y-%m-%d").to_string());
        let eta_to = q
            .transit_days_max
            .map(|t| (today + chrono::Duration::days(production_days + t)).format("%Y-%m-%d").to_string());
        shipping_options.push(serde_json::json!({
            "carrier": q.carrier,
            "fee": money(fee),
            "lead_time": q.lead_time,
            "selected": is_selected,
            "carrier_code": q.carrier_code,
            "service_level": q.service_level,
            "transit_days_min": q.transit_days_min,
            "transit_days_max": q.transit_days_max,
            "estimated_delivery_from": eta_from,
            "estimated_delivery_to": eta_to,
        }));
        if is_selected {
            shipping_fee = fee;
            shipping_fee_usd = q.fee_usd;
            selected_carrier_code = Some(q.carrier_code.clone());
            selected_service_level = Some(q.service_level);
            transit_min = q.transit_days_min;
            transit_max = q.transit_days_max;
        }
    }
    // STEP-TRD-05 礼品包装
    let gift_wrap_fee = if req.gift_wrap.unwrap_or(false) { round2(gift_wrap_fee_usd * rate) } else { 0.0 };
    // STEP-TRD-06 券(coupon 域未迁:quote 口径无效不阻断,discount=0)
    let coupon_valid: Option<bool> = req.coupon_code.as_ref().map(|_| false);
    let mut discount_amount = 0.0f64;
    // STEP-TRD-07 税费
    let discount_usd = if rate > 0.0 { round2(discount_amount / rate) } else { 0.0 };
    let tax = tax_compute(
        db,
        address_cc.as_deref(),
        address_region.as_deref(),
        subtotal_usd,
        discount_usd,
        shipping_fee_usd,
        rate,
    )
    .await?;
    // STEP-TRD-08 金额恒等式
    let total_amount = round2(subtotal + shipping_fee + gift_wrap_fee - discount_amount + tax.tax_amount);
    // STEP-TRD-09 交期
    let max_lead = lines.iter().filter_map(|(l, _)| *l).max();
    let lead_time_warning = req
        .wedding_date
        .as_deref()
        .and_then(|w| chrono::NaiveDate::parse_from_str(w, "%Y-%m-%d").ok())
        .map(|wd| {
            max_lead
                .map(|ml| {
                    let today_d = chrono::Utc::now().date_naive();
                    wd >= today_d && (today_d + chrono::Duration::days(ml)) > wd
                })
                .unwrap_or(false)
        });
    // STEP-TRD-10 预计送达
    let eta_from = transit_min.map(|t| (today + chrono::Duration::days(production_days + t)).format("%Y-%m-%d").to_string());
    let eta_to = transit_max.map(|t| (today + chrono::Duration::days(production_days + t)).format("%Y-%m-%d").to_string());
    // 规范码:地址 country_code 优先,文本兜底
    let country_code = address_cc.clone().or_else(|| {
        address_country.as_deref().and_then(cc::resolve_code).map(String::from)
    });
    Ok(QuoteResponse {
        currency: currency_out,
        lines_snapshot,
        exchange_rate: rate,
        subtotal,
        shipping_options,
        shipping_fee,
        gift_wrap_fee,
        discount_amount,
        total_amount,
        coupon_valid,
        coupon_reason_code: None,
        lead_time_warning: lead_time_warning.map(Some).unwrap_or(None),
        max_lead_time_days: max_lead,
        dye_lot_product_ids: vec![],
        tax_amount: tax.tax_amount,
        tax_breakdown: tax.breakdown,
        incoterm: Some(tax.incoterm),
        duties_notice: tax.duties_notice,
        duties_notice_text: tax.notice_text,
        exchange_rate_locked_note: currency != "USD",
        service_level: selected_service_level,
        carrier_code: selected_carrier_code,
        estimated_delivery_from: eta_from,
        estimated_delivery_to: eta_to,
        production_days,
        country_code,
        region_code: address_region,
    })
}

/// product brief 公共重导(cart 模块已有;此为 quote 用的直查)
async fn get_product_brief_pub(db: &DatabaseConnection, id: i64) -> Result<Option<crate::cart::ProductBrief>, CatalogError> {
    crate::cart::get_product_brief(db, id).await
}

/// 商品主图(gallery sort=0 兜底 gallery 首张)
async fn product_image_url(db: &DatabaseConnection, product_id: i64) -> Result<Option<String>, CatalogError> {
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT url FROM product_image WHERE product_id = {} AND kind = 1 ORDER BY (sort = 0) DESC, sort ASC, id ASC LIMIT 1",
                product_id
            ),
        ))
        .await
        .map_err(db_err)?;
    Ok(row.and_then(|r| r.try_get::<String>("", "url").ok()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fee_threshold_semantics() {
        // threshold NULL → fee_under;subtotal < threshold → fee_under;>= → fee_over
        assert_eq!(compute_fee(27.0, Some(43.2), Some(400.0), 300.0), 27.0);
        assert_eq!(compute_fee(27.0, Some(43.2), Some(400.0), 400.0), 43.2);
        assert_eq!(compute_fee(9.0, None, None, 500.0), 9.0, "threshold NULL 恒 under");
        assert_eq!(compute_fee(27.0, None, Some(400.0), 500.0), 0.0, "fee_over NULL 计 0");
    }

    #[test]
    fn spread_semantics() {
        assert_eq!(apply_spread(1.36, 0), 1.36);
        assert_eq!(apply_spread(1.36, 100), round6(1.36 * 1.01), "100 = +1%");
    }

    #[test]
    fn zone_resolution_priority() {
        assert_eq!(resolve_zone(Some("DE"), Some("Germany")), "EUROPE");
        assert_eq!(resolve_zone(None, Some("USA")), "NORTH_AMERICA");
        assert_eq!(resolve_zone(None, Some("Nowhere")), "REST");
    }

    #[test]
    fn money_rounding() {
        // 已知差异:Java BigDecimal HALF_UP vs f64 二进制精度(10.005 实际存储 10.00499999 → 10.0;
        // 但 10.005f64 实际略大于 .005 → 10.01)。金额精度最终迁移 rust_decimal(见 TODO-ROUND5)。
        // 本测试锁定 round2 的确定性行为防回归。
        assert_eq!(round2(10.004), 10.0);
        assert_eq!(round2(10.006), 10.01);
        assert_eq!(money(27.0), Value::from(27.0));
    }
}
