//! product admin 端:列表/详情/CRUD/状态切换/flags/批量。

use std::collections::{HashMap, HashSet};

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement, TransactionTrait};
use serde_json::{json, Value};

use crate::service_category::{cat_err, CatalogError};

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[p-admin] db error:{e}");
    CatalogError::new(500501)
}

pub fn not_found() -> CatalogError {
    CatalogError::new(404501)
}

pub async fn page_list(
    db: &DatabaseConnection,
    status: Option<i64>,
    keyword: Option<&str>,
    page: i64,
    page_size: i64,
) -> Result<(Vec<Value>, i64), CatalogError> {
    let mut conds: Vec<String> = vec![];
    if let Some(s) = status {
        conds.push(format!("p.status = {}", s));
    }
    if let Some(k) = keyword.filter(|s| !s.trim().is_empty()) {
        conds.push(format!("(p.name LIKE '%{}%' OR p.slug LIKE '%{}%')", k.replace('\'', "''"), k.replace('\'', "''")));
    }
    let where_clause = if conds.is_empty() { String::new() } else { format!(" WHERE {}", conds.join(" AND ")) };
    let total = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT COUNT(*) AS n FROM product p{}", where_clause),
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
                "SELECT p.id, p.name, p.slug, p.status, p.price, p.is_new, p.is_best, p.recommend, p.sort, p.created_at \
                 FROM product p{} ORDER BY p.id DESC LIMIT {} OFFSET {}",
                where_clause, page_size, offset
            ),
        ))
        .await
        .map_err(db_err)?;
    Ok((
        rows.iter()
            .map(|r| {
                json!({
                    "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                    "name": r.try_get::<String>("", "name").unwrap_or_default(),
                    "slug": r.try_get::<String>("", "slug").unwrap_or_default(),
                    "status": r.try_get::<i64>("", "status").unwrap_or(1),
                    "price": crate::cart::dec(r, "price").unwrap_or(0.0),
                    "is_new": r.try_get::<bool>("", "is_new").unwrap_or(false),
                    "is_best": r.try_get::<bool>("", "is_best").unwrap_or(false),
                    "recommend": r.try_get::<bool>("", "recommend").unwrap_or(false),
                    "sort": r.try_get::<i64>("", "sort").unwrap_or(0),
                })
            })
            .collect(),
        total,
    ))
}

pub async fn get(db: &DatabaseConnection, id: i64) -> Result<Value, CatalogError> {
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT p.id, p.name, p.slug, p.status, p.price, p.compare_at, p.is_new, p.is_best, p.recommend, p.sort, p.description, p.fabric_care_note FROM product p WHERE p.id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    let colors = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT DISTINCT color FROM sku WHERE product_id = {} AND color IS NOT NULL AND color != ''",
                id
            ),
        ))
        .await
        .map_err(db_err)?;
    Ok(json!({
        "id": row.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "name": row.try_get::<String>("", "name").unwrap_or_default(),
        "slug": row.try_get::<String>("", "slug").unwrap_or_default(),
        "status": row.try_get::<i64>("", "status").unwrap_or(1),
        "price": crate::cart::dec(&row, "price").unwrap_or(0.0),
        "compare_at": crate::cart::dec(&row, "compare_at"),
        "is_new": row.try_get::<bool>("", "is_new").unwrap_or(false),
        "is_best": row.try_get::<bool>("", "is_best").unwrap_or(false),
        "recommend": row.try_get::<bool>("", "recommend").unwrap_or(false),
        "sort": row.try_get::<i64>("", "sort").unwrap_or(0),
        "description": row.try_get::<String>("", "description").ok(),
        "fabric_care_note": row.try_get::<String>("", "fabric_care_note").ok(),
        "colors": colors.iter().filter_map(|c| c.try_get::<String>("", "color").ok()).collect::<Vec<_>>(),
    }))
}

/// 状态切换(1=草稿 2=已发布 3=归档)
pub async fn toggle_status(db: &DatabaseConnection, id: i64, status: i64) -> Result<Value, CatalogError> {
    if !matches!(status, 1 | 2 | 3) {
        return Err(CatalogError::field_validation(&[("status", "invalid_enum")]));
    }
    let res = db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "UPDATE product SET status = ?, updated_at = NOW(3) WHERE id = ?",
            [status.into(), id.into()],
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(not_found());
    }
    get(db, id).await
}

/// flags 修补(is_new/is_best/recommend/sort)
pub async fn patch_flags(
    db: &DatabaseConnection,
    id: i64,
    is_new: Option<bool>,
    is_best: Option<bool>,
    recommend: Option<bool>,
    sort: Option<i64>,
) -> Result<Value, CatalogError> {
    let mut sets: Vec<String> = vec!["updated_at = NOW(3)".into()];
    if let Some(v) = is_new {
        sets.push(format!("is_new = {}", v as i64));
    }
    if let Some(v) = is_best {
        sets.push(format!("is_best = {}", v as i64));
    }
    if let Some(v) = recommend {
        sets.push(format!("recommend = {}", v as i64));
    }
    if let Some(v) = sort {
        sets.push(format!("sort = {}", v));
    }
    let res = db
        .execute(Statement::from_string(
            DbBackend::MySql,
            format!("UPDATE product SET {} WHERE id = {}", sets.join(", "), id),
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(not_found());
    }
    get(db, id).await
}

pub async fn delete(db: &DatabaseConnection, id: i64) -> Result<(), CatalogError> {
    let res = db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "DELETE FROM product WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(not_found());
    }
    Ok(())
}


// ══════════ E-CAT-09/11 商品创建/编辑(TX-CAT-001/002;Java AdminProductService.create/update + helper 全文移植) ══════════

use serde::Deserialize;
use sea_orm::DatabaseTransaction;

use crate::service_category::{find_by_id as cat_find_by_id, list_all as cat_list_all, CategoryRow};

/// V-CAT-024 slug ^[a-z0-9-]+$
fn slug_ok(s: &str) -> bool {
    !s.is_empty() && s.bytes().all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'-')
}

/// V-CAT-032 sku_code ^[A-Z0-9-]+$
fn sku_code_ok(s: &str) -> bool {
    !s.is_empty() && s.bytes().all(|b| b.is_ascii_uppercase() || b.is_ascii_digit() || b == b'-')
}

fn too_long(v: Option<&str>, max: usize) -> bool {
    v.map(|s| s.chars().count() > max).unwrap_or(false)
}

fn check_too_long(
    v: Option<&str>,
    max: usize,
    field: &'static str,
    fields: &mut Vec<(&'static str, &'static str)>,
) {
    if too_long(v, max) {
        fields.push((field, "too_long"));
    }
}

// ─────── 请求载荷(AdminProductUpsert record;snake_case 主名 + camelCase alias 兼容 Java 字段名) ───────

#[derive(Debug, Deserialize)]
pub struct ProductUpsertIn {
    pub name: Option<String>,
    pub slug: Option<String>,
    #[serde(alias = "categoryId")]
    pub category_id: Option<i64>,
    pub description: Option<String>,
    #[serde(alias = "designerNote")]
    pub designer_note: Option<String>,
    #[serde(alias = "sellingPoints")]
    pub selling_points: Option<Vec<String>>,
    pub price: Option<f64>,
    #[serde(alias = "compareAt")]
    pub compare_at: Option<f64>,
    pub installment: Option<bool>,
    #[serde(alias = "multiCurrencyPrices")]
    pub multi_currency_prices: Option<Value>,
    pub status: Option<i64>,
    #[serde(alias = "isNew")]
    pub is_new: Option<bool>,
    #[serde(alias = "isBest")]
    pub is_best: Option<bool>,
    pub recommend: Option<bool>,
    pub sort: Option<i64>,
    #[serde(alias = "leadTimeDays")]
    pub lead_time_days: Option<i64>,
    #[serde(alias = "rushAvailable")]
    pub rush_available: Option<bool>,
    #[serde(alias = "customSizeAvailable")]
    pub custom_size_available: Option<bool>,
    pub attributes: Option<Vec<AttributeValueIn>>,
    #[serde(alias = "styleNo")]
    pub style_no: Option<String>,
    #[serde(alias = "seoTitle")]
    pub seo_title: Option<String>,
    #[serde(alias = "seoDesc")]
    pub seo_desc: Option<String>,
    pub images: Option<Vec<ProductImageIn>>,
    pub skus: Option<Vec<SkuIn>>,
    #[serde(alias = "sizeChart")]
    pub size_chart: Option<Vec<SizeChartRowIn>>,
    #[serde(alias = "collectionIds")]
    pub collection_ids: Option<Vec<i64>>,
    pub translations: Option<Vec<ProductTranslationIn>>,
    /// 编辑端可选回传(无 SKU 携带 id 时的并发防丢失比对 → 409508)
    #[serde(alias = "updatedAt")]
    pub updated_at: Option<String>,
    #[serde(alias = "fabricCompositions")]
    pub fabric_compositions: Option<Value>,
    pub care: Option<Value>,
    #[serde(alias = "fabricCareNote")]
    pub fabric_care_note: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct SkuIn {
    pub id: Option<i64>,
    #[serde(alias = "skuCode")]
    pub sku_code: Option<String>,
    pub color: Option<String>,
    pub size: Option<String>,
    pub stock: Option<i64>,
    /// 带 id 行必带(CAS 乐观锁,409508)
    pub version: Option<i64>,
}

#[derive(Debug, Deserialize)]
pub struct ProductImageIn {
    pub url: Option<String>,
    /// 1=gallery 2=lifestyle 3=video 4=swatch(V-CAT-031)
    pub kind: Option<i64>,
    #[serde(alias = "colorName")]
    pub color_name: Option<String>,
    pub sort: Option<i64>,
}

#[derive(Debug, Deserialize)]
pub struct SizeChartRowIn {
    pub us: Option<String>,
    pub uk: Option<String>,
    pub au: Option<String>,
    pub bust: Option<f64>,
    pub waist: Option<f64>,
    pub hips: Option<f64>,
    #[serde(alias = "hollowToFloor")]
    pub hollow_to_floor: Option<f64>,
}

#[derive(Debug, Deserialize)]
pub struct ProductTranslationIn {
    pub locale: Option<String>,
    pub name: Option<String>,
    pub description: Option<String>,
    #[serde(alias = "designerNote")]
    pub designer_note: Option<String>,
    #[serde(alias = "sellingPoints")]
    pub selling_points: Option<Vec<String>>,
    #[serde(alias = "seoTitle")]
    pub seo_title: Option<String>,
    #[serde(alias = "seoDescription")]
    pub seo_description: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct AttributeValueIn {
    pub key: Option<String>,
    pub values: Option<Vec<String>>,
}

struct AttrDef {
    id: i64,
    /// 1=select 2=multiselect 3=text 4=toggle
    def_type: i64,
    options: Option<Vec<String>>,
}

struct UpsertCtx {
    category_exists: bool,
    existing_collection_ids: HashSet<i64>,
    defs_by_key: HashMap<String, AttrDef>,
    /// 分类生效非 hidden 属性 key 白名单;None=分类无效跳过 not_in_category(对齐 Java allowedKeys 语义)
    allowed_keys: Option<HashSet<String>>,
}

fn req_slug(req: &ProductUpsertIn) -> &str {
    req.slug.as_deref().unwrap_or("")
}

/// E-CAT-09 createAdminProduct(TX-CAT-001):校验 → slug 唯一(409501) → sku_code 唯一(409504)
/// → 单事务写主行+六子表 → AdminProductDetail 回读(201)。
pub async fn create(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    req: ProductUpsertIn,
    operator: &str,
) -> Result<Value, CatalogError> {
    let ctx = build_upsert_ctx(db, &req, None).await?;
    validate_upsert(&req, &ctx, None)?;
    // STEP-CAT-01 slug 唯一性 → 409501
    if slug_exists(db, req_slug(&req), None).await? {
        return Err(CatalogError::new(cat_err::SLUG_EXISTS));
    }
    // STEP-CAT-02 sku_code 全局唯一性 → 409504(details.sku_codes)
    check_sku_codes(db, req.skus.as_deref(), None).await?;
    let name = req.name.as_deref().map(str::trim).unwrap_or_default().to_string();
    let tx = db.begin().await.map_err(db_err)?;
    // STEP-CAT-03 INSERT product(缺省 false 标记;冗余列 sales_30d/rating_* 走列默认 0)
    let id = insert_product_tx(&tx, &req).await?;
    // STEP-CAT-04 批量 INSERT 子表(sku version=0;其余 DELETE+INSERT 整单覆盖)
    replace_images_tx(&tx, id, req.images.as_deref()).await?;
    insert_skus_tx(&tx, id, req.skus.as_deref()).await?;
    replace_size_chart_tx(&tx, id, req.size_chart.as_deref()).await?;
    replace_collections_tx(&tx, id, req.collection_ids.as_deref()).await?;
    replace_translations_tx(&tx, id, req.translations.as_deref()).await?;
    replace_attributes_tx(&tx, id, req.attributes.as_deref(), &ctx.defs_by_key).await?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "创建商品", &name).await;
    load_detail(db, id).await
}

/// E-CAT-11 updateAdminProduct(TX-CAT-002):存在(404501) → 校验(422501) → slug/sku_code 查重
/// → updated_at 可选防丢失比对(409508) → sku 差异化 CAS(409508) → 主行更新 → 子表整单覆盖 → 详情回读(200)。
pub async fn update(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    req: ProductUpsertIn,
    operator: &str,
) -> Result<Value, CatalogError> {
    // STEP-CAT-01 商品存在(行值预读供 updated_at 比对)
    let existing = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, updated_at FROM product WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    let owned_sku_ids = list_owned_sku_ids(db, id).await?;
    let owned_set: HashSet<i64> = owned_sku_ids.iter().copied().collect();
    let ctx = build_upsert_ctx(db, &req, Some(id)).await?;
    validate_upsert(&req, &ctx, Some(&owned_set))?;
    // STEP-CAT-02 slug 查重(排除自身)→ 409501;sku_code 查重(排除本商品行)→ 409504
    if slug_exists(db, req_slug(&req), Some(id)).await? {
        return Err(CatalogError::new(cat_err::SLUG_EXISTS));
    }
    check_sku_codes(db, req.skus.as_deref(), Some(id)).await?;
    // STEP-CAT-03 并发防丢失:无 SKU 携带 id 时以可选回传 updated_at 与 DB 比对(毫秒截断)→ 409508
    let has_carried_sku_ids = req.skus.as_ref().map_or(false, |v| v.iter().any(|s| s.id.is_some()));
    if !has_carried_sku_ids {
        if let Some(carried) = req.updated_at.as_deref().filter(|s| !s.trim().is_empty()) {
            let db_updated_at = existing.try_get::<chrono::NaiveDateTime>("", "updated_at").ok();
            check_updated_at_conflict(db_updated_at, carried)?;
        }
    }
    let name = req.name.as_deref().map(str::trim).unwrap_or_default().to_string();
    let tx = db.begin().await.map_err(db_err)?;
    // STEP-CAT-05 sku 差异化前置于主表更新(同一事务,原子性等价;CAS affected=0 → 整体回滚 409508)
    apply_sku_diff_tx(&tx, id, &owned_sku_ids, req.skus.as_deref()).await?;
    // STEP-CAT-04 UPDATE 主表(冗余列 sales_30d/sales_refreshed_at/rating_* 强制剔除不触碰;
    // 可空列省略=保留原值,对齐 MyBatis-Plus updateById NOT_NULL 字段策略)
    update_product_tx(&tx, id, &req).await?;
    // STEP-CAT-05 其余子表整单覆盖(DELETE+批量 INSERT)
    replace_images_tx(&tx, id, req.images.as_deref()).await?;
    replace_size_chart_tx(&tx, id, req.size_chart.as_deref()).await?;
    replace_collections_tx(&tx, id, req.collection_ids.as_deref()).await?;
    replace_translations_tx(&tx, id, req.translations.as_deref()).await?;
    replace_attributes_tx(&tx, id, req.attributes.as_deref(), &ctx.defs_by_key).await?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "编辑商品", &name).await;
    load_detail(db, id).await
}

// ─────── 校验上下文与纯校验器(V-CAT-023~038;对齐 ProductUpsertValidator.java) ───────

async fn build_upsert_ctx(
    db: &DatabaseConnection,
    req: &ProductUpsertIn,
    product_id: Option<i64>,
) -> Result<UpsertCtx, CatalogError> {
    // V-CAT-025 category 存在性(不存在 → fields.category_id=not_exists,契约无 404)
    let category: Option<CategoryRow> = match req.category_id {
        Some(cid) => cat_find_by_id(db, cid).await?,
        None => None,
    };
    // V-CAT-034 collection 存在集
    let mut existing_collection_ids: HashSet<i64> = HashSet::new();
    if let Some(ids) = req.collection_ids.as_deref().filter(|v| !v.is_empty()) {
        let in_list = ids.iter().map(|v| v.to_string()).collect::<Vec<_>>().join(",");
        let rows = db
            .query_all(Statement::from_string(
                DbBackend::MySql,
                format!("SELECT id FROM collection WHERE id IN ({in_list})"),
            ))
            .await
            .map_err(db_err)?;
        for r in rows {
            if let Ok(v) = r.try_get::<u64>("", "id") {
                existing_collection_ids.insert(v as i64);
            }
        }
    }
    // 属性字典全量 key 索引(动态属性校验数据源)
    let mut defs_by_key: HashMap<String, AttrDef> = HashMap::new();
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, `key` AS k, type, options FROM attribute_def".to_string(),
        ))
        .await
        .map_err(db_err)?;
    for r in rows {
        let id = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
        let key = r.try_get::<String>("", "k").unwrap_or_default();
        let def_type = r.try_get::<i64>("", "type").unwrap_or(0);
        let options = r
            .try_get::<Value>("", "options")
            .ok()
            .and_then(|v| serde_json::from_value::<Vec<String>>(v).ok());
        defs_by_key.insert(key, AttrDef { id, def_type, options });
    }
    // 分类生效非 hidden 白名单:沿祖先链最近 attribute_set_id 基底 + 子分类 attr_overrides delta
    // (对齐 ProductAttributeConfigService.visibleAttrs;分类无效传 None 跳过 not_in_category)
    let mut allowed_keys: Option<HashSet<String>> = None;
    if let Some(cat) = &category {
        let all: Vec<CategoryRow> = cat_list_all(db).await?;
        let by_id: HashMap<i64, &CategoryRow> = all.iter().map(|c| (c.id, c)).collect();
        let mut eff_set = cat.attribute_set_id;
        let mut cursor_pid = cat.parent_id;
        let mut hops = 0;
        while eff_set.is_none() && cursor_pid.is_some() && hops < 4 {
            let Some(p) = cursor_pid.and_then(|pid| by_id.get(&pid)) else { break };
            eff_set = p.attribute_set_id;
            cursor_pid = p.parent_id;
            hops += 1;
        }
        let mut vis_by_key: HashMap<String, i64> = HashMap::new();
        if let Some(sid) = eff_set {
            let rows = db
                .query_all(Statement::from_sql_and_values(
                    DbBackend::MySql,
                    "SELECT asi.attribute_id, asi.visibility FROM attribute_set_item asi WHERE asi.attribute_set_id = ?",
                    [sid.into()],
                ))
                .await
                .map_err(db_err)?;
            for r in rows {
                let aid = r.try_get::<u64>("", "attribute_id").map(|v| v as i64).unwrap_or(0);
                let vis = r.try_get::<i64>("", "visibility").unwrap_or(0);
                if let Some((k, _)) = defs_by_key.iter().find(|(_, d)| d.id == aid) {
                    vis_by_key.insert(k.clone(), vis);
                }
            }
        }
        // delta:仅子分类存 overrides(根分类写侧禁止);未知 key 跳过
        if cat.parent_id.is_some() {
            if let Some(obj) = cat.attr_overrides.as_ref().and_then(|v| v.as_object()) {
                for (k, v) in obj {
                    if let Some(n) = v.as_i64() {
                        if defs_by_key.contains_key(k) {
                            vis_by_key.insert(k.clone(), n);
                        }
                    }
                }
            }
        }
        let mut allowed: HashSet<String> = HashSet::new();
        for (k, vis) in vis_by_key {
            if vis != 3 {
                // 3=HIDDEN(AttributeVisibility)
                allowed.insert(k);
            }
        }
        // 编辑场景:放行该商品已持久化的存量 key(迁移遗留/属性集后续收缩不阻塞整单保存)
        if let Some(pid) = product_id {
            let rows = db
                .query_all(Statement::from_sql_and_values(
                    DbBackend::MySql,
                    "SELECT pav.attribute_id FROM product_attribute_value pav WHERE pav.product_id = ?",
                    [pid.into()],
                ))
                .await
                .map_err(db_err)?;
            for r in rows {
                let aid = r.try_get::<u64>("", "attribute_id").map(|v| v as i64).unwrap_or(0);
                if let Some((k, _)) = defs_by_key.iter().find(|(_, d)| d.id == aid) {
                    allowed.insert(k.clone());
                }
            }
        }
        allowed_keys = Some(allowed);
    }
    Ok(UpsertCtx {
        category_exists: category.is_some(),
        existing_collection_ids,
        defs_by_key,
        allowed_keys,
    })
}

/// V-CAT-023~038 统一入口:逐项收集,任一违规 → 422501 fields(一次性抛出)
fn validate_upsert(
    req: &ProductUpsertIn,
    ctx: &UpsertCtx,
    owned_sku_ids: Option<&HashSet<i64>>,
) -> Result<(), CatalogError> {
    let mut fields: Vec<(&'static str, &'static str)> = Vec::new();
    // V-CAT-023 name 必填 trim 非空 ≤128
    let name_trim = req.name.as_deref().map(str::trim).unwrap_or("");
    if name_trim.is_empty() {
        fields.push(("name", "required"));
    } else if name_trim.chars().count() > 128 {
        fields.push(("name", "too_long"));
    }
    // V-CAT-024 slug 必填 pattern ≤128
    match req.slug.as_deref() {
        None | Some("") => fields.push(("slug", "required")),
        Some(s) if s.chars().count() > 128 || !slug_ok(s) => fields.push(("slug", "pattern")),
        _ => {}
    }
    // V-CAT-025 category_id 必填且存在
    match req.category_id {
        None => fields.push(("category_id", "required")),
        Some(_) if !ctx.category_exists => fields.push(("category_id", "not_exists")),
        _ => {}
    }
    // V-CAT-026 price 必填 >= 0
    match req.price {
        None => fields.push(("price", "required")),
        Some(p) if p < 0.0 => fields.push(("price", "range_invalid")),
        _ => {}
    }
    // V-CAT-027 compare_at 为空或 >= price
    if let Some(c) = req.compare_at {
        if c < 0.0 {
            fields.push(("compare_at", "range_invalid"));
        } else if let Some(p) = req.price {
            if c < p {
                fields.push(("compare_at", "lt_price"));
            }
        }
    }
    // V-CAT-028 lead_time_days 必填 >= 1
    match req.lead_time_days {
        None => fields.push(("lead_time_days", "required")),
        Some(v) if v < 1 => fields.push(("lead_time_days", "range_invalid")),
        _ => {}
    }
    // V-CAT-029 status 必填枚举 {1=草稿, 2=已上架}
    match req.status {
        None => fields.push(("status", "required")),
        Some(1) | Some(2) => {}
        Some(_) => fields.push(("status", "invalid_enum")),
    }
    // V-CAT-030 sort >= 0
    if let Some(s) = req.sort {
        if s < 0 {
            fields.push(("sort", "range_invalid"));
        }
    }
    validate_images(req.images.as_deref(), &mut fields);
    validate_skus(req.skus.as_deref(), owned_sku_ids, &mut fields);
    validate_size_chart(req.size_chart.as_deref(), &mut fields);
    // V-CAT-034 collection_ids 全部存在(重复由写入侧保序去重)
    if let Some(ids) = req.collection_ids.as_deref() {
        for cid in ids {
            if !ctx.existing_collection_ids.contains(cid) {
                fields.push(("collection_ids", "not_exists"));
                break;
            }
        }
    }
    validate_translations(req.translations.as_deref(), &mut fields);
    // V-CAT-036 文本长度上限
    check_too_long(req.style_no.as_deref(), 32, "style_no", &mut fields);
    check_too_long(req.seo_title.as_deref(), 128, "seo_title", &mut fields);
    check_too_long(req.seo_desc.as_deref(), 255, "seo_desc", &mut fields);
    validate_attributes(req.attributes.as_deref(), ctx, &mut fields);
    if !fields.is_empty() {
        return Err(CatalogError::field_validation(&fields));
    }
    Ok(())
}

/// V-CAT-031 images(CV-CAT-010 主图不变量:gallery sort=0 至多一张)
fn validate_images(images: Option<&[ProductImageIn]>, fields: &mut Vec<(&'static str, &'static str)>) {
    let Some(images) = images else { return };
    let mut primary_gallery_count = 0usize;
    for image in images {
        let url = image.url.as_deref().unwrap_or("");
        if url.trim().is_empty() || url.chars().count() > 512 {
            fields.push(("images", "url_invalid"));
            return;
        }
        let kind = image.kind.unwrap_or(0);
        if !(1..=4).contains(&kind) {
            fields.push(("images", "invalid_enum"));
            return;
        }
        if let Some(s) = image.sort {
            if s < 0 {
                fields.push(("images", "range_invalid"));
                return;
            }
        }
        if kind == 1 && image.sort == Some(0) {
            primary_gallery_count += 1;
        }
        if kind == 4 {
            if let Some(cn) = image.color_name.as_deref() {
                if cn.chars().count() > 32 {
                    fields.push(("images", "color_name_too_long"));
                    return;
                }
            }
        }
    }
    if primary_gallery_count > 1 {
        fields.push(("images", "multiple_primary"));
    }
}

/// V-CAT-032 + V-CAT-038 skus(CV-CAT-011 组合唯一;编辑场景带 id 行须 owned 且带 version)
fn validate_skus(
    skus: Option<&[SkuIn]>,
    owned_sku_ids: Option<&HashSet<i64>>,
    fields: &mut Vec<(&'static str, &'static str)>,
) {
    let Some(skus) = skus else { return };
    let mut codes: HashSet<String> = HashSet::new();
    let mut color_sizes: HashSet<String> = HashSet::new();
    for sku in skus {
        let code = sku.sku_code.as_deref().unwrap_or("");
        if code.trim().is_empty() || code.chars().count() > 64 || !sku_code_ok(code) {
            fields.push(("skus", "sku_code_invalid"));
            return;
        }
        let color = sku.color.as_deref().unwrap_or("");
        if color.trim().is_empty() || color.chars().count() > 32 {
            fields.push(("skus", "color_invalid"));
            return;
        }
        let size = sku.size.as_deref().unwrap_or("");
        if size.trim().is_empty() || size.chars().count() > 16 {
            fields.push(("skus", "size_invalid"));
            return;
        }
        if let Some(st) = sku.stock {
            if st < 0 {
                fields.push(("skus", "stock_invalid"));
                return;
            }
        }
        // 提交集内 sku_code 不重复、(color,size) 不重复
        if !codes.insert(code.to_string()) {
            fields.push(("skus", "duplicated"));
            return;
        }
        if !color_sizes.insert(format!("{color} {size}")) {
            fields.push(("skus", "duplicated"));
            return;
        }
        // V-CAT-038(编辑场景):带 id 行须属于本商品且携带 version
        if let Some(owned) = owned_sku_ids {
            if let Some(id) = sku.id {
                if !owned.contains(&id) {
                    fields.push(("skus", "not_owned"));
                    return;
                }
                if sku.version.is_none() {
                    fields.push(("skus", "version_required"));
                    return;
                }
            }
        }
    }
}

/// V-CAT-033 size_chart(CV-CAT-003 数值域)
fn validate_size_chart(rows: Option<&[SizeChartRowIn]>, fields: &mut Vec<(&'static str, &'static str)>) {
    let Some(rows) = rows else { return };
    for row in rows {
        let us = row.us.as_deref().unwrap_or("");
        if us.trim().is_empty() || us.chars().count() > 8 {
            fields.push(("size_chart", "us_invalid"));
            return;
        }
        if too_long(row.uk.as_deref(), 8) || too_long(row.au.as_deref(), 8) {
            fields.push(("size_chart", "too_long"));
            return;
        }
        for v in [row.bust, row.waist, row.hips, row.hollow_to_floor] {
            if let Some(v) = v {
                if v < 0.0 {
                    fields.push(("size_chart", "range_invalid"));
                    return;
                }
            }
        }
    }
}

/// V-CAT-035 translations:locale ∈ {es,fr} 且不重复;文本上限
fn validate_translations(
    translations: Option<&[ProductTranslationIn]>,
    fields: &mut Vec<(&'static str, &'static str)>,
) {
    let Some(translations) = translations else { return };
    let mut seen: HashSet<String> = HashSet::new();
    for t in translations {
        let locale = t.locale.as_deref().unwrap_or("");
        if (locale != "es" && locale != "fr") || !seen.insert(locale.to_string()) {
            fields.push(("translations", "invalid_locale"));
            return;
        }
        if too_long(t.name.as_deref(), 128)
            || too_long(t.seo_title.as_deref(), 128)
            || too_long(t.seo_description.as_deref(), 255)
        {
            fields.push(("translations", "too_long"));
            return;
        }
    }
}

/// 动态属性 entries 校验:key 存在字典/在分类白名单/不重复;值按 type 校验
/// (select 单值 ∈ options / multiselect ⊆ options / toggle ∈ {true,false} / text ≤255 / 多值上限)
fn validate_attributes(
    entries: Option<&[AttributeValueIn]>,
    ctx: &UpsertCtx,
    fields: &mut Vec<(&'static str, &'static str)>,
) {
    let Some(entries) = entries else { return };
    if entries.is_empty() {
        return;
    }
    let mut seen: HashSet<String> = HashSet::new();
    for entry in entries {
        let key = entry.key.as_deref().unwrap_or("");
        if key.trim().is_empty() {
            fields.push(("attributes", "key_required"));
            return;
        }
        if !seen.insert(key.to_string()) {
            fields.push(("attributes", "duplicated"));
            return;
        }
        let Some(def) = ctx.defs_by_key.get(key) else {
            fields.push(("attributes", "attribute_not_exists"));
            return;
        };
        match &ctx.allowed_keys {
            None => {}
            Some(allowed) if !allowed.contains(key) => {
                fields.push(("attributes", "not_in_category"));
                return;
            }
            _ => {}
        }
        let values: Vec<&String> = entry
            .values
            .as_ref()
            .map(|v| v.iter().filter(|s| !s.trim().is_empty()).collect())
            .unwrap_or_default();
        if values.is_empty() {
            continue; // 空值 entry 视为未填,落库时跳过
        }
        if def.def_type != 2 && values.len() > 1 {
            fields.push(("attributes", "too_many_values"));
            return;
        }
        for value in values {
            if value.chars().count() > 255 {
                fields.push(("attributes", "too_long"));
                return;
            }
            if def.def_type == 4 && value != "true" && value != "false" {
                fields.push(("attributes", "invalid_toggle"));
                return;
            }
            if (def.def_type == 1 || def.def_type == 2)
                && def.options.as_ref().map_or(true, |o| !o.contains(value))
            {
                fields.push(("attributes", "invalid_option"));
                return;
            }
        }
    }
}

/// STEP-CAT-03(E-CAT-11)updated_at 并发比对(毫秒截断;解析失败 → 422501;不匹配 → 409508)
fn check_updated_at_conflict(db_updated_at: Option<chrono::NaiveDateTime>, carried: &str) -> Result<(), CatalogError> {
    let Some(carried) = parse_carried_ts(carried) else {
        return Err(CatalogError::field_validation(&[("updated_at", "invalid_format")]));
    };
    if let Some(dbv) = db_updated_at {
        if dbv.and_utc().timestamp_millis() != carried.and_utc().timestamp_millis() {
            return Err(CatalogError::new(cat_err::PRODUCT_VERSION_CONFLICT));
        }
    }
    Ok(())
}

/// Java LocalDateTime.parse ISO 口径(容错空格分隔)
fn parse_carried_ts(s: &str) -> Option<chrono::NaiveDateTime> {
    let s = s.trim();
    for fmt in [
        "%Y-%m-%dT%H:%M:%S%.f",
        "%Y-%m-%dT%H:%M:%S",
        "%Y-%m-%d %H:%M:%S%.f",
        "%Y-%m-%d %H:%M:%S",
    ] {
        if let Ok(t) = chrono::NaiveDateTime::parse_from_str(s, fmt) {
            return Some(t);
        }
    }
    None
}

// ─────── 查重辅助 ───────

async fn slug_exists(db: &DatabaseConnection, slug: &str, except_id: Option<i64>) -> Result<bool, CatalogError> {
    let cond = match except_id {
        Some(id) => format!(" AND id <> {id}"),
        None => String::new(),
    };
    let sql = format!(
        "SELECT id FROM product WHERE slug = '{}'{cond} LIMIT 1",
        slug.replace('\'', "''")
    );
    let row = db
        .query_one(Statement::from_string(DbBackend::MySql, sql))
        .await
        .map_err(db_err)?;
    Ok(row.is_some())
}

/// STEP-CAT-02 sku_code 全局唯一(排除本商品行)→ 409504 details.sku_codes
async fn check_sku_codes(
    db: &DatabaseConnection,
    skus: Option<&[SkuIn]>,
    except_product_id: Option<i64>,
) -> Result<(), CatalogError> {
    let Some(skus) = skus else { return Ok(()) };
    if skus.is_empty() {
        return Ok(());
    }
    let in_list = skus
        .iter()
        .map(|s| format!("'{}'", s.sku_code.as_deref().unwrap_or("").replace('\'', "''")))
        .collect::<Vec<_>>()
        .join(",");
    let except = match except_product_id {
        Some(pid) => format!(" AND product_id <> {pid}"),
        None => String::new(),
    };
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT DISTINCT sku_code FROM sku WHERE sku_code IN ({in_list}){except}"),
        ))
        .await
        .map_err(db_err)?;
    let taken: Vec<String> = rows
        .iter()
        .filter_map(|r| r.try_get::<String>("", "sku_code").ok())
        .collect();
    if !taken.is_empty() {
        return Err(CatalogError::new(cat_err::SKU_CODE_EXISTS).with_detail("sku_codes", json!(taken)));
    }
    Ok(())
}

async fn list_owned_sku_ids(db: &DatabaseConnection, product_id: i64) -> Result<Vec<i64>, CatalogError> {
    let rows = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id FROM sku WHERE product_id = ? ORDER BY id ASC",
            [product_id.into()],
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .filter_map(|r| r.try_get::<u64>("", "id").ok().map(|v| v as i64))
        .collect())
}

// ─────── 事务内写入 ───────

async fn inserted_id(tx: &DatabaseTransaction) -> Result<i64, CatalogError> {
    let id = tx
        .query_one(Statement::from_string(
            DbBackend::MySql,
            "SELECT LAST_INSERT_ID() AS id".to_string(),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<u64>("", "id").ok())
        .unwrap_or(0);
    Ok(id as i64)
}

/// STEP-CAT-03(E-CAT-09)INSERT product:布尔缺省 false、sort 缺省 0、lead_time 缺省 1
/// (对齐 applyUpsert 强制缺省;冗余列 sales_30d/rating_* 走列默认 0)
async fn insert_product_tx(tx: &DatabaseTransaction, req: &ProductUpsertIn) -> Result<i64, CatalogError> {
    let name = req.name.as_deref().map(str::trim).unwrap_or_default();
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO product(name, slug, category_id, description, designer_note, selling_points, price, compare_at, \
         installment, multi_currency_prices, status, is_new, is_best, recommend, sort, lead_time_days, rush_available, \
         custom_size_available, style_no, seo_title, seo_desc, fabric_compositions, care, fabric_care_note, \
         created_at, updated_at) \
         VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(3),NOW(3))",
        vec![
            name.into(),
            req.slug.clone().unwrap_or_default().into(),
            req.category_id.unwrap_or(0).into(),
            req.description.clone().into(),
            req.designer_note.clone().into(),
            req.selling_points.as_ref().map(|v| json!(v).to_string()).into(),
            req.price.unwrap_or(0.0).into(),
            req.compare_at.into(),
            (req.installment.unwrap_or(false) as i64).into(),
            req.multi_currency_prices.as_ref().map(|v| v.to_string()).into(),
            req.status.unwrap_or(1).into(),
            (req.is_new.unwrap_or(false) as i64).into(),
            (req.is_best.unwrap_or(false) as i64).into(),
            (req.recommend.unwrap_or(false) as i64).into(),
            req.sort.unwrap_or(0).into(),
            req.lead_time_days.unwrap_or(1).into(),
            (req.rush_available.unwrap_or(false) as i64).into(),
            (req.custom_size_available.unwrap_or(false) as i64).into(),
            req.style_no.clone().into(),
            req.seo_title.clone().into(),
            req.seo_desc.clone().into(),
            req.fabric_compositions.as_ref().map(|v| v.to_string()).into(),
            req.care.as_ref().map(|v| v.to_string()).into(),
            req.fabric_care_note.clone().into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    inserted_id(tx).await
}

/// STEP-CAT-04(E-CAT-11)UPDATE 主表:必写列(校验已保证非空)+ 可空列仅回传时写
/// (对齐 MP updateById NOT_NULL 策略:请求省略/显式 null 的可空列保留原值;updated_at=NOW(3))
async fn update_product_tx(tx: &DatabaseTransaction, id: i64, req: &ProductUpsertIn) -> Result<(), CatalogError> {
    let mut sets: Vec<String> = Vec::new();
    let mut vals: Vec<sea_orm::Value> = Vec::new();
    let name = req.name.as_deref().map(str::trim).unwrap_or_default();
    sets.push("name = ?".into());
    vals.push(name.into());
    sets.push("slug = ?".into());
    vals.push(req.slug.clone().unwrap_or_default().into());
    sets.push("category_id = ?".into());
    vals.push(req.category_id.unwrap_or(0).into());
    sets.push("price = ?".into());
    vals.push(req.price.unwrap_or(0.0).into());
    sets.push("installment = ?".into());
    vals.push((req.installment.unwrap_or(false) as i64).into());
    sets.push("status = ?".into());
    vals.push(req.status.unwrap_or(1).into());
    sets.push("is_new = ?".into());
    vals.push((req.is_new.unwrap_or(false) as i64).into());
    sets.push("is_best = ?".into());
    vals.push((req.is_best.unwrap_or(false) as i64).into());
    sets.push("recommend = ?".into());
    vals.push((req.recommend.unwrap_or(false) as i64).into());
    sets.push("sort = ?".into());
    vals.push(req.sort.unwrap_or(0).into());
    sets.push("lead_time_days = ?".into());
    vals.push(req.lead_time_days.unwrap_or(1).into());
    if let Some(v) = &req.description {
        sets.push("description = ?".into());
        vals.push(v.clone().into());
    }
    if let Some(v) = &req.designer_note {
        sets.push("designer_note = ?".into());
        vals.push(v.clone().into());
    }
    if let Some(v) = &req.selling_points {
        sets.push("selling_points = ?".into());
        vals.push(json!(v).to_string().into());
    }
    if let Some(v) = req.compare_at {
        sets.push("compare_at = ?".into());
        vals.push(v.into());
    }
    if let Some(v) = &req.multi_currency_prices {
        sets.push("multi_currency_prices = ?".into());
        vals.push(v.to_string().into());
    }
    if let Some(v) = &req.style_no {
        sets.push("style_no = ?".into());
        vals.push(v.clone().into());
    }
    if let Some(v) = &req.seo_title {
        sets.push("seo_title = ?".into());
        vals.push(v.clone().into());
    }
    if let Some(v) = &req.seo_desc {
        sets.push("seo_desc = ?".into());
        vals.push(v.clone().into());
    }
    if let Some(v) = &req.fabric_compositions {
        sets.push("fabric_compositions = ?".into());
        vals.push(v.to_string().into());
    }
    if let Some(v) = &req.care {
        sets.push("care = ?".into());
        vals.push(v.to_string().into());
    }
    if let Some(v) = &req.fabric_care_note {
        sets.push("fabric_care_note = ?".into());
        vals.push(v.clone().into());
    }
    sets.push("updated_at = NOW(3)".into());
    vals.push(id.into());
    let sql = format!("UPDATE product SET {} WHERE id = ?", sets.join(", "));
    tx.execute(Statement::from_sql_and_values(DbBackend::MySql, sql, vals))
        .await
        .map_err(db_err)?;
    Ok(())
}

/// SKU 差异化(TX-CAT-002):带 id 行 CAS UPDATE(version 不匹配 affected=0 → 409508 整体回滚);
/// 新行 INSERT(version=0);缺席既有行 DELETE(订单快照不受影响)
async fn apply_sku_diff_tx(
    tx: &DatabaseTransaction,
    product_id: i64,
    owned_sku_ids: &[i64],
    incoming: Option<&[SkuIn]>,
) -> Result<(), CatalogError> {
    let mut kept: HashSet<i64> = HashSet::new();
    let mut to_insert: Vec<&SkuIn> = Vec::new();
    for dto in incoming.unwrap_or_default() {
        if let Some(sid) = dto.id {
            kept.insert(sid);
            // RM-CAT-122 CAS:id + version 双条件;version=version+1;affected=0 → 409508
            let res = tx
                .execute(Statement::from_sql_and_values(
                    DbBackend::MySql,
                    "UPDATE sku SET sku_code = ?, color = ?, size = ?, stock = ?, version = version + 1, updated_at = NOW(3) WHERE id = ? AND version = ?",
                    [
                        dto.sku_code.clone().unwrap_or_default().into(),
                        dto.color.clone().unwrap_or_default().into(),
                        dto.size.clone().unwrap_or_default().into(),
                        dto.stock.unwrap_or(0).into(),
                        sid.into(),
                        dto.version.unwrap_or(0).into(),
                    ],
                ))
                .await
                .map_err(db_err)?;
            if res.rows_affected() == 0 {
                return Err(CatalogError::new(cat_err::PRODUCT_VERSION_CONFLICT));
            }
        } else {
            to_insert.push(dto);
        }
    }
    let absent: Vec<i64> = owned_sku_ids.iter().filter(|sid| !kept.contains(sid)).copied().collect();
    if !absent.is_empty() {
        let in_list = absent.iter().map(|v| v.to_string()).collect::<Vec<_>>().join(",");
        tx.execute(Statement::from_string(
            DbBackend::MySql,
            format!("DELETE FROM sku WHERE id IN ({in_list})"),
        ))
        .await
        .map_err(db_err)?;
    }
    for dto in &to_insert {
        insert_one_sku_tx(tx, product_id, dto).await?;
    }
    Ok(())
}

async fn insert_skus_tx(tx: &DatabaseTransaction, product_id: i64, skus: Option<&[SkuIn]>) -> Result<(), CatalogError> {
    for s in skus.unwrap_or_default() {
        insert_one_sku_tx(tx, product_id, s).await?;
    }
    Ok(())
}

/// 单行 INSERT sku(version=0;create 批量与 update 差异化新增共用)
async fn insert_one_sku_tx(tx: &DatabaseTransaction, product_id: i64, s: &SkuIn) -> Result<(), CatalogError> {
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO sku(product_id, sku_code, color, size, stock, version, created_at, updated_at) VALUES (?,?,?,?,?,0,NOW(3),NOW(3))",
        [
            product_id.into(),
            s.sku_code.clone().unwrap_or_default().into(),
            s.color.clone().unwrap_or_default().into(),
            s.size.clone().unwrap_or_default().into(),
            s.stock.unwrap_or(0).into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    Ok(())
}

async fn replace_images_tx(
    tx: &DatabaseTransaction,
    product_id: i64,
    images: Option<&[ProductImageIn]>,
) -> Result<(), CatalogError> {
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "DELETE FROM product_image WHERE product_id = ?",
        [product_id.into()],
    ))
    .await
    .map_err(db_err)?;
    for img in images.unwrap_or_default() {
        tx.execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO product_image(product_id, url, kind, color_name, sort, created_at, updated_at) VALUES (?,?,?,?,?,NOW(3),NOW(3))",
            [
                product_id.into(),
                img.url.clone().unwrap_or_default().into(),
                img.kind.unwrap_or(0).into(),
                img.color_name.clone().into(),
                img.sort.unwrap_or(0).into(),
            ],
        ))
        .await
        .map_err(db_err)?;
    }
    Ok(())
}

async fn replace_size_chart_tx(
    tx: &DatabaseTransaction,
    product_id: i64,
    rows: Option<&[SizeChartRowIn]>,
) -> Result<(), CatalogError> {
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "DELETE FROM size_chart_row WHERE product_id = ?",
        [product_id.into()],
    ))
    .await
    .map_err(db_err)?;
    for row in rows.unwrap_or_default() {
        tx.execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO size_chart_row(product_id, us, uk, au, bust, waist, hips, hollow_to_floor, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,NOW(3),NOW(3))",
            [
                product_id.into(),
                row.us.clone().unwrap_or_default().into(),
                row.uk.clone().into(),
                row.au.clone().into(),
                row.bust.into(),
                row.waist.into(),
                row.hips.into(),
                row.hollow_to_floor.into(),
            ],
        ))
        .await
        .map_err(db_err)?;
    }
    Ok(())
}

/// collection_ids 整单覆盖:LinkedHashSet 保序去重,sort=去重后序号 0,1,2...
async fn replace_collections_tx(
    tx: &DatabaseTransaction,
    product_id: i64,
    ids: Option<&[i64]>,
) -> Result<(), CatalogError> {
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "DELETE FROM product_collection WHERE product_id = ?",
        [product_id.into()],
    ))
    .await
    .map_err(db_err)?;
    let mut seen: HashSet<i64> = HashSet::new();
    let mut sort = 0i64;
    for cid in ids.unwrap_or_default() {
        if !seen.insert(*cid) {
            continue;
        }
        tx.execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO product_collection(product_id, collection_id, sort, created_at, updated_at) VALUES (?,?,?,NOW(3),NOW(3))",
            [product_id.into(), (*cid).into(), sort.into()],
        ))
        .await
        .map_err(db_err)?;
        sort += 1;
    }
    Ok(())
}

async fn replace_translations_tx(
    tx: &DatabaseTransaction,
    product_id: i64,
    translations: Option<&[ProductTranslationIn]>,
) -> Result<(), CatalogError> {
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "DELETE FROM product_translation WHERE product_id = ?",
        [product_id.into()],
    ))
    .await
    .map_err(db_err)?;
    for t in translations.unwrap_or_default() {
        tx.execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO product_translation(product_id, locale, name, description, designer_note, selling_points, seo_title, seo_description, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,NOW(3),NOW(3))",
            [
                product_id.into(),
                t.locale.clone().unwrap_or_default().into(),
                t.name.clone().into(),
                t.description.clone().into(),
                t.designer_note.clone().into(),
                t.selling_points.as_ref().map(|v| json!(v).to_string()).into(),
                t.seo_title.clone().into(),
                t.seo_description.clone().into(),
            ],
        ))
        .await
        .map_err(db_err)?;
    }
    Ok(())
}

/// entries 数组 → EAV 行:key→def id;值去重保序;空值跳过(校验器已保证合法性)
async fn replace_attributes_tx(
    tx: &DatabaseTransaction,
    product_id: i64,
    entries: Option<&[AttributeValueIn]>,
    defs_by_key: &HashMap<String, AttrDef>,
) -> Result<(), CatalogError> {
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "DELETE FROM product_attribute_value WHERE product_id = ?",
        [product_id.into()],
    ))
    .await
    .map_err(db_err)?;
    for entry in entries.unwrap_or_default() {
        let Some(key) = entry.key.as_deref() else { continue };
        let Some(def) = defs_by_key.get(key) else { continue };
        let mut seen: HashSet<&str> = HashSet::new();
        for v in entry.values.iter().flatten() {
            if v.trim().is_empty() || !seen.insert(v.as_str()) {
                continue;
            }
            tx.execute(Statement::from_sql_and_values(
                DbBackend::MySql,
                "INSERT INTO product_attribute_value(product_id, attribute_id, value, created_at, updated_at) VALUES (?,?,?,NOW(3),NOW(3))",
                [product_id.into(), def.id.into(), v.clone().into()],
            ))
            .await
            .map_err(db_err)?;
        }
    }
    Ok(())
}

// ─────── 详情回读(MAP-CAT-004;E-CAT-09/11 出参 AdminProductDetail 33 字段) ───────

async fn load_detail(db: &DatabaseConnection, id: i64) -> Result<Value, CatalogError> {
    let Some(row) = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, name, slug, category_id, description, designer_note, selling_points, price, compare_at, \
             installment, multi_currency_prices, status, is_new, is_best, recommend, sort, lead_time_days, \
             rush_available, custom_size_available, style_no, seo_title, seo_desc, created_at, updated_at, \
             fabric_compositions, care, fabric_care_note FROM product WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?
    else {
        return Err(not_found());
    };
    // EAV 回读 → entries 数组(key 取字典;按属性首行 id 序分组,行序即写入序)
    let pav_rows = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT pav.value, ad.`key` AS attr_key FROM product_attribute_value pav \
             JOIN attribute_def ad ON ad.id = pav.attribute_id WHERE pav.product_id = ? ORDER BY pav.id ASC",
            [id.into()],
        ))
        .await
        .map_err(db_err)?;
    let mut attr_order: Vec<String> = Vec::new();
    let mut attr_vals: HashMap<String, Vec<String>> = HashMap::new();
    for r in pav_rows {
        let key = r.try_get::<String>("", "attr_key").unwrap_or_default();
        let val = r.try_get::<String>("", "value").unwrap_or_default();
        if !attr_vals.contains_key(&key) {
            attr_order.push(key.clone());
        }
        attr_vals.entry(key).or_default().push(val);
    }
    let attributes: Vec<Value> = attr_order
        .iter()
        .map(|key| json!({ "key": key, "values": attr_vals.get(key).cloned().unwrap_or_default() }))
        .collect();
    let sku_rows = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, sku_code, color, size, stock, version FROM sku WHERE product_id = ? ORDER BY id ASC",
            [id.into()],
        ))
        .await
        .map_err(db_err)?;
    let skus: Vec<Value> = sku_rows
        .iter()
        .map(|r| {
            json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "sku_code": r.try_get::<String>("", "sku_code").unwrap_or_default(),
                "color": r.try_get::<String>("", "color").unwrap_or_default(),
                "size": r.try_get::<String>("", "size").unwrap_or_default(),
                "stock": r.try_get::<i64>("", "stock").unwrap_or(0),
                "version": r.try_get::<i64>("", "version").unwrap_or(0),
            })
        })
        .collect();
    let image_rows = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, url, kind, color_name, sort FROM product_image WHERE product_id = ? ORDER BY sort ASC, id ASC",
            [id.into()],
        ))
        .await
        .map_err(db_err)?;
    let images: Vec<Value> = image_rows
        .iter()
        .map(|r| {
            json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "url": r.try_get::<String>("", "url").unwrap_or_default(),
                "kind": r.try_get::<i64>("", "kind").unwrap_or(0),
                "color_name": r.try_get::<String>("", "color_name").ok(),
                "sort": r.try_get::<i64>("", "sort").unwrap_or(0),
            })
        })
        .collect();
    let scr_rows = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, us, uk, au, bust, waist, hips, hollow_to_floor FROM size_chart_row WHERE product_id = ? ORDER BY id ASC",
            [id.into()],
        ))
        .await
        .map_err(db_err)?;
    let size_chart: Vec<Value> = scr_rows
        .iter()
        .map(|r| {
            json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "us": r.try_get::<String>("", "us").unwrap_or_default(),
                "uk": r.try_get::<String>("", "uk").ok(),
                "au": r.try_get::<String>("", "au").ok(),
                "bust": crate::cart::dec(r, "bust"),
                "waist": crate::cart::dec(r, "waist"),
                "hips": crate::cart::dec(r, "hips"),
                "hollow_to_floor": crate::cart::dec(r, "hollow_to_floor"),
            })
        })
        .collect();
    let pc_rows = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT collection_id FROM product_collection WHERE product_id = ? ORDER BY id ASC",
            [id.into()],
        ))
        .await
        .map_err(db_err)?;
    let collection_ids: Vec<i64> = pc_rows
        .iter()
        .filter_map(|r| r.try_get::<u64>("", "collection_id").ok().map(|v| v as i64))
        .collect();
    let tr_rows = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT locale, name, description, designer_note, selling_points, seo_title, seo_description \
             FROM product_translation WHERE product_id = ? ORDER BY id ASC",
            [id.into()],
        ))
        .await
        .map_err(db_err)?;
    let translations: Vec<Value> = tr_rows
        .iter()
        .map(|r| {
            json!({
                "locale": r.try_get::<String>("", "locale").unwrap_or_default(),
                "name": r.try_get::<String>("", "name").ok(),
                "description": r.try_get::<String>("", "description").ok(),
                "designer_note": r.try_get::<String>("", "designer_note").ok(),
                "selling_points": r.try_get::<Value>("", "selling_points").ok(),
                "seo_title": r.try_get::<String>("", "seo_title").ok(),
                "seo_description": r.try_get::<String>("", "seo_description").ok(),
            })
        })
        .collect();
    let fmt_time = |col: &str| -> Option<String> {
        row.try_get::<chrono::NaiveDateTime>("", col)
            .ok()
            .map(|t| t.format("%Y-%m-%dT%H:%M:%S").to_string())
    };
    Ok(json!({
        "id": row.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "name": row.try_get::<String>("", "name").unwrap_or_default(),
        "slug": row.try_get::<String>("", "slug").unwrap_or_default(),
        "category_id": row.try_get::<i64>("", "category_id").unwrap_or(0),
        "description": row.try_get::<String>("", "description").ok(),
        "designer_note": row.try_get::<String>("", "designer_note").ok(),
        "selling_points": row.try_get::<Value>("", "selling_points").ok(),
        "price": crate::cart::dec(&row, "price").unwrap_or(0.0),
        "compare_at": crate::cart::dec(&row, "compare_at"),
        "installment": row.try_get::<bool>("", "installment").unwrap_or(false),
        "multi_currency_prices": row.try_get::<Value>("", "multi_currency_prices").ok(),
        "status": row.try_get::<i64>("", "status").unwrap_or(1),
        "is_new": row.try_get::<bool>("", "is_new").unwrap_or(false),
        "is_best": row.try_get::<bool>("", "is_best").unwrap_or(false),
        "recommend": row.try_get::<bool>("", "recommend").unwrap_or(false),
        "sort": row.try_get::<i64>("", "sort").unwrap_or(0),
        "lead_time_days": row.try_get::<i64>("", "lead_time_days").unwrap_or(1),
        "rush_available": row.try_get::<bool>("", "rush_available").unwrap_or(false),
        "custom_size_available": row.try_get::<bool>("", "custom_size_available").unwrap_or(false),
        "attributes": attributes,
        "style_no": row.try_get::<String>("", "style_no").ok(),
        "seo_title": row.try_get::<String>("", "seo_title").ok(),
        "seo_desc": row.try_get::<String>("", "seo_desc").ok(),
        "images": images,
        "skus": skus,
        "size_chart": size_chart,
        "collection_ids": collection_ids,
        "translations": translations,
        "created_at": fmt_time("created_at"),
        "updated_at": fmt_time("updated_at"),
        "fabric_compositions": row.try_get::<Value>("", "fabric_compositions").ok(),
        "care": row.try_get::<Value>("", "care").ok(),
        "fabric_care_note": row.try_get::<String>("", "fabric_care_note").ok(),
    }))
}

/// 审计(best-effort;operation_log 在主库 dreamy_server,与业务库事务解耦——对齐 collection.rs 口径)
async fn audit(db: &DatabaseConnection, operator_id: &str, action: &str, target: &str) {
    let oid: Option<i64> = operator_id.parse().ok();
    if let Err(e) = db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO operation_log(operator_id, operator_name, action, target, ip, user_agent, changes, created_at, updated_at) VALUES (?,NULL,?,?,NULL,NULL,NULL,NOW(3),NOW(3))",
            [oid.into(), action.into(), target.into()],
        ))
        .await
    {
        tracing::warn!("[p-admin] audit best-effort failed:{e}");
    }
}
// ══════════ CSV 导出(Java AdminProductExportService;keyset 500/上限 10000/BOM/截断标记) ══════════

/// CV-CAT-01 标准 CSV 转义:含逗号/引号/换行 → 双引号包裹 + 引号翻倍
pub fn csv_esc(value: Option<String>) -> String {
    let Some(s) = value else { return String::new() };
    if s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r') {
        format!("\"{}\"", s.replace('"', "\"\""))
    } else {
        s
    }
}

pub async fn export(
    db: &DatabaseConnection,
    status: Option<i64>,
    category_id: Option<i64>,
    search: Option<&str>,
) -> Result<(Vec<u8>, bool, String, usize), CatalogError> {
    // V-011/V-012:status 枚举、search 长度
    if let Some(s) = status {
        if !(1..=2).contains(&s) {
            return Err(CatalogError::field_validation(&[("status", "invalid_enum")]));
        }
    }
    if let Some(k) = search {
        if k.trim().len() > 80 {
            return Err(CatalogError::field_validation(&[("search", "too_long")]));
        }
    }
    let normalized_search = search.map(str::trim).filter(|s| !s.is_empty()).map(String::from);
    // STEP-01:category_id 含子树
    let all_cats = crate::service_category::list_all(db).await?;
    let cat_ids: Vec<i64> = match category_id {
        Some(cid) => crate::service_category::subtree_ids(&all_cats, cid),
        None => Vec::new(),
    };
    let cat_names: std::collections::HashMap<i64, String> = all_cats
        .into_iter()
        .map(|c| (c.id, c.name))
        .collect();
    const PAGE_SIZE: usize = 500;
    const MAX_ROWS: usize = 10000;
    let header = "id,name,slug,style_no,category_name,price,compare_at,status,is_new,recommend,sort,stock_total,sales_total";
    let mut csv = String::from(header);
    csv.push('\n');
    let mut last_id: i64 = 0;
    let mut rows: usize = 0;
    let mut truncated = false;
    loop {
        let mut conds: Vec<String> = vec![format!("p.id > {}", last_id)];
        if let Some(s) = status {
            conds.push(format!("p.status = {}", s));
        }
        if !cat_ids.is_empty() {
            let list = cat_ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
            conds.push(format!("p.category_id IN ({})", list));
        }
        if let Some(k) = &normalized_search {
            let k = k.replace('\'', "''");
            conds.push(format!("(p.name LIKE '%{}%' OR p.slug LIKE '%{}%')", k, k));
        }
        let w = format!(" WHERE {}", conds.join(" AND "));
        let batch = db
            .query_all(Statement::from_string(
                DbBackend::MySql,
                format!(
                    "SELECT p.id, p.name, p.slug, p.style_no, p.category_id, p.price, p.compare_at, p.status, p.is_new, p.recommend, p.sort                      FROM product p{} ORDER BY p.id ASC LIMIT {}",
                    w, PAGE_SIZE
                ),
            ))
            .await
            .map_err(db_err)?;
        if batch.is_empty() {
            break;
        }
        let ids: Vec<i64> = batch
            .iter()
            .filter_map(|r| r.try_get::<u64>("", "id").ok().map(|v| v as i64))
            .collect();
        let id_list = ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
        let mut stock: std::collections::HashMap<i64, i64> = std::collections::HashMap::new();
        for r in db
            .query_all(Statement::from_string(
                DbBackend::MySql,
                format!("SELECT product_id, SUM(stock) AS n FROM sku WHERE product_id IN ({}) GROUP BY product_id", id_list),
            ))
            .await
            .unwrap_or_default()
            .iter()
        {
            if let (Some(pid), Some(n)) = (
                r.try_get::<u64>("", "product_id").ok().map(|v| v as i64),
                r.try_get::<u64>("", "n").ok().map(|v| v as i64),
            ) {
                stock.insert(pid, n);
            }
        }
        let mut sales: std::collections::HashMap<i64, i64> = std::collections::HashMap::new();
        for r in db
            .query_all(Statement::from_string(
                DbBackend::MySql,
                format!("SELECT product_id, SUM(qty) AS n FROM order_line WHERE product_id IN ({}) GROUP BY product_id", id_list),
            ))
            .await
            .unwrap_or_default()
            .iter()
        {
            if let (Some(pid), Some(n)) = (
                r.try_get::<u64>("", "product_id").ok().map(|v| v as i64),
                r.try_get::<u64>("", "n").ok().map(|v| v as i64),
            ) {
                sales.insert(pid, n);
            }
        }
        for r in batch.iter() {
            if rows >= MAX_ROWS {
                truncated = true;
                break;
            }
            let id = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            let cat_name = r
                .try_get::<i64>("", "category_id")
                .ok()
                .and_then(|cid| cat_names.get(&cid).cloned());
            let row = format!(
                "{},{},{},{},{},{},{},{},{},{},{},{},{}",
                csv_esc(Some(id.to_string())),
                csv_esc(r.try_get::<String>("", "name").ok()),
                csv_esc(r.try_get::<String>("", "slug").ok()),
                csv_esc(r.try_get::<String>("", "style_no").ok()),
                csv_esc(cat_name),
                csv_esc(crate::cart::dec(r, "price").map(|v| v.to_string())),
                csv_esc(crate::cart::dec(r, "compare_at").map(|v| v.to_string())),
                csv_esc(r.try_get::<i64>("", "status").ok().map(|v| v.to_string())),
                r.try_get::<bool>("", "is_new").unwrap_or(false),
                r.try_get::<bool>("", "recommend").unwrap_or(false),
                csv_esc(r.try_get::<i64>("", "sort").ok().map(|v| v.to_string())),
                stock.get(&id).copied().unwrap_or(0),
                sales.get(&id).copied().unwrap_or(0),
            );
            csv.push_str(&row);
            csv.push('\n');
            rows += 1;
            last_id = id;
        }
        if truncated || batch.len() < PAGE_SIZE {
            break;
        }
    }
    if truncated {
        csv.push_str("# TRUNCATED AT 10000 ROWS\n");
    }
    let filename = format!(
        "products-{}.csv",
        chrono::Local::now().format("%Y%m%d")
    );
    let mut content = vec![0xEF, 0xBB, 0xBF];
    content.extend_from_slice(csv.as_bytes());
    Ok((content, truncated, filename, rows))
}
