//! cart 域(trading-api-detail §1,FLOW-P04,决策 8):双模式 guard/合并判定/现货超量 409601/
//! 匿名车合并(幂等闸+截断)/定制哈希。对齐 StoreCartService;错误码 404603/409601/422604/422601/404501。

use std::collections::{HashMap, HashSet};

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement, TransactionTrait};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::service_category::{cat_err, CatalogError};

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[cart] db error:{e}");
    CatalogError::new(500601)
}

/// decimal(12,2)/(12,6) 列 → f64(Decimal 中转;直接 f64 try_get 恒失败)
pub fn dec(r: &sea_orm::QueryResult, col: &str) -> Option<f64> {
    r.try_get::<rust_decimal::Decimal>("", col)
        .ok()
        .and_then(|d| d.to_string().parse::<f64>().ok())
}

fn trading(code: i32) -> CatalogError {
    CatalogError::new(code)
}

fn field_err(fields: Vec<(&'static str, &'static str)>) -> CatalogError {
    CatalogError::field_validation(&fields)
}

const TRUNCATE_KEEP_MIN: i64 = 1;

// ══════════════════ catalog 快照(跨域 port;迁移期直查 identity 库)══════════════════

#[derive(Debug, Clone)]
pub struct SkuBrief {
    pub id: i64,
    pub product_id: i64,
    pub sku_code: String,
    pub color: Option<String>,
    pub size: Option<String>,
    pub stock: Option<i64>,
    pub version: i64,
}

#[derive(Debug, Clone)]
pub struct ProductBrief {
    pub id: i64,
    pub slug: String,
    pub name: String,
    pub price: f64,
    pub compare_at: Option<f64>,
    pub multi_currency_prices: Option<Value>,
    pub image_url: Option<String>,
    pub lead_time_days: Option<i64>,
    pub rush_available: bool,
    pub custom_size_available: bool,
    pub status: i64,
}

impl ProductBrief {
    pub fn published(&self) -> bool {
        self.status == 2
    }
}

pub async fn get_product_brief(db: &DatabaseConnection, id: i64) -> Result<Option<ProductBrief>, CatalogError> {
    let row = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, slug, name, price, compare_at, multi_currency_prices, lead_time_days, rush_available, custom_size_available, status FROM product WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?;
    Ok(row.map(|r| {
        let pid = r.try_get::<i64>("", "id").unwrap_or(0);
        ProductBrief {
            id: pid,
            slug: r.try_get::<String>("", "slug").unwrap_or_default(),
            name: r.try_get::<String>("", "name").unwrap_or_default(),
            price: dec(&r, "price").unwrap_or(0.0),
            compare_at: dec(&r, "compare_at"),
            multi_currency_prices: r.try_get::<Value>("", "multi_currency_prices").ok(),
            image_url: None, // 主图由 assemble 批查填充
            lead_time_days: r.try_get::<i64>("", "lead_time_days").ok(),
            rush_available: r.try_get::<bool>("", "rush_available").unwrap_or(false),
            custom_size_available: r.try_get::<bool>("", "custom_size_available").unwrap_or(false),
            status: r.try_get::<i64>("", "status").unwrap_or(1),
        }
    }))
}

pub async fn get_sku(db: &DatabaseConnection, sku_id: i64) -> Result<Option<SkuBrief>, CatalogError> {
    let row = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, product_id, sku_code, color, size, stock, version FROM sku WHERE id = ?",
            [sku_id.into()],
        ))
        .await
        .map_err(db_err)?;
    Ok(row.map(|r| SkuBrief {
        id: r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        product_id: r.try_get::<u64>("", "product_id").map(|v| v as i64).unwrap_or(0),
        sku_code: r.try_get::<String>("", "sku_code").unwrap_or_default(),
        color: r.try_get::<String>("", "color").ok(),
        size: r.try_get::<String>("", "size").ok(),
        stock: r.try_get::<i64>("", "stock").ok(),
        version: r.try_get::<i64>("", "version").unwrap_or(0),
    }))
}

// ══════════════════ DTO ══════════════════

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct CustomSizeData {
    pub bust: Option<f64>,
    pub waist: Option<f64>,
    pub hips: Option<f64>,
    pub hollow_to_floor: Option<f64>,
    pub height: Option<f64>,
}

impl CustomSizeData {
    /// 定制哈希(对齐 TradingParams.customSizeHash:字段定序后 SHA-256)
    pub fn hash(&self) -> String {
        use std::fmt::Write;
        let mut raw = String::new();
        for v in [&self.bust, &self.waist, &self.hips, &self.hollow_to_floor, &self.height] {
            match v {
                Some(n) => {
                    let _ = write!(raw, "{};", n);
                }
                None => raw.push_str("-;"),
            }
        }
        use sha2::Digest;
        format!("{:x}", sha2::Sha256::digest(raw.as_bytes()))
    }
    pub fn to_value(&self) -> Value {
        serde_json::json!({
            "bust": self.bust, "waist": self.waist, "hips": self.hips,
            "hollow_to_floor": self.hollow_to_floor, "height": self.height,
        })
    }
    /// 完整性:四主尺寸必填(height 可选;V-TRD-005)
    pub fn complete(&self) -> bool {
        self.bust.is_some() && self.waist.is_some() && self.hips.is_some() && self.hollow_to_floor.is_some()
    }
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct CartItemCreate {
    pub product_id: Option<i64>,
    pub sku_id: Option<i64>,
    pub qty: Option<i64>,
    pub custom_size_data: Option<CustomSizeData>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct CartItemDto {
    pub id: i64,
    pub product_id: i64,
    pub sku_id: Option<i64>,
    pub qty: i64,
    pub custom_size_data: Option<Value>,
    pub product: Option<Value>,
    pub sku: Option<Value>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct CartResponse {
    pub items: Vec<CartItemDto>,
    pub dye_lot_product_ids: Vec<i64>,
    pub merged_truncated_item_ids: Option<Vec<i64>>,
}

struct ValidatedItem {
    product_id: i64,
    sku: Option<SkuBrief>,
    qty: i64,
    custom_size_data: Option<CustomSizeData>,
    custom_hash: Option<String>,
}

/// V-TRD-002~005 单条目校验
async fn validate_item(db: &DatabaseConnection, req: &CartItemCreate) -> Result<ValidatedItem, CatalogError> {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let Some(product_id) = req.product_id else {
        return Err(field_err(vec![("product_id", "required")]));
    };
    let qty = match req.qty {
        Some(q) if q < 1 => {
            fields.push(("qty", "range_invalid"));
            1
        }
        None => {
            fields.push(("qty", "range_invalid"));
            1
        }
        Some(q) => q,
    };
    if let Some(c) = &req.custom_size_data {
        if !c.complete() {
            fields.push(("custom_size_data", "incomplete"));
        }
    }
    if !fields.is_empty() {
        return Err(field_err(fields));
    }
    let product = get_product_brief(db, product_id)
        .await?
        .filter(|p| p.published())
        .ok_or(CatalogError::new(404501))?;
    // V-TRD-004 双模式 XOR guard
    let custom_mode = req.custom_size_data.is_some();
    if custom_mode {
        if req.sku_id.is_some() || !product.custom_size_available {
            return Err(trading(422604)); // SKU_REQUIRED
        }
        let data = req.custom_size_data.clone().unwrap();
        let hash = data.hash();
        Ok(ValidatedItem { product_id, sku: None, qty, custom_size_data: Some(data), custom_hash: Some(hash) })
    } else {
        let Some(sku_id) = req.sku_id else {
            return Err(trading(422604));
        };
        let sku = get_sku(db, sku_id)
            .await?
            .filter(|s| s.product_id == product.id)
            .ok_or(trading(422604))?;
        Ok(ValidatedItem { product_id, sku: Some(sku), qty, custom_size_data: None, custom_hash: None })
    }
}

/// 合并判定 + UPSERT(同 sku / 同定制哈希 → 累加)
async fn upsert_item(
    tx: &sea_orm::DatabaseTransaction,
    customer_id: i64,
    item: &ValidatedItem,
) -> Result<(i64, i64), CatalogError> {
    let sku_id = item.sku.as_ref().map(|s| s.id);
    let target = tx
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, qty FROM cart_item WHERE customer_id = ? AND ((sku_id IS NOT NULL AND sku_id = ?) OR (sku_id IS NULL AND custom_size_hash = ?)) LIMIT 1",
            [
                customer_id.into(),
                sku_id.unwrap_or(0).into(),
                item.custom_hash.clone().unwrap_or_default().into(),
            ],
        ))
        .await
        .map_err(db_err)?;
    if let Some(t) = target {
        let tid = t.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
        let merged = t.try_get::<i64>("", "qty").unwrap_or(0) + item.qty;
        tx.execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "UPDATE cart_item SET qty = ?, updated_at = NOW(3) WHERE id = ?",
            [merged.into(), tid.into()],
        ))
        .await
        .map_err(db_err)?;
        return Ok((tid, merged));
    }
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO cart_item(customer_id, product_id, sku_id, qty, custom_size_data, custom_size_hash, created_at, updated_at) VALUES (?,?,?,?,?,?,NOW(3),NOW(3))",
        [
            customer_id.into(),
            item.product_id.into(),
            sku_id.into(),
            item.qty.into(),
            item.custom_size_data.as_ref().map(|d| d.to_value().to_string()).into(),
            item.custom_hash.clone().into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    let id = tx
        .query_one(Statement::from_string(
            DbBackend::MySql,
            "SELECT LAST_INSERT_ID() AS id".to_string(),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<u64>("", "id").ok())
        .unwrap_or(0);
    Ok((id as i64, item.qty))
}

/// CartResponse 装配(批量快照防 N+1;MAP-TRD-001)
async fn assemble_cart(
    db: &DatabaseConnection,
    customer_id: i64,
    truncated_ids: Option<Vec<i64>>,
) -> Result<CartResponse, CatalogError> {
    let items = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, product_id, sku_id, qty, custom_size_data FROM cart_item WHERE customer_id = ? ORDER BY id ASC",
            [customer_id.into()],
        ))
        .await
        .map_err(db_err)?;
    let product_ids: Vec<i64> = items
        .iter()
        .filter_map(|r| r.try_get::<u64>("", "product_id").ok().map(|v| v as i64))
        .collect::<HashSet<_>>()
        .into_iter()
        .collect();
    // 商品快照批查
    let mut products: HashMap<i64, Value> = HashMap::new();
    if !product_ids.is_empty() {
        let pid_list = product_ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
        let rows = db
            .query_all(Statement::from_string(
                DbBackend::MySql,
                format!(
                    "SELECT p.id, p.slug, p.name, p.price, p.compare_at, p.multi_currency_prices, p.lead_time_days, p.rush_available, p.status, \
                     (SELECT url FROM product_image i WHERE i.product_id = p.id AND i.kind = 1 ORDER BY i.sort ASC, i.id ASC LIMIT 1) AS image_url \
                     FROM product p WHERE p.id IN ({})",
                    pid_list
                ),
            ))
            .await
            .map_err(db_err)?;
        for r in rows {
            let pid = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            products.insert(
                pid,
                serde_json::json!({
                    "id": pid,
                    "slug": r.try_get::<String>("", "slug").unwrap_or_default(),
                    "name": r.try_get::<String>("", "name").unwrap_or_default(),
                    "price": crate::cart::dec(&r, "price").unwrap_or(0.0),
                    "compare_at": crate::cart::dec(&r, "compare_at"),
                    "multi_currency_prices": r.try_get::<Value>("", "multi_currency_prices").ok(),
                    "image_url": r.try_get::<String>("", "image_url").ok(),
                    "lead_time_days": r.try_get::<i64>("", "lead_time_days").ok(),
                    "rush_available": r.try_get::<bool>("", "rush_available").unwrap_or(false),
                    "status": r.try_get::<i64>("", "status").unwrap_or(1),
                }),
            );
        }
    }
    // SKU 快照批查
    let sku_ids: Vec<i64> = items
        .iter()
        .filter_map(|r| r.try_get::<u64>("", "sku_id").ok().map(|v| v as i64))
        .collect::<HashSet<_>>()
        .into_iter()
        .collect();
    let mut skus: HashMap<i64, Value> = HashMap::new();
    if !sku_ids.is_empty() {
        let sid_list = sku_ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
        let rows = db
            .query_all(Statement::from_string(
                DbBackend::MySql,
                format!(
                    "SELECT id, sku_code, color, size, stock FROM sku WHERE id IN ({})",
                    sid_list
                ),
            ))
            .await
            .map_err(db_err)?;
        for r in rows {
            let sid = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            skus.insert(
                sid,
                serde_json::json!({
                    "id": sid,
                    "sku_code": r.try_get::<String>("", "sku_code").unwrap_or_default(),
                    "color": r.try_get::<String>("", "color").ok(),
                    "size": r.try_get::<String>("", "size").ok(),
                    "stock": r.try_get::<i64>("", "stock").ok(),
                }),
            );
        }
    }
    let dtos: Vec<CartItemDto> = items
        .iter()
        .map(|r| {
            let id = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            let pid = r.try_get::<u64>("", "product_id").map(|v| v as i64).unwrap_or(0);
            let sid = r.try_get::<u64>("", "sku_id").ok().map(|v| v as i64);
            CartItemDto {
                id,
                product_id: pid,
                sku_id: sid,
                qty: r.try_get::<i64>("", "qty").unwrap_or(0),
                custom_size_data: r.try_get::<Value>("", "custom_size_data").ok(),
                product: products.get(&pid).cloned(),
                sku: sid.and_then(|s| skus.get(&s).cloned()),
            }
        })
        .collect();
    Ok(CartResponse {
        items: dtos,
        dye_lot_product_ids: vec![], // dye lot port 随营销域接线
        merged_truncated_item_ids: truncated_ids,
    })
}

/// E-getCart
pub async fn get_cart(db: &DatabaseConnection, customer_id: i64) -> Result<CartResponse, CatalogError> {
    assemble_cart(db, customer_id, None).await
}

/// E-addCartItem(STEP-TRD-01 现货校验提示 409601)
pub async fn add_item(
    db: &DatabaseConnection,
    customer_id: i64,
    req: CartItemCreate,
) -> Result<CartResponse, CatalogError> {
    let item = validate_item(db, &req).await?;
    if let Some(sku) = &item.sku {
        // 既有同 SKU 条目 + 本次 qty > stock → 409601
        let existing = db
            .query_one(Statement::from_sql_and_values(
                DbBackend::MySql,
                "SELECT qty FROM cart_item WHERE customer_id = ? AND sku_id = ? LIMIT 1",
                [customer_id.into(), sku.id.into()],
            ))
            .await
            .map_err(db_err)?;
        let existing_qty = existing.and_then(|r| r.try_get::<i64>("", "qty").ok()).unwrap_or(0);
        let stock = sku.stock.unwrap_or(0);
        if existing_qty + item.qty > stock {
            let mut e = trading(409601);
            e.details = Some(serde_json::json!({"sku_id": sku.id}));
            return Err(e);
        }
    }
    let tx = db.begin().await.map_err(db_err)?;
    upsert_item(&tx, customer_id, &item).await?;
    tx.commit().await.map_err(db_err)?;
    assemble_cart(db, customer_id, None).await
}

/// E-updateCartItem(qty≥1;现货校验)
pub async fn update_item(
    db: &DatabaseConnection,
    customer_id: i64,
    id: i64,
    qty: Option<i64>,
) -> Result<CartResponse, CatalogError> {
    let Some(qty) = qty.filter(|q| *q >= 1) else {
        return Err(field_err(vec![("qty", "range_invalid")]));
    };
    let item = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, sku_id FROM cart_item WHERE id = ? AND customer_id = ?",
            [id.into(), customer_id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(404603))?;
    if let Some(sku_id) = item.try_get::<u64>("", "sku_id").ok().map(|v| v as i64) {
        let stock = get_sku(db, sku_id).await?.and_then(|s| s.stock).unwrap_or(0);
        if qty > stock {
            let mut e = trading(409601);
            e.details = Some(serde_json::json!({"sku_id": sku_id}));
            return Err(e);
        }
    }
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE cart_item SET qty = ?, updated_at = NOW(3) WHERE id = ?",
        [qty.into(), id.into()],
    ))
    .await
    .map_err(db_err)?;
    assemble_cart(db, customer_id, None).await
}

/// E-removeCartItem(affected=0 → 404603)
pub async fn remove_item(db: &DatabaseConnection, customer_id: i64, id: i64) -> Result<(), CatalogError> {
    let res = db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "DELETE FROM cart_item WHERE id = ? AND customer_id = ?",
            [id.into(), customer_id.into()],
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(trading(404603));
    }
    Ok(())
}

/// E-mergeCart(TX-TRD-007 幂等闸 + 截断;V-TRD-009/010)
pub async fn merge(
    db: &DatabaseConnection,
    customer_id: i64,
    anon_token: Option<String>,
    items: Option<Vec<CartItemCreate>>,
) -> Result<CartResponse, CatalogError> {
    let token = anon_token
        .as_deref()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .ok_or_else(|| field_err(vec![("anon_token", "required")]))?;
    if token.len() > 64 {
        return Err(field_err(vec![("anon_token", "too_long")]));
    }
    let items = items.ok_or_else(|| field_err(vec![("items", "required")]))?;
    // V-TRD-010 逐项校验:单项非法跳过;全部非法 → 422601 invalid_items
    let mut valid_items: Vec<ValidatedItem> = vec![];
    let mut invalid_indexes: Vec<i64> = vec![];
    for (i, item) in items.iter().enumerate() {
        match validate_item(db, item).await {
            Ok(v) => valid_items.push(v),
            Err(_) => invalid_indexes.push(i as i64),
        }
    }
    if !items.is_empty() && valid_items.is_empty() {
        let mut e = trading(422601);
        e.details = Some(serde_json::json!({"invalid_items": invalid_indexes}));
        return Err(e);
    }
    let tx = db.begin().await.map_err(db_err)?;
    // STEP-TRD-01 幂等闸(uk_merge_customer_token)
    let gate = tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT IGNORE INTO cart_merge_record(customer_id, anon_token, created_at, updated_at) VALUES (?,?,NOW(3),NOW(3))",
        [customer_id.into(), token.into()],
    ));
    let gate = match gate.await {
        Ok(r) => r,
        Err(e) => return Err(db_err(e)),
    };
    if gate.rows_affected() == 0 {
        tx.commit().await.map_err(db_err)?;
        return assemble_cart(db, customer_id, None).await;
    }
    // STEP-TRD-02/03 UPSERT + 现货截断
    let mut truncated: Vec<i64> = vec![];
    for item in &valid_items {
        let (row_id, qty) = upsert_item(&tx, customer_id, item).await?;
        if let Some(sku) = &item.sku {
            let stock = sku.stock.unwrap_or(0);
            if qty > stock {
                let clamped = std::cmp::max(stock, TRUNCATE_KEEP_MIN);
                if clamped < qty {
                    tx.execute(Statement::from_sql_and_values(
                        DbBackend::MySql,
                        "UPDATE cart_item SET qty = ?, updated_at = NOW(3) WHERE id = ?",
                        [clamped.into(), row_id.into()],
                    ))
                    .await
                    .map_err(db_err)?;
                }
                truncated.push(row_id);
            }
        }
    }
    tx.commit().await.map_err(db_err)?;
    assemble_cart(db, customer_id, if truncated.is_empty() { None } else { Some(truncated) }).await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn custom_hash_deterministic_and_sensitive() {
        let a = CustomSizeData { bust: Some(90.0), waist: Some(70.0), hips: Some(95.0), hollow_to_floor: Some(140.0), height: None };
        let b = CustomSizeData { bust: Some(90.0), waist: Some(70.0), hips: Some(95.0), hollow_to_floor: Some(140.0), height: None };
        let c = CustomSizeData { bust: Some(91.0), waist: Some(70.0), hips: Some(95.0), hollow_to_floor: Some(140.0), height: None };
        assert_eq!(a.hash(), b.hash(), "同数据同哈希");
        assert_ne!(a.hash(), c.hash(), "不同数据不同哈希");
    }

    #[test]
    fn custom_complete_checks_main_four() {
        assert!(CustomSizeData { bust: Some(1.0), waist: Some(1.0), hips: Some(1.0), hollow_to_floor: Some(1.0), height: None }.complete());
        assert!(!CustomSizeData { bust: None, waist: Some(1.0), hips: Some(1.0), hollow_to_floor: Some(1.0), height: None }.complete());
        assert!(!CustomSizeData { bust: Some(1.0), waist: Some(1.0), hips: Some(1.0), hollow_to_floor: None, height: Some(1.0) }.complete(), "height 不能替代主尺寸");
    }
}
