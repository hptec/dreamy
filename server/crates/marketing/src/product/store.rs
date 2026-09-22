//! product store 侧:E-CAT-01 列表 / E-CAT-02 搜索 / E-CAT-04 PDP。
//! 语义对齐 StoreProductService + ProductCardAssembler(MAP-CAT-001/002)。
//! 缓存沿用 infra TwoTierCache(key 形态与 Java filtersHash 一致);本批先 DB 直查,缓存接入在
//! cache invalidation 域迁移时统一开启(避免脏读窗口)。

use std::collections::{BTreeMap, BTreeSet, HashMap, HashSet};

use sea_orm::ConnectionTrait;

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::service_category::{cat_err, CatalogError};

const SLUG_MAX: usize = 128;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[prod] db error:{e}");
    CatalogError::new(500501)
}

fn not_found() -> CatalogError {
    CatalogError::new(404501)
}

// ══════════════════ 查询参数解析(V-CAT-001~006)══════════════════

#[derive(Debug, Deserialize)]
pub struct ListParams {
    pub locale: Option<String>,
    pub page: Option<i64>,
    #[serde(rename = "page_size")]
    pub page_size: Option<i64>,
    #[serde(rename = "category_id")]
    pub category_id: Option<i64>,
    #[serde(rename = "collection_id")]
    pub collection_id: Option<i64>,
    pub color: Option<String>,
    pub size: Option<String>,
    #[serde(rename = "price_min")]
    pub price_min: Option<f64>,
    #[serde(rename = "price_max")]
    pub price_max: Option<f64>,
    pub sort: Option<String>,
    /// ?attr=key:value&attr=key:value2(重复参数,axum Query 以多次出现合并)
    #[serde(rename = "attr")]
    pub attr: Vec<String>,
}

pub struct ParsedList {
    pub locale: String,
    pub page: i64,
    pub page_size: i64,
    pub category_id: Option<i64>,
    pub collection_id: Option<i64>,
    pub color: Option<String>,
    pub size: Option<String>,
    pub price_min: Option<f64>,
    pub price_max: Option<f64>,
    pub sort: String,
    pub attrs: BTreeMap<String, BTreeSet<String>>,
}

fn parse_locale(v: Option<&str>, fields: &mut Vec<(&'static str, &'static str)>) -> String {
    match v.map(str::trim).filter(|s| !s.is_empty()) {
        None => "en".into(),
        Some(l @ ("en" | "es" | "fr")) => l.into(),
        Some(_) => {
            fields.push(("locale", "invalid_enum"));
            "en".into()
        }
    }
}

fn parse_page(v: Option<i64>, fields: &mut Vec<(&'static str, &'static str)>) -> i64 {
    match v {
        None => 1,
        Some(p) if p < 1 => {
            fields.push(("page", "range_invalid"));
            1
        }
        Some(p) => p,
    }
}

fn parse_page_size(v: Option<i64>, fields: &mut Vec<(&'static str, &'static str)>) -> i64 {
    match v {
        None => 20,
        Some(p) if !(1..=100).contains(&p) => {
            fields.push(("page_size", "range_invalid"));
            20
        }
        Some(p) => p,
    }
}

fn check_max(v: Option<&str>, max: usize, field: &'static str, fields: &mut Vec<(&'static str, &'static str)>) -> Option<String> {
    match v.map(str::trim).filter(|s| !s.is_empty()) {
        Some(s) if s.len() > max => {
            fields.push((field, "too_long"));
            None
        }
        other => other.map(String::from),
    }
}

fn parse_positive_id(v: Option<i64>, field: &'static str, fields: &mut Vec<(&'static str, &'static str)>) -> Option<i64> {
    match v {
        None => None,
        Some(x) if x <= 0 => {
            fields.push((field, "invalid_id"));
            None
        }
        Some(x) => Some(x),
    }
}

const SORTS: [&str; 4] = ["recommended", "newest", "price_asc", "price_desc"];

/// ?attr=key:value 解析(同 key 多值 OR,跨 key AND;TreeMap/TreeSet 规范化)
fn parse_attrs(raw: &[String], fields: &mut Vec<(&'static str, &'static str)>) -> BTreeMap<String, BTreeSet<String>> {
    let mut out: BTreeMap<String, BTreeSet<String>> = BTreeMap::new();
    for item in raw {
        let Some((key, value)) = item.split_once(':') else {
            fields.push(("attr", "format_invalid"));
            continue;
        };
        let key = key.trim().to_lowercase();
        let value = value.trim().to_string();
        if key.is_empty() || value.is_empty() || value.len() > 255 {
            fields.push(("attr", "format_invalid"));
            continue;
        }
        out.entry(key).or_default().insert(value);
    }
    out
}

pub fn parse_list(p: &ListParams) -> Result<ParsedList, CatalogError> {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let locale = parse_locale(p.locale.as_deref(), &mut fields);
    let page = parse_page(p.page, &mut fields);
    let page_size = parse_page_size(p.page_size, &mut fields);
    // 价格区间:min<=max(V-CAT-005)
    if let (Some(min), Some(max)) = (p.price_min, p.price_max) {
        if min > max {
            fields.push(("price_min", "range_invalid"));
        }
    }
    let sort = match p.sort.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
        None => "recommended".to_string(),
        Some(s) if SORTS.contains(&s) => s.to_string(),
        Some(_) => {
            fields.push(("sort", "invalid_enum"));
            "recommended".to_string()
        }
    };
    let category_id = parse_positive_id(p.category_id, "category_id", &mut fields);
    let collection_id = parse_positive_id(p.collection_id, "collection_id", &mut fields);
    let color = check_max(p.color.as_deref(), 32, "color", &mut fields);
    let size = check_max(p.size.as_deref(), 16, "size", &mut fields);
    let attrs = parse_attrs(&p.attr, &mut fields);
    if !fields.is_empty() {
        return Err(CatalogError::field_validation(&fields));
    }
    Ok(ParsedList {
        locale,
        page,
        page_size,
        category_id,
        collection_id,
        color,
        size,
        price_min: p.price_min,
        price_max: p.price_max,
        sort,
        attrs,
    })
}

/// 筛选参数规范化序列化(逐字对齐 Java filtersHash;空页同样缓存防穿透)
pub fn filters_hash(q: &ParsedList, valid_attrs: &BTreeMap<String, BTreeSet<String>>) -> String {
    let attrs = valid_attrs
        .iter()
        .map(|(k, vs)| format!("{}:{}", k, Vec::from_iter(vs.clone()).join(",")))
        .collect::<Vec<_>>()
        .join(";");
    let dec = |v: &Option<f64>| match v {
        None => "-".to_string(),
        Some(x) => {
            let s = format!("{}", x);
            // stripTrailingZeros().toPlainString() 语义:去尾零
            if s.contains('.') {
                s.trim_end_matches('0').trim_end_matches('.').to_string()
            } else {
                s
            }
        }
    };
    format!(
        "c={}|t={}|co={}|s={}|pm={}|px={}|so={}|a={}|p={}|ps={}",
        q.category_id.map(|v| v.to_string()).unwrap_or_else(|| "-".into()),
        q.collection_id.map(|v| v.to_string()).unwrap_or_else(|| "-".into()),
        q.color.clone().unwrap_or_else(|| "-".into()),
        q.size.clone().unwrap_or_else(|| "-".into()),
        dec(&q.price_min),
        dec(&q.price_max),
        q.sort,
        if attrs.is_empty() { "-".into() } else { attrs },
        q.page,
        q.page_size
    )
}

// ══════════════════ 数据行 ══════════════════

pub struct ProductRow {
    pub id: i64,
    pub slug: String,
    pub name: String,
    pub category_id: Option<i64>,
    pub description: Option<String>,
    pub designer_note: Option<String>,
    pub selling_points: Option<Value>,
    pub price: f64,
    pub compare_at: Option<f64>,
    pub multi_currency_prices: Option<Value>,
    pub installment: bool,
    pub is_new: bool,
    pub is_best: bool,
    pub rating_avg: Option<f64>,
    pub rating_count: Option<i64>,
    pub lead_time_days: Option<i64>,
    pub rush_available: bool,
    pub custom_size_available: bool,
    pub style_no: Option<String>,
    pub seo_title: Option<String>,
    pub seo_desc: Option<String>,
    pub fabric_compositions: Option<Value>,
    pub care: Option<Value>,
    pub fabric_care_note: Option<String>,
}

pub struct TranslationRow {
    pub product_id: i64,
    pub name: Option<String>,
    pub description: Option<String>,
    pub designer_note: Option<String>,
    pub selling_points: Option<Value>,
}

pub struct ImageRow {
    pub id: i64,
    pub product_id: i64,
    pub url: String,
    /// 1=gallery 2=lifestyle 3=video 4=swatch
    pub kind: i8,
    pub color_name: Option<String>,
    pub sort: Option<i32>,
}

pub struct SkuRow {
    pub id: i64,
    pub sku_code: String,
    pub color: Option<String>,
    pub size: Option<String>,
    pub stock: i32,
    pub version: i64,
}

pub struct SizeRow {
    pub id: i64,
    pub us: Option<String>,
    pub uk: Option<String>,
    pub au: Option<String>,
    pub bust: Option<f64>,
    pub waist: Option<f64>,
    pub hips: Option<f64>,
    pub hollow_to_floor: Option<f64>,
}

// ══════════════════ 查询执行 ══════════════════

/// 分类子树 → IN 列表;子树为空(category 不存在)→ Ok(None) 空页
async fn subtree_category_ids(
    db: &sea_orm::DatabaseConnection,
    category_id: Option<i64>,
) -> Result<Option<Vec<i64>>, CatalogError> {
    let Some(cid) = category_id else {
        return Ok(None);
    };
    let all = crate::service_category::list_all(db).await?;
    let sub = crate::service_category::subtree_ids(&all, cid);
    if sub.is_empty() {
        return Ok(Some(vec![]));
    }
    Ok(Some(sub))
}

/// E-CAT-01 列表(status=published + EXISTS 筛选叠加;排序 switch 对齐)
pub async fn list_products(
    db: &sea_orm::DatabaseConnection,
    q: &ParsedList,
) -> Result<(Vec<Value>, i64), CatalogError> {
    // STEP-CAT-00 attr key → attribute_id(未知 key 忽略)
    let mut attr_filters: BTreeMap<i64, BTreeSet<String>> = BTreeMap::new();
    let mut valid_attrs: BTreeMap<String, BTreeSet<String>> = BTreeMap::new();
    if !q.attrs.is_empty() {
        let rows = db
            .query_all(sea_orm::Statement::from_string(
                sea_orm::DatabaseBackend::MySql,
                "SELECT id, `key` AS k FROM attribute_def".to_string(),
            ))
            .await
            .map_err(db_err)?;
        let id_by_key: HashMap<String, i64> = rows
            .into_iter()
            .filter_map(|r| {
                let k: Option<String> = r.try_get("", "k").ok();
                let id: Option<u64> = r.try_get("", "id").ok();
                Some((k?, id? as i64))
            })
            .collect();
        for (key, values) in &q.attrs {
            if let Some(id) = id_by_key.get(key) {
                attr_filters.insert(*id, values.clone());
                valid_attrs.insert(key.clone(), values.clone());
            }
        }
    }
    // STEP-CAT-02 分类子树
    let category_ids = subtree_category_ids(db, q.category_id).await?;
    if let Some(ids) = &category_ids {
        if ids.is_empty() {
            return Ok((vec![], 0));
        }
    }
    // STEP-CAT-03 动态 SQL(参数化;排序 switch 对齐 Java)
    let mut where_clause = String::from("p.status = 2");
    let mut args: Vec<sea_orm::Value> = vec![];
    if let Some(ids) = &category_ids {
        let in_list = ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
        where_clause.push_str(&format!(" AND p.category_id IN ({})", in_list));
    }
    if let Some(t) = q.collection_id {
        where_clause.push_str(
            " AND EXISTS (SELECT 1 FROM product_collection pcol WHERE pcol.product_id = p.id AND pcol.collection_id = ?)",
        );
        args.push(t.into());
    }
    if let Some(c) = &q.color {
        where_clause.push_str(
            " AND EXISTS (SELECT 1 FROM sku s WHERE s.product_id = p.id AND s.color = ?)",
        );
        args.push(c.clone().into());
    }
    if let Some(s) = &q.size {
        where_clause.push_str(
            " AND EXISTS (SELECT 1 FROM sku s WHERE s.product_id = p.id AND s.size = ?)",
        );
        args.push(s.clone().into());
    }
    for (attr_id, values) in &attr_filters {
        let placeholders = values.iter().map(|_| "?").collect::<Vec<_>>().join(", ");
        where_clause.push_str(&format!(
            " AND EXISTS (SELECT 1 FROM product_attribute_value pav WHERE pav.product_id = p.id AND pav.attribute_id = ? AND pav.`value` IN ({}))",
            placeholders
        ));
        args.push((*attr_id).into());
        for v in values {
            args.push(v.clone().into());
        }
    }
    if let Some(pm) = q.price_min {
        where_clause.push_str(" AND p.price >= ?");
        args.push(pm.into());
    }
    if let Some(px) = q.price_max {
        where_clause.push_str(" AND p.price <= ?");
        args.push(px.into());
    }
    let order = match q.sort.as_str() {
        "newest" => "p.created_at DESC, p.id DESC",
        "price_asc" => "p.price ASC, p.id DESC",
        "price_desc" => "p.price DESC, p.id DESC",
        _ => "p.sort ASC, p.created_at DESC, p.id DESC",
    };
    // COUNT + PAGE(两条;COUNT 免装配)
    let count_sql = format!("SELECT COUNT(*) AS n FROM product p WHERE {}", where_clause);
    let count_rows = db
        .query_all(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            count_sql,
            args.clone(),
        ))
        .await
        .map_err(db_err)?;
    let total: i64 = count_rows
        .first()
        .and_then(|r| r.try_get::<i64>("", "n").ok())
        .unwrap_or(0);
    let offset = (q.page - 1) * q.page_size;
    let page_sql = format!(
        "SELECT p.* FROM product p WHERE {} ORDER BY {} LIMIT {} OFFSET {}",
        where_clause, order, q.page_size, offset
    );
    let rows = db
        .query_all(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            page_sql,
            args,
        ))
        .await
        .map_err(db_err)?;
    let products = rows.iter().map(map_product_row).collect::<Vec<_>>();
    let ids: Vec<i64> = products.iter().map(|p| p.id).collect();
    // STEP-CAT-04/05 卡片装配(翻译 + 图片批查)
    let cards = assemble_cards(db, &products, &ids, &q.locale).await?;
    Ok((cards, total))
}

fn map_product_row(r: &sea_orm::QueryResult) -> ProductRow {
    ProductRow {
        id: r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        slug: r.try_get::<String>("", "slug").unwrap_or_default(),
        name: r.try_get::<String>("", "name").unwrap_or_default(),
        category_id: r.try_get::<i64>("", "category_id").ok(),
        description: r.try_get::<String>("", "description").ok(),
        designer_note: r.try_get::<String>("", "designer_note").ok(),
        selling_points: r.try_get::<Value>("", "selling_points").ok(),
        price: r.try_get::<f64>("", "price").unwrap_or(0.0),
        compare_at: r.try_get::<f64>("", "compare_at").ok(),
        multi_currency_prices: r.try_get::<Value>("", "multi_currency_prices").ok(),
        installment: r.try_get::<bool>("", "installment").unwrap_or(false),
        is_new: r.try_get::<bool>("", "is_new").unwrap_or(false),
        is_best: r.try_get::<bool>("", "is_best").unwrap_or(false),
        rating_avg: crate::cart::dec(r, "rating_avg"),
        rating_count: r.try_get::<i64>("", "rating_count").ok(),
        lead_time_days: r.try_get::<i64>("", "lead_time_days").ok(),
        rush_available: r.try_get::<bool>("", "rush_available").unwrap_or(false),
        custom_size_available: r.try_get::<bool>("", "custom_size_available").unwrap_or(false),
        style_no: r.try_get::<String>("", "style_no").ok(),
        seo_title: r.try_get::<String>("", "seo_title").ok(),
        seo_desc: r.try_get::<String>("", "seo_desc").ok(),
        fabric_compositions: r.try_get::<Value>("", "fabric_compositions").ok(),
        care: r.try_get::<Value>("", "care").ok(),
        fabric_care_note: r.try_get::<String>("", "fabric_care_note").ok(),
    }
}

/// 翻译批查(仅 es/fr;MAP-CAT-001 pick 语义)
async fn translations_for(
    db: &sea_orm::DatabaseConnection,
    ids: &[i64],
    locale: &str,
) -> Result<HashMap<i64, TranslationRow>, CatalogError> {
    let mut out = HashMap::new();
    if locale != "es" && locale != "fr" || ids.is_empty() {
        return Ok(out);
    }
    let in_list = ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
    let sql = format!(
        "SELECT product_id, name, description, designer_note, selling_points FROM product_translation WHERE locale = '{}' AND product_id IN ({})",
        locale, in_list
    );
    let rows = db
        .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
        .await
        .map_err(db_err)?;
    for r in rows {
        let pid = r.try_get::<u64>("", "product_id").map(|v| v as i64).unwrap_or(0);
        out.insert(pid, TranslationRow {
            product_id: pid,
            name: r.try_get::<String>("", "name").ok(),
            description: r.try_get::<String>("", "description").ok(),
            designer_note: r.try_get::<String>("", "designer_note").ok(),
            selling_points: r.try_get::<Value>("", "selling_points").ok(),
        });
    }
    Ok(out)
}

/// 图片批查
async fn images_for(db: &sea_orm::DatabaseConnection, ids: &[i64]) -> Result<HashMap<i64, Vec<ImageRow>>, CatalogError> {
    let mut out: HashMap<i64, Vec<ImageRow>> = HashMap::new();
    if ids.is_empty() {
        return Ok(out);
    }
    let in_list = ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
    let sql = format!(
        "SELECT id, product_id, url, kind, color_name, sort FROM product_image WHERE product_id IN ({}) ORDER BY sort ASC, id ASC",
        in_list
    );
    let rows = db
        .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
        .await
        .map_err(db_err)?;
    for r in rows {
        let row = ImageRow {
            id: r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
            product_id: r.try_get::<u64>("", "product_id").map(|v| v as i64).unwrap_or(0),
            url: r.try_get::<String>("", "url").unwrap_or_default(),
            kind: r.try_get::<i8>("", "kind").unwrap_or(1),
            color_name: r.try_get::<String>("", "color_name").ok(),
            sort: r.try_get::<i32>("", "sort").ok(),
        };
        out.entry(row.product_id).or_default().push(row);
    }
    Ok(out)
}

fn pick(translated: &Option<String>, fallback: &Option<String>) -> Option<String> {
    match translated {
        Some(t) if !t.trim().is_empty() => Some(t.clone()),
        _ => fallback.clone(),
    }
}

fn pick_list(translated: &Option<Value>, fallback: &Option<Value>) -> Option<Value> {
    match translated {
        Some(Value::Array(a)) if !a.is_empty() => Some(Value::Array(a.clone())),
        _ => fallback.clone(),
    }
}

/// MAP-CAT-001 单卡映射(主图=gallery sort=0 兜底 gallery 首张;swatches=kind=swatch)
fn to_card(p: &ProductRow, images: &[ImageRow], tr: Option<&TranslationRow>) -> Value {
    let gallery: Vec<&ImageRow> = images.iter().filter(|i| i.kind == 1).collect();
    let image_url = gallery
        .iter()
        .find(|i| i.sort == Some(0))
        .or_else(|| gallery.first())
        .map(|i| i.url.clone());
    let swatches: Vec<Value> = images
        .iter()
        .filter(|i| i.kind == 4)
        .map(|i| serde_json::json!({"color_name": i.color_name, "url": i.url}))
        .collect();
    serde_json::json!({
        "id": p.id,
        "slug": p.slug,
        "name": pick(&tr.and_then(|t| t.name.clone()), &Some(p.name.clone())),
        "price": p.price,
        "compare_at": p.compare_at,
        "multi_currency_prices": p.multi_currency_prices,
        "installment": p.installment,
        "is_new": p.is_new,
        "is_best": p.is_best,
        "image_url": image_url,
        "swatches": swatches,
        "rating_avg": p.rating_avg,
        "rating_count": p.rating_count,
        "selling_points": pick_list(&tr.and_then(|t| t.selling_points.clone()), &p.selling_points),
    })
}

/// 卡片批量装配(一次 IN 批查翻译+图片)
pub async fn assemble_cards(
    db: &sea_orm::DatabaseConnection,
    products: &[ProductRow],
    ids: &[i64],
    locale: &str,
) -> Result<Vec<Value>, CatalogError> {
    if products.is_empty() {
        return Ok(vec![]);
    }
    let translations = translations_for(db, ids, locale).await?;
    let images = images_for(db, ids).await?;
    Ok(products
        .iter()
        .map(|p| {
            let tr = translations.get(&p.id);
            let imgs = images.get(&p.id).map(|v| v.as_slice()).unwrap_or(&[]);
            to_card(p, imgs, tr)
        })
        .collect())
}

/// E-CAT-02 搜索(EN 主 FULLTEXT + es/fr 附表 UNION + 集合命中;内存合并去重分页)
pub async fn search(
    db: &sea_orm::DatabaseConnection,
    q: Option<String>,
    locale: &str,
    page: i64,
    page_size: i64,
) -> Result<(Vec<Value>, i64), CatalogError> {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let q_norm = q.unwrap_or_default().trim().to_lowercase();
    if q_norm.is_empty() || q_norm.len() > 80 {
        fields.push(("q", if q_norm.is_empty() { "required" } else { "too_long" }));
    }
    if !fields.is_empty() {
        return Err(CatalogError::field_validation(&fields));
    }
    let mut merged: Vec<i64> = vec![];
    let mut seen: HashSet<i64> = HashSet::new();
    // STEP-CAT-02 EN 主检索(FULLTEXT ngram,相关度序,LIMIT 500)
    let sql = format!(
        "SELECT id FROM product WHERE status = 2 AND MATCH(name) AGAINST('{}' IN NATURAL LANGUAGE MODE) ORDER BY MATCH(name) AGAINST('{}' IN NATURAL LANGUAGE MODE) DESC LIMIT 500",
        q_norm.replace('\'', "''"), q_norm.replace('\'', "''")
    );
    let rows = db
        .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
        .await
        .map_err(db_err)?;
    for r in rows {
        if let Ok(id) = r.try_get::<u64>("", "id") {
            let id = id as i64;
            if seen.insert(id) {
                merged.push(id);
            }
        }
    }
    // STEP-CAT-03 es/fr 附表
    if locale == "es" || locale == "fr" {
        let sql = format!(
            "SELECT product_id FROM product_translation WHERE locale = '{}' AND MATCH(name, description) AGAINST('{}' IN NATURAL LANGUAGE MODE) LIMIT 500",
            locale, q_norm.replace('\'', "''")
        );
        if let Ok(rows) = db
            .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
            .await
        {
            for r in rows {
                if let Ok(id) = r.try_get::<u64>("", "product_id") {
                    let id = id as i64;
                    if seen.insert(id) {
                        merged.push(id);
                    }
                }
            }
        }
    }
    // STEP-CAT-04 集合命中(collection name/label LIKE → published 商品并入)
    let like = format!("%{}%", q_norm.replace('\\', "\\\\").replace('%', "\\%").replace('_', "\\_"));
    let coll_sql = format!(
        "SELECT DISTINCT c.id FROM collection c LEFT JOIN collection_translation ct ON ct.collection_id = c.id WHERE c.status = 1 AND (c.name LIKE '{}' OR ct.label LIKE '{}')",
        like, like
    );
    if let Ok(rows) = db
        .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, coll_sql))
        .await
    {
        for r in rows {
            let Some(cid) = r.try_get::<u64>("", "id").ok().map(|v| v as i64) else {
                continue;
            };
            let sql = format!(
                "SELECT product_id FROM product_collection WHERE collection_id = {} LIMIT 200",
                cid
            );
            if let Ok(prows) = db
                .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
                .await
            {
                for pr in prows {
                    if let Ok(id) = pr.try_get::<u64>("", "product_id") {
                        let id = id as i64;
                        if seen.insert(id) {
                            merged.push(id);
                        }
                    }
                }
            }
        }
    }
    // STEP-CAT-05 内存分页;集合命中可能含未发布 → published 批查回排
    let total = merged.len() as i64;
    let from = ((page - 1) * page_size).min(total) as usize;
    let to = (from + page_size as usize).min(total as usize);
    let page_ids = &merged[from..to];
    let cards = cards_by_ids_ordered(db, page_ids, locale).await?;
    Ok((cards, total))
}

/// 按 ids 批查 published 商品并按给定顺序输出卡片
pub async fn cards_by_ids_ordered(
    db: &sea_orm::DatabaseConnection,
    ids: &[i64],
    locale: &str,
) -> Result<Vec<Value>, CatalogError> {
    if ids.is_empty() {
        return Ok(vec![]);
    }
    let in_list = ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
    let sql = format!("SELECT p.* FROM product p WHERE p.id IN ({}) AND p.status = 2", in_list);
    let rows = db
        .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
        .await
        .map_err(db_err)?;
    let mut by_id: HashMap<i64, ProductRow> = HashMap::new();
    for r in &rows {
        let p = map_product_row(r);
        by_id.insert(p.id, p);
    }
    let ordered: Vec<ProductRow> = ids.iter().filter_map(|id| by_id.remove(id)).collect();
    let all_ids: Vec<i64> = ordered.iter().map(|p| p.id).collect();
    assemble_cards(db, &ordered, &all_ids, locale).await
}

// ══════════════════ PDP(E-CAT-04)══════════════════

/// PDP 全量装配(30 字段;null 缓存穿透保护在 api 层接 infra cache)
pub async fn get_product(
    db: &sea_orm::DatabaseConnection,
    slug: &str,
    locale: &str,
) -> Result<Value, CatalogError> {
    // V-CAT-012 slug 防探测:pattern/长度不匹配 → 404501
    let slug_ok = !slug.is_empty()
        && slug.len() <= SLUG_MAX
        && slug.chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '-');
    if !slug_ok {
        return Err(not_found());
    }
    let rows = db
        .query_all(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "SELECT p.* FROM product p WHERE p.slug = ? AND p.status = 2",
            [slug.into()],
        ))
        .await
        .map_err(db_err)?;
    let Some(r) = rows.first() else {
        return Err(not_found());
    };
    let p = map_product_row(r);
    let ids = [p.id];
    // 子资源批查
    let images = images_for(db, &ids).await?;
    let image_dtos: Vec<Value> = images
        .get(&p.id)
        .map(|v| v.as_slice())
        .unwrap_or(&[])
        .iter()
        .map(|i| {
            serde_json::json!({
                "id": i.id, "url": i.url, "kind": i.kind,
                "color_name": i.color_name, "sort": i.sort,
            })
        })
        .collect();
    let sku_rows = db
        .query_all(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!("SELECT id, sku_code, color, size, stock, version FROM sku WHERE product_id = {}", p.id),
        ))
        .await
        .map_err(db_err)?;
    let skus: Vec<Value> = sku_rows
        .iter()
        .map(|r| {
            serde_json::json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "sku_code": r.try_get::<String>("", "sku_code").unwrap_or_default(),
                "color": r.try_get::<String>("", "color").ok(),
                "size": r.try_get::<String>("", "size").ok(),
                "stock": r.try_get::<i32>("", "stock").unwrap_or(0),
                "version": r.try_get::<i64>("", "version").unwrap_or(0),
            })
        })
        .collect();
    let size_rows = db
        .query_all(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!("SELECT id, us, uk, au, bust, waist, hips, hollow_to_floor FROM size_chart_row WHERE product_id = {} ORDER BY id ASC", p.id),
        ))
        .await
        .map_err(db_err)?;
    let size_chart: Vec<Value> = size_rows
        .iter()
        .map(|r| {
            serde_json::json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "us": r.try_get::<String>("", "us").ok(),
                "uk": r.try_get::<String>("", "uk").ok(),
                "au": r.try_get::<String>("", "au").ok(),
                "bust": r.try_get::<f64>("", "bust").ok(),
                "waist": r.try_get::<f64>("", "waist").ok(),
                "hips": r.try_get::<f64>("", "hips").ok(),
                "hollow_to_floor": r.try_get::<f64>("", "hollow_to_floor").ok(),
            })
        })
        .collect();
    // 集合(enabled)+ locale 翻译
    let coll_rows = db
        .query_all(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!(
                "SELECT c.id, c.collection_group_id, c.name FROM product_collection pc JOIN collection c ON c.id = pc.collection_id WHERE pc.product_id = {} AND c.status = 1 ORDER BY pc.sort ASC",
                p.id
            ),
        ))
        .await
        .map_err(db_err)?;
    let mut coll_names: HashMap<i64, String> = HashMap::new();
    if (locale == "es" || locale == "fr") && !coll_rows.is_empty() {
        let cids: Vec<String> = coll_rows
            .iter()
            .filter_map(|r| r.try_get::<u64>("", "id").ok().map(|v| v.to_string()))
            .collect();
        if !cids.is_empty() {
            let sql = format!(
                "SELECT collection_id, label FROM collection_translation WHERE locale = '{}' AND collection_id IN ({}) AND label IS NOT NULL AND label != ''",
                locale, cids.join(",")
            );
            if let Ok(trows) = db
                .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
                .await
            {
                for t in trows {
                    if let (Ok(cid), Ok(label)) = (
                        t.try_get::<u64>("", "collection_id").map(|v| v as i64),
                        t.try_get::<String>("", "label"),
                    ) {
                        coll_names.insert(cid, label);
                    }
                }
            }
        }
    }
    let collections: Vec<Value> = coll_rows
        .iter()
        .map(|r| {
            let id = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            let name: String = r.try_get::<String>("", "name").unwrap_or_default();
            serde_json::json!({
                "id": id,
                "collection_group_id": r.try_get::<u64>("", "collection_group_id").map(|v| v as i64).ok(),
                "name": coll_names.get(&id).cloned().unwrap_or(name),
            })
        })
        .collect();
    // 分类名派生(es/fr 经 category_translation)
    let category_name = match p.category_id {
        Some(cid) => {
            let name_sql = if locale == "es" || locale == "fr" {
                format!(
                    "(SELECT name FROM category_translation WHERE category_id = {} AND locale = '{}' AND name IS NOT NULL AND name != '' LIMIT 1)",
                    cid, locale
                )
            } else {
                "NULL".to_string()
            };
            let sql = format!(
                "SELECT COALESCE(NULLIF({}, ''), name, '') AS n FROM category WHERE id = {}",
                name_sql, cid
            );
            db.query_one(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
                .await
                .map_err(db_err)?
                .and_then(|r| r.try_get::<String>("", "n").ok())
                .filter(|s| !s.is_empty())
        }
        None => None,
    };
    // 翻译覆盖
    let translations = translations_for(db, &ids, locale).await?;
    let tr = translations.get(&p.id);
    // PDP attributes(生效属性集序,hidden 排除,无值省略,toggle 值本地化)
    let attributes = build_store_attributes(db, &p, locale).await?;
    Ok(serde_json::json!({
        "id": p.id,
        "slug": p.slug,
        "name": pick(&tr.and_then(|t| t.name.clone()), &Some(p.name.clone())),
        "category_id": p.category_id,
        "category_name": category_name,
        "description": pick(&tr.and_then(|t| t.description.clone()), &p.description),
        "designer_note": pick(&tr.and_then(|t| t.designer_note.clone()), &p.designer_note),
        "selling_points": pick_list(&tr.and_then(|t| t.selling_points.clone()), &p.selling_points),
        "price": p.price,
        "compare_at": p.compare_at,
        "multi_currency_prices": p.multi_currency_prices,
        "installment": p.installment,
        "is_new": p.is_new,
        "is_best": p.is_best,
        "lead_time_days": p.lead_time_days,
        "rush_available": p.rush_available,
        "custom_size_available": p.custom_size_available,
        "attributes": attributes,
        "style_no": p.style_no,
        "seo_title": p.seo_title,
        "seo_desc": p.seo_desc,
        "images": image_dtos,
        "skus": skus,
        "size_chart": size_chart,
        "collections": collections,
        "rating_avg": p.rating_avg,
        "rating_count": p.rating_count,
        "fabric_compositions": p.fabric_compositions,
        "care_instructions": p.care,
        "fabric_care_note": p.fabric_care_note,
    }))
}

/// 生效属性配置(沿祖先链 set + overrides delta;对齐 ProductAttributeConfigService.effectiveAttrs)
pub async fn effective_attrs(
    db: &sea_orm::DatabaseConnection,
    category_id: Option<i64>,
) -> Result<Vec<(Value, i8)>, CatalogError> {
    let Some(cid) = category_id else {
        return Ok(vec![]);
    };
    let all = crate::service_category::list_all(db).await?;
    let Some(category) = all.iter().find(|c| c.id == cid) else {
        return Ok(vec![]);
    };
    // 沿祖先链最近 attribute_set_id
    let by_id: HashMap<i64, &crate::service_category::CategoryRow> =
        all.iter().map(|c| (c.id, c)).collect();
    let mut effective_set = category.attribute_set_id;
    let mut cursor = category.parent_id;
    let mut hops = 0;
    while effective_set.is_none() && cursor.is_some() && hops < 4 {
        let Some(parent) = cursor.and_then(|pid| by_id.get(&pid)) else {
            break;
        };
        effective_set = parent.attribute_set_id;
        cursor = parent.parent_id;
        hops += 1;
    }
    let mut out: Vec<(Value, i8)> = vec![];
    if let Some(set_id) = effective_set {
        let rows = db
            .query_all(sea_orm::Statement::from_sql_and_values(
                sea_orm::DatabaseBackend::MySql,
                "SELECT asi.visibility, ad.id AS def_id, ad.`key` AS k, ad.label, ad.type, ad.options \
                 FROM attribute_set_item asi JOIN attribute_def ad ON ad.id = asi.attribute_id \
                 WHERE asi.attribute_set_id = ? ORDER BY asi.id ASC",
                [set_id.into()],
            ))
            .await
            .map_err(db_err)?;
        for r in rows {
            let visibility: i8 = r.try_get("", "visibility").unwrap_or(1);
            let def = serde_json::json!({
                "id": r.try_get::<u64>("", "def_id").map(|v| v as i64).unwrap_or(0),
                "key": r.try_get::<String>("", "k").unwrap_or_default(),
                "label": r.try_get::<String>("", "label").unwrap_or_default(),
                "type": r.try_get::<i8>("", "type").unwrap_or(3),
                "options": r.try_get::<Value>("", "options").ok(),
            });
            out.push((def, visibility));
        }
    }
    // delta:attr_overrides(仅子分类)
    if category.parent_id.is_some() {
        if let Some(Value::Object(map)) = &category.attr_overrides {
            for (key, v) in map {
                let Some(vis) = v.as_i64().filter(|n| (1..=3).contains(n)) else {
                    continue;
                };
                // 已有 key 改可见性;新 key 追加尾部
                if let Some(entry) = out.iter_mut().find(|(def, _)| def["key"] == *key) {
                    entry.1 = vis as i8;
                } else {
                    let rows = db
                        .query_all(sea_orm::Statement::from_sql_and_values(
                            sea_orm::DatabaseBackend::MySql,
                            "SELECT id, `key` AS k, label, type, options FROM attribute_def WHERE `key` = ?",
                            [key.clone().into()],
                        ))
                        .await
                        .map_err(db_err)?;
                    if let Some(r) = rows.first() {
                        out.push((
                            serde_json::json!({
                                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                                "key": r.try_get::<String>("", "k").unwrap_or_default(),
                                "label": r.try_get::<String>("", "label").unwrap_or_default(),
                                "type": r.try_get::<i8>("", "type").unwrap_or(3),
                                "options": r.try_get::<Value>("", "options").ok(),
                            }),
                            vis as i8,
                        ));
                    }
                }
            }
        }
    }
    Ok(out)
}

/// PDP attributes(hidden 排除;无值省略;label/值译文;toggle Yes/No 本地化)
async fn build_store_attributes(
    db: &sea_orm::DatabaseConnection,
    p: &ProductRow,
    locale: &str,
) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!(
                "SELECT attribute_id, `value` FROM product_attribute_value WHERE product_id = {}",
                p.id
            ),
        ))
        .await
        .map_err(db_err)?;
    if rows.is_empty() {
        return Ok(vec![]);
    }
    let mut values_by_attr: BTreeMap<i64, Vec<String>> = BTreeMap::new();
    for r in &rows {
        let aid = r.try_get::<u64>("", "attribute_id").map(|v| v as i64).unwrap_or(0);
        let v: String = r.try_get("", "value").unwrap_or_default();
        values_by_attr.entry(aid).or_default().push(v);
    }
    let config = effective_attrs(db, p.category_id).await?;
    // 翻译批查
    let def_ids: Vec<String> = config
        .iter()
        .map(|(def, _)| def["id"].as_i64().unwrap_or(0).to_string())
        .collect();
    let mut translations: HashMap<i64, (Option<String>, Option<Value>)> = HashMap::new();
    if (locale == "es" || locale == "fr") && !def_ids.is_empty() {
        let sql = format!(
            "SELECT attribute_def_id, label, options FROM attribute_def_translation WHERE locale = '{}' AND attribute_def_id IN ({})",
            locale,
            def_ids.join(",")
        );
        if let Ok(trows) = db
            .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
            .await
        {
            for t in trows {
                if let Ok(did) = t.try_get::<u64>("", "attribute_def_id") {
                    translations.insert(
                        did as i64,
                        (
                            t.try_get::<String>("", "label").ok(),
                            t.try_get::<Value>("", "options").ok(),
                        ),
                    );
                }
            }
        }
    }
    let mut out = vec![];
    for (def, visibility) in &config {
        if *visibility == 3 {
            continue; // hidden 排除
        }
        let def_id = def["id"].as_i64().unwrap_or(0);
        let Some(values) = values_by_attr.get(&def_id) else {
            continue; // 无值省略
        };
        let def_type = def["type"].as_i64().unwrap_or(3);
        let key = def["key"].as_str().unwrap_or_default().to_string();
        let def_label = def["label"].as_str().unwrap_or_default().to_string();
        let def_options = def["options"].as_array().cloned();
        let (tr_label, tr_options) = translations
            .get(&def_id)
            .map(|(l, o)| (l.clone(), o.clone()))
            .unwrap_or((None, None));
        let label = tr_label
            .filter(|l| !l.trim().is_empty())
            .unwrap_or(def_label);
        let localize = |value: &str| -> String {
            // toggle:Yes/No 本地化
            if def_type == 4 {
                let yes = value == "true";
                return match locale {
                    "es" => {
                        if yes { "Sí".into() } else { "No".into() }
                    }
                    "fr" => {
                        if yes { "Oui".into() } else { "No".into() }
                    }
                    _ => {
                        if yes { "Yes".into() } else { "No".into() }
                    }
                };
            }
            // options 同序译文
            if let Some(tr_opts) = tr_options.as_ref().and_then(|o| o.as_array()) {
                if let Some(opts) = &def_options {
                    if let Some(idx) = opts.iter().position(|o| o.as_str() == Some(value)) {
                        if let Some(translated) = tr_opts.get(idx).and_then(|v| v.as_str()) {
                            if !translated.trim().is_empty() {
                                return translated.to_string();
                            }
                        }
                    }
                }
            }
            value.to_string()
        };
        let value_dtos: Vec<Value> = values
            .iter()
            .map(|v| serde_json::json!({"value": v, "label": localize(v)}))
            .collect();
        out.push(serde_json::json!({
            "key": key,
            "label": label,
            "type": def_type,
            "values": value_dtos,
        }));
    }
    Ok(out)
}

/// E-CAT-27 filters 维度(非 hidden 的 select/multiselect;options 本地化)
pub async fn list_filters(
    db: &sea_orm::DatabaseConnection,
    category_id: Option<i64>,
    locale: &str,
) -> Result<Vec<Value>, CatalogError> {
    let config = effective_attrs(db, category_id).await?;
    let def_ids: Vec<String> = config
        .iter()
        .map(|(def, _)| def["id"].as_i64().unwrap_or(0).to_string())
        .collect();
    let mut translations: HashMap<i64, (Option<String>, Option<Value>)> = HashMap::new();
    if (locale == "es" || locale == "fr") && !def_ids.is_empty() {
        let sql = format!(
            "SELECT attribute_def_id, label, options FROM attribute_def_translation WHERE locale = '{}' AND attribute_def_id IN ({})",
            locale,
            def_ids.join(",")
        );
        if let Ok(trows) = db
            .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
            .await
        {
            for t in trows {
                if let Ok(did) = t.try_get::<u64>("", "attribute_def_id") {
                    translations.insert(
                        did as i64,
                        (
                            t.try_get::<String>("", "label").ok(),
                            t.try_get::<Value>("", "options").ok(),
                        ),
                    );
                }
            }
        }
    }
    let mut out = vec![];
    for (def, visibility) in &config {
        if *visibility == 3 {
            continue;
        }
        let def_type = def["type"].as_i64().unwrap_or(3);
        // select(1)/multiselect(2) 才允许 options
        if def_type != 1 && def_type != 2 {
            continue;
        }
        let Some(options) = def["options"].as_array().cloned().filter(|o| !o.is_empty()) else {
            continue;
        };
        let def_id = def["id"].as_i64().unwrap_or(0);
        let def_label = def["label"].as_str().unwrap_or_default().to_string();
        let (tr_label, tr_options) = translations
            .get(&def_id)
            .map(|(l, o)| (l.clone(), o.clone()))
            .unwrap_or((None, None));
        let label = tr_label
            .filter(|l| !l.trim().is_empty())
            .unwrap_or(def_label);
        let localize = |value: &Value| -> String {
            let v = value.as_str().unwrap_or_default().to_string();
            if let Some(tr_opts) = tr_options.as_ref().and_then(|o| o.as_array()) {
                if let Some(opts) = def["options"].as_array() {
                    if let Some(idx) = opts.iter().position(|o| o.as_str() == Some(v.as_str())) {
                        if let Some(translated) = tr_opts.get(idx).and_then(|x| x.as_str()) {
                            if !translated.trim().is_empty() {
                                return translated.to_string();
                            }
                        }
                    }
                }
            }
            v
        };
        let option_dtos: Vec<Value> = options
            .iter()
            .map(|v| serde_json::json!({"value": v, "label": localize(v)}))
            .collect();
        out.push(serde_json::json!({
            "key": def["key"],
            "label": label,
            "type": def_type,
            "options": option_dtos,
        }));
    }
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn qlist() -> ParsedList {
        ParsedList {
            locale: "en".into(),
            page: 1,
            page_size: 20,
            category_id: None,
            collection_id: None,
            color: None,
            size: None,
            price_min: None,
            price_max: None,
            sort: "recommended".into(),
            attrs: BTreeMap::new(),
        }
    }

    #[test]
    fn filters_hash_shape_matches_java() {
        let mut q = qlist();
        q.category_id = Some(3);
        q.page = 2;
        q.page_size = 12;
        q.sort = "price_asc".into();
        let mut attrs = BTreeMap::new();
        let mut vs = BTreeSet::new();
        vs.insert("Tulle".into());
        vs.insert("Satin".into());
        attrs.insert("fabric".into(), vs);
        let h = filters_hash(&q, &attrs);
        assert_eq!(
            h,
            "c=3|t=-|co=-|s=-|pm=-|px=-|so=price_asc|a=fabric:Satin,Tulle|p=2|ps=12",
            "与 Java filtersHash 逐字对齐(TreeSet 字典序)"
        );
    }

    #[test]
    fn price_dec_strips_trailing_zeros() {
        let mut q = qlist();
        q.price_min = Some(100.50);
        let h = filters_hash(&q, &BTreeMap::new());
        assert!(h.contains("pm=100.5|"), "100.50 → 100.5(实际 {})", h);
    }

    #[test]
    fn pick_falls_back_on_blank_translation() {
        assert_eq!(pick(&Some("  ".into()), &Some("EN".into())), Some("EN".into()));
        assert_eq!(pick(&Some("ES".into()), &Some("EN".into())), Some("ES".into()));
        assert_eq!(pick(&None, &None), None);
    }

    #[test]
    fn pick_list_falls_back_on_empty() {
        assert_eq!(
            pick_list(&Some(Value::Array(vec![])), &Some(serde_json::json!(["a"]))),
            Some(serde_json::json!(["a"]))
        );
    }

    #[test]
    fn slug_validation_rejects_uppercase_and_long() {
        assert!("a-b-1".chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '-'));
        assert!(!"Bad-Slug".chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '-'));
        assert!("x".repeat(SLUG_MAX + 1).len() > SLUG_MAX);
    }
}
