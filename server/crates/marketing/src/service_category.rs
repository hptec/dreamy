//! category 域(第二批首位;对齐 Java AdminCategoryService/StoreCategoryService/CategoryTreeService)。
//! 跨域依赖处理:
//! - product_count:直查 identity 库 product 表 count group by category_id(迁移期同库联合查询,与 Java 同库同语义)
//! - attribute_set/attribute_def 校验:同库直查(effective set 沿祖先链)
//! 迁移完成后这两类查询随 product/attribute 域迁入各自 crate,此文件仅保留树/CRUD 纯逻辑。

use std::collections::HashMap;

use sea_orm::{
    ConnectionTrait, DatabaseConnection, DbBackend, Statement, TransactionTrait,
};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::trading_error::TradingError; // 复用字段级 details 形状(码表独立,见 catalog_error)

/// catalog 域 6 位错误码(域段 5;对齐 CatalogErrorCode 本域用到的码)
pub mod cat_err {
    pub const PRODUCT_NOT_FOUND: i32 = 404501;
    pub const CATEGORY_NOT_FOUND: i32 = 404502;
    pub const ATTRIBUTE_SET_NOT_FOUND: i32 = 404503;
    pub const CATEGORY_HAS_PRODUCTS: i32 = 409502;
    pub const COLLECTION_GROUP_IN_USE: i32 = 409506;
    pub const CATEGORY_LEVEL_EXCEEDED: i32 = 409505;
    pub const FIELD_VALIDATION: i32 = 422501;

    pub fn http_status(code: i32) -> u16 {
        match code {
            404501 | 404502 | 404503 | 404906 | 404601 | 404602 | 404603 | 404604 | 404605 | 404907 => 404,
            404701 => 404,
            409502 | 409505 | 409506 | 409507 => 409,
            409601 | 409602 | 409603 | 409604 | 409605 => 409,
            409905 | 409906 | 409907 | 409908 | 409909 => 409,
            409703 => 409,
            422501 | 422704 | 422705 | 422907 => 422,
            422601 | 422602 | 422603 | 422604 | 422605 => 422,
            422906 | 422907 | 422908 => 422,
            410601 => 410,
            429601 => 429,
            _ => 500,
        }
    }
}

#[derive(Debug)]
pub struct CatalogError {
    pub code: i32,
    pub details: Option<Value>,
}

impl CatalogError {
    pub fn new(code: i32) -> Self {
        CatalogError { code, details: None }
    }
    pub fn with_detail(mut self, k: &str, v: Value) -> Self {
        let mut m = serde_json::Map::new();
        m.insert(k.into(), v);
        self.details = Some(Value::Object(m));
        self
    }
    pub fn field_validation(fields: &[(&'static str, &'static str)]) -> Self {
        let map: serde_json::Map<String, Value> = fields
            .iter()
            .map(|(f, r)| (f.to_string(), Value::String(r.to_string())))
            .collect();
        CatalogError {
            code: cat_err::FIELD_VALIDATION,
            details: Some(json_obj_fields(map)),
        }
    }
}

fn json_obj_fields(map: serde_json::Map<String, Value>) -> Value {
    let mut root = serde_json::Map::new();
    root.insert("fields".into(), Value::Object(map));
    Value::Object(root)
}

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[cat] db error:{e}");
    CatalogError::new(500501)
}

/// Category 行(listAll 全量;树逻辑纯内存)
#[derive(Debug, Clone)]
pub struct CategoryRow {
    pub id: i64,
    pub name: String,
    pub parent_id: Option<i64>,
    pub level: i8,
    pub attribute_set_id: Option<i64>,
    pub attr_overrides: Option<Value>,
    pub sort: i32,
}

/// Translation 行
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct TranslationDto {
    pub locale: String,
    pub name: Option<String>,
}

pub async fn list_all(db: &DatabaseConnection) -> Result<Vec<CategoryRow>, CatalogError> {
    let rows: Vec<sea_orm::QueryResult> = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, name, parent_id, level, attribute_set_id, attr_overrides, sort FROM category",
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .into_iter()
        .map(|r| CategoryRow {
            id: r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
            name: r.try_get::<String>("", "name").unwrap_or_default(),
            parent_id: r.try_get::<i64>("", "parent_id").ok(),
            level: r.try_get::<i8>("", "level").unwrap_or(1),
            attribute_set_id: r.try_get::<i64>("", "attribute_set_id").ok(),
            attr_overrides: r.try_get::<Value>("", "attr_overrides").ok(),
            sort: r.try_get::<i32>("", "sort").unwrap_or(0),
        })
        .collect())
}

pub async fn find_by_id(db: &DatabaseConnection, id: i64) -> Result<Option<CategoryRow>, CatalogError> {
    Ok(list_all(db)
        .await?
        .into_iter()
        .find(|c| c.id == id))
}

/// 子树 id 集(含自身;BFS;不存在 → 空集)
pub fn subtree_ids(all: &[CategoryRow], category_id: i64) -> Vec<i64> {
    if !all.iter().any(|c| c.id == category_id) {
        return vec![];
    }
    let mut by_parent: HashMap<i64, Vec<i64>> = HashMap::new();
    for c in all {
        if let Some(p) = c.parent_id {
            by_parent.entry(p).or_default().push(c.id);
        }
    }
    let mut result = vec![];
    let mut queue = std::collections::VecDeque::new();
    queue.push_back(category_id);
    while let Some(cur) = queue.pop_front() {
        result.push(cur);
        if let Some(children) = by_parent.get(&cur) {
            for ch in children {
                queue.push_back(*ch);
            }
        }
    }
    result
}

/// 层级计算:根=1,否则 parent.level+1
pub fn compute_level(parent: Option<&CategoryRow>) -> i8 {
    match parent {
        None => 1,
        Some(p) => p.level + 1,
    }
}

/// product_count 自底向上累加(leaf 直值,祖先=子孙和)
pub fn rollup_counts(all: &[CategoryRow], leaf_counts: &HashMap<i64, i64>) -> HashMap<i64, i64> {
    let mut by_parent: HashMap<i64, Vec<i64>> = HashMap::new();
    for c in all {
        if let Some(p) = c.parent_id {
            by_parent.entry(p).or_default().push(c.id);
        }
    }
    let mut out: HashMap<i64, i64> = HashMap::new();
    // 3 层以内,直接逐节点求子树和(与 Java 递归累计同结果)
    fn subtree_sum(id: i64, by_parent: &HashMap<i64, Vec<i64>>, leaf_counts: &HashMap<i64, i64>) -> i64 {
        let own = leaf_counts.get(&id).copied().unwrap_or(0);
        by_parent
            .get(&id)
            .map(|children| own + children.iter().map(|c| subtree_sum(*c, by_parent, leaf_counts)).sum::<i64>())
            .unwrap_or(own)
    }
    for c in all {
        out.insert(c.id, subtree_sum(c.id, &by_parent, leaf_counts));
    }
    out
}

/// product count:全部口径(含 draft;后台树)
pub async fn product_count_all(db: &DatabaseConnection) -> Result<HashMap<i64, i64>, CatalogError> {
    count_group_by(db, "SELECT category_id AS category_id, COUNT(*) AS n FROM product GROUP BY category_id")
        .await
}

/// product count:published 口径(店铺树)
pub async fn product_count_published(db: &DatabaseConnection) -> Result<HashMap<i64, i64>, CatalogError> {
    count_group_by(db, "SELECT category_id AS category_id, COUNT(*) AS n FROM product WHERE status = 2 GROUP BY category_id")
        .await
}

async fn count_group_by(db: &DatabaseConnection, sql: &str) -> Result<HashMap<i64, i64>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(DbBackend::MySql, sql.to_string()))
        .await
        .map_err(db_err)?;
    let mut m = HashMap::new();
    for r in rows {
        let cid: Option<i64> = r.try_get("", "category_id").ok();
        let n: i64 = r.try_get("", "n").unwrap_or(0);
        if let Some(cid) = cid {
            m.insert(cid, n);
        }
    }
    Ok(m)
}

/// 翻译批查(按 category ids)
pub async fn translations_map(
    db: &DatabaseConnection,
    locale: &str,
) -> Result<HashMap<i64, String>, CatalogError> {
    let mut m = HashMap::new();
    if locale != "es" && locale != "fr" {
        return Ok(m);
    }
    let sql = format!(
        "SELECT category_id, name FROM category_translation WHERE locale = '{}' AND name IS NOT NULL AND name != ''",
        locale.replace('\'', "")
    );
    let rows = db
        .query_all(Statement::from_string(DbBackend::MySql, sql))
        .await
        .map_err(db_err)?;
    for r in rows {
        let cid: i64 = r.try_get("", "category_id").unwrap_or(0);
        let name: String = r.try_get("", "name").unwrap_or_default();
        m.insert(cid, name);
    }
    Ok(m)
}

/// StoreCategoryNode(E-CAT-06;字段序 id,name,parent_id,level,sort,product_count,children)
#[derive(Debug, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct StoreCategoryNode {
    pub id: i64,
    pub name: String,
    pub parent_id: Option<i64>,
    pub level: i8,
    pub sort: i32,
    pub product_count: i64,
    pub children: Vec<StoreCategoryNode>,
}

/// 店铺三层树(published 口径 + locale 翻译回退 EN)
pub async fn store_tree(
    db: &DatabaseConnection,
    locale: &str,
) -> Result<Vec<StoreCategoryNode>, CatalogError> {
    let all = list_all(db).await?;
    let counts = rollup_counts(&all, &product_count_published(db).await?);
    let translated = translations_map(db, locale).await?;
    Ok(build_store_tree(&all, None, &counts, &translated))
}

fn build_store_tree(
    all: &[CategoryRow],
    parent: Option<i64>,
    counts: &HashMap<i64, i64>,
    translated: &HashMap<i64, String>,
) -> Vec<StoreCategoryNode> {
    let mut nodes: Vec<StoreCategoryNode> = all
        .iter()
        .filter(|c| c.parent_id == parent)
        .map(|c| StoreCategoryNode {
            id: c.id,
            name: translated.get(&c.id).cloned().unwrap_or_else(|| c.name.clone()),
            parent_id: c.parent_id,
            level: c.level,
            sort: c.sort,
            product_count: counts.get(&c.id).copied().unwrap_or(0),
            children: build_store_tree(all, Some(c.id), counts, translated),
        })
        .collect();
    nodes.sort_by_key(|n| (n.sort, n.id));
    nodes
}

/// AdminCategoryNode(E-CAT-15;product_count 全量口径;translations 原样)
#[derive(Debug, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct AdminCategoryNode {
    pub id: i64,
    pub name: String,
    pub parent_id: Option<i64>,
    pub level: i8,
    pub attribute_set_id: Option<i64>,
    pub attr_overrides: Option<Value>,
    pub sort: i32,
    pub product_count: i64,
    pub translations: Vec<TranslationDto>,
}

/// 后台三层树
pub async fn admin_tree(db: &DatabaseConnection) -> Result<Vec<AdminCategoryNode>, CatalogError> {
    let all = list_all(db).await?;
    let counts = rollup_counts(&all, &product_count_all(db).await?);
    let mut translations: HashMap<i64, Vec<TranslationDto>> = HashMap::new();
    let trows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT category_id, locale, name FROM category_translation",
        ))
        .await
        .map_err(db_err)?;
    for r in trows {
        let cid: i64 = r.try_get("", "category_id").unwrap_or(0);
        translations.entry(cid).or_default().push(TranslationDto {
            locale: r.try_get("", "locale").unwrap_or_default(),
            name: r.try_get::<Option<String>>("", "name").unwrap_or(None),
        });
    }
    Ok(build_admin_tree(&all, None, &counts, &translations))
}

fn build_admin_tree(
    all: &[CategoryRow],
    parent: Option<i64>,
    counts: &HashMap<i64, i64>,
    translations: &HashMap<i64, Vec<TranslationDto>>,
) -> Vec<AdminCategoryNode> {
    let mut nodes: Vec<AdminCategoryNode> = all
        .iter()
        .filter(|c| c.parent_id == parent)
        .map(|c| AdminCategoryNode {
            id: c.id,
            name: c.name.clone(),
            parent_id: c.parent_id,
            level: c.level,
            attribute_set_id: c.attribute_set_id,
            attr_overrides: c.attr_overrides.clone(),
            sort: c.sort,
            product_count: counts.get(&c.id).copied().unwrap_or(0),
            translations: translations.get(&c.id).cloned().unwrap_or_default(),
        })
        .collect();
    nodes.sort_by_key(|n| (n.sort, n.id));
    nodes
}

// ══════════════════ Admin CRUD(E-CAT-16/17/18;TX-CAT-006~008)══════════════════

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct AdminCategoryUpsert {
    pub name: Option<String>,
    pub parent_id: Option<i64>,
    pub attribute_set_id: Option<i64>,
    pub attr_overrides: Option<Value>,
    pub sort: Option<i32>,
    pub translations: Option<Vec<TranslationInput>>,
}

#[derive(Debug, Deserialize, Clone)]
#[serde(rename_all = "snake_case")]
pub struct TranslationInput {
    pub locale: String,
    pub name: Option<String>,
}

/// 校验(V-CAT-043~048)。create 场景需 parent 存在性;update 场景 parent_id 不可变已在调用方先行。
async fn validate_upsert(
    db: &DatabaseConnection,
    req: &AdminCategoryUpsert,
) -> Result<Option<CategoryRow>, CatalogError> {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let name = req.name.as_deref().map(str::trim).unwrap_or("");
    if name.is_empty() {
        fields.push(("name", "required"));
    } else if name.len() > 64 {
        fields.push(("name", "too_long"));
    }
    let mut parent: Option<CategoryRow> = None;
    if req.parent_id.is_none() {
        // V-CAT-044 根分类 attribute_set_id 必填且存在
        match req.attribute_set_id {
            None => fields.push(("attribute_set_id", "required_for_root")),
            Some(sid) => {
                if !attr_set_exists(db, sid).await? {
                    return Err(CatalogError::new(cat_err::ATTRIBUTE_SET_NOT_FOUND));
                }
            }
        }
        // V-CAT-047 根不允许 attr_overrides
        if req
            .attr_overrides
            .as_ref()
            .and_then(|v| v.as_object())
            .is_some_and(|m| !m.is_empty())
        {
            fields.push(("attr_overrides", "root_not_allowed"));
        }
    } else {
        // V-CAT-045 父存在 + level ≤ 3
        parent = find_by_id(db, req.parent_id.unwrap()).await?;
        if parent.is_none() {
            return Err(CatalogError::new(cat_err::CATEGORY_NOT_FOUND));
        }
        if compute_level(parent.as_ref()) > 3 {
            return Err(CatalogError::new(cat_err::CATEGORY_LEVEL_EXCEEDED));
        }
        if let Some(sid) = req.attribute_set_id {
            if !attr_set_exists(db, sid).await? {
                return Err(CatalogError::new(cat_err::ATTRIBUTE_SET_NOT_FOUND));
            }
        }
        validate_attr_overrides(db, req, &mut fields).await?;
    }
    // V-CAT-046 translations
    if let Some(ts) = &req.translations {
        let mut seen: Vec<&str> = vec![];
        for t in ts {
            if t.locale != "es" && t.locale != "fr" {
                fields.push(("translations", "invalid_enum"));
                break;
            }
            if seen.contains(&t.locale.as_str()) {
                fields.push(("translations", "duplicate_locale"));
                break;
            }
            seen.push(&t.locale);
            if let Some(n) = &t.name {
                if n.trim().len() > 64 {
                    fields.push(("translations", "too_long"));
                    break;
                }
            }
        }
    }
    if !fields.is_empty() {
        return Err(CatalogError::field_validation(&fields));
    }
    Ok(parent)
}

async fn attr_set_exists(db: &DatabaseConnection, id: i64) -> Result<bool, CatalogError> {
    let rows = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id FROM attribute_set WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?;
    Ok(!rows.is_empty())
}

/// V-CAT-047 attr_overrides:key ∈ 生效属性集(沿祖先链);value ∈ 三态 {0,1,2}
async fn validate_attr_overrides(
    db: &DatabaseConnection,
    req: &AdminCategoryUpsert,
    fields: &mut Vec<(&'static str, &'static str)>,
) -> Result<(), CatalogError> {
    let Some(overrides) = &req.attr_overrides else {
        return Ok(());
    };
    let Some(obj) = overrides.as_object() else {
        return Ok(());
    };
    if obj.is_empty() {
        return Ok(());
    }
    // 生效属性集:沿祖先链最近 attribute_set_id(含本节点 req.attribute_set_id)
    let all = list_all(db).await?;
    let by_id: HashMap<i64, &CategoryRow> = all.iter().map(|c| (c.id, c)).collect();
    let mut cursor_pid = req.parent_id;
    let mut effective_set = req.attribute_set_id;
    let mut hops = 0;
    while effective_set.is_none() && cursor_pid.is_some() && hops < 4 {
        let Some(p) = cursor_pid.and_then(|pid| by_id.get(&pid)) else {
            break;
        };
        effective_set = p.attribute_set_id;
        cursor_pid = p.parent_id;
        hops += 1;
    }
    let mut allowed: Vec<String> = vec![];
    if let Some(sid) = effective_set {
        let rows = db
            .query_all(Statement::from_sql_and_values(
                DbBackend::MySql,
                "SELECT ad.`key` AS attr_key FROM attribute_set_item asi JOIN attribute_def ad ON ad.id = asi.attribute_id WHERE asi.attribute_set_id = ?",
                [sid.into()],
            ))
            .await
            .map_err(db_err)?;
        for r in rows {
            allowed.push(r.try_get::<String>("", "attr_key").unwrap_or_default());
        }
    }
    for (k, v) in obj {
        if !allowed.iter().any(|a| a == k) {
            fields.push(("attr_overrides", "key_not_in_effective_set"));
            return Ok(());
        }
        let valid = v.as_i64().is_some_and(|n| (0..=2).contains(&n));
        if !valid {
            fields.push(("attr_overrides", "invalid_enum"));
            return Ok(());
        }
    }
    Ok(())
}

/// E-CAT-16 create(TX-CAT-006:INSERT + translations + 审计同事务)
pub async fn admin_create(
    db: &DatabaseConnection,
    req: AdminCategoryUpsert,
    operator: &str,
) -> Result<AdminCategoryNode, CatalogError> {
    let parent = validate_upsert(db, &req).await?;
    let name = req.name.as_deref().map(str::trim).unwrap_or_default().to_string();
    let level = compute_level(parent.as_ref());
    // sort 缺省同层 MAX+1
    let sort = match req.sort {
        Some(s) => s,
        None => {
            let rows = db
                .query_all(Statement::from_sql_and_values(
                    DbBackend::MySql,
                    "SELECT COALESCE(MAX(sort), -1) AS m FROM category WHERE parent_id <=> ?",
                    [req.parent_id.into()],
                ))
                .await
                .map_err(db_err)?;
            rows.first()
                .and_then(|r| r.try_get::<i32>("", "m").ok())
                .unwrap_or(-1)
                + 1
        }
    };
    let overrides: Option<Value> = if req.parent_id.is_none() {
        None
    } else {
        req.attr_overrides.clone()
    };
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO category(name, parent_id, level, attribute_set_id, attr_overrides, sort, created_at, updated_at) VALUES (?,?,?,?,?,?,NOW(3),NOW(3))",
        [
            name.clone().into(),
            req.parent_id.into(),
            level.into(),
            req.attribute_set_id.into(),
            overrides.clone().map(|v| v.to_string()).into(),
            sort.into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    let new_id = tx
        .query_one(Statement::from_string(
            DbBackend::MySql,
            "SELECT LAST_INSERT_ID() AS id".to_string(),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<u64>("", "id").ok())
        .unwrap_or(0);
    replace_translations_tx(&tx, new_id, req.translations.as_deref()).await?;
    audit_record(&tx, operator, "创建分类", &name).await?;
    tx.commit().await.map_err(db_err)?;
    Ok(AdminCategoryNode {
        id: new_id as i64,
        name,
        parent_id: req.parent_id,
        level,
        attribute_set_id: req.attribute_set_id,
        attr_overrides: overrides,
        sort,
        product_count: 0,
        translations: req
            .translations
            .unwrap_or_default()
            .into_iter()
            .map(|t| TranslationDto { locale: t.locale, name: t.name })
            .collect(),
    })
}

/// E-CAT-17 update(TX-CAT-007;parent_id 不可变;translation 整单覆盖)
pub async fn admin_update(
    db: &DatabaseConnection,
    id: i64,
    req: AdminCategoryUpsert,
    operator: &str,
) -> Result<AdminCategoryNode, CatalogError> {
    let existing = find_by_id(db, id)
        .await?
        .ok_or(CatalogError::new(cat_err::CATEGORY_NOT_FOUND))?;
    if req.parent_id != existing.parent_id {
        return Err(CatalogError::field_validation(&[("parent_id", "immutable")]));
    }
    validate_upsert(db, &req).await?;
    let name = req.name.as_deref().map(str::trim).unwrap_or_default().to_string();
    let overrides: Option<Value> = if existing.parent_id.is_none() {
        None
    } else {
        req.attr_overrides.clone()
    };
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE category SET name = ?, attribute_set_id = ?, attr_overrides = ?, sort = COALESCE(?, sort), updated_at = NOW(3) WHERE id = ?",
        [
            name.clone().into(),
            req.attribute_set_id.into(),
            overrides.clone().map(|v| v.to_string()).into(),
            req.sort.into(),
            id.into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    replace_translations_tx(&tx, id as u64, req.translations.as_deref()).await?;
    audit_record(&tx, operator, "编辑分类", &name).await?;
    tx.commit().await.map_err(db_err)?;
    let counts = rollup_counts(&list_all(db).await?, &product_count_all(db).await?);
    Ok(AdminCategoryNode {
        id,
        name,
        parent_id: existing.parent_id,
        level: existing.level,
        attribute_set_id: req.attribute_set_id,
        attr_overrides: overrides,
        sort: req.sort.unwrap_or(existing.sort),
        product_count: counts.get(&id).copied().unwrap_or(0),
        translations: req
            .translations
            .unwrap_or_default()
            .into_iter()
            .map(|t| TranslationDto { locale: t.locale, name: t.name })
            .collect(),
    })
}

/// E-CAT-18 delete(TX-CAT-008;子树商品 guard 409502;子分类 guard 409502 reason=has_children)
pub async fn admin_delete(
    db: &DatabaseConnection,
    id: i64,
    operator: &str,
) -> Result<(), CatalogError> {
    let existing = find_by_id(db, id)
        .await?
        .ok_or(CatalogError::new(cat_err::CATEGORY_NOT_FOUND))?;
    let all = list_all(db).await?;
    let subtree = subtree_ids(&all, id);
    let counts = product_count_all(db).await?;
    let product_count: i64 = subtree.iter().map(|cid| counts.get(cid).copied().unwrap_or(0)).sum();
    if product_count > 0 {
        return Err(CatalogError::new(cat_err::CATEGORY_HAS_PRODUCTS)
            .with_detail("product_count", Value::from(product_count)));
    }
    let has_children = all.iter().any(|c| c.parent_id == Some(id));
    if has_children {
        return Err(CatalogError::new(cat_err::CATEGORY_HAS_PRODUCTS)
            .with_detail("reason", Value::from("has_children")));
    }
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "DELETE FROM category_translation WHERE category_id = ?",
        [id.into()],
    ))
    .await
    .map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "DELETE FROM category WHERE id = ?",
        [id.into()],
    ))
    .await
    .map_err(db_err)?;
    audit_record(&tx, operator, "删除分类", &existing.name).await?;
    tx.commit().await.map_err(db_err)?;
    Ok(())
}

async fn replace_translations_tx(
    tx: &sea_orm::DatabaseTransaction,
    category_id: u64,
    translations: Option<&[TranslationInput]>,
) -> Result<(), CatalogError> {
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "DELETE FROM category_translation WHERE category_id = ?",
        [(category_id as i64).into()],
    ))
    .await
    .map_err(db_err)?;
    if let Some(ts) = translations {
        for t in ts {
            tx.execute(Statement::from_sql_and_values(
                DbBackend::MySql,
                "INSERT INTO category_translation(category_id, locale, name, created_at, updated_at) VALUES (?,?,?,NOW(3),NOW(3))",
                [
                    (category_id as i64).into(),
                    t.locale.clone().into(),
                    t.name.clone().into(),
                ],
            ))
            .await
            .map_err(db_err)?;
        }
    }
    Ok(())
}

/// 审计(事务内;对齐 CatalogAuditRecorder → AuditService.record:
/// operator_id=admin subject 数字,operator_name=NULL,action/target/ip/ua/changes)
async fn audit_record(
    tx: &sea_orm::DatabaseTransaction,
    operator_id: &str,
    action: &str,
    target: &str,
) -> Result<(), CatalogError> {
    let operator_id: Option<i64> = operator_id.parse().ok();
    let res = tx
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO operation_log(operator_id, operator_name, action, target, ip, user_agent, changes, created_at, updated_at) VALUES (?,NULL,?,?,NULL,NULL,NULL,NOW(3),NOW(3))",
            [
                operator_id.into(),
                action.into(),
                target.into(),
            ],
        ))
        .await;
    match res {
        Ok(_) => Ok(()),
        Err(e) => {
            tracing::warn!("[cat] audit best-effort failed:{e}");
            Ok(())
        }
    }
}

// ══════════════════ 单测(树逻辑纯内存) ══════════════════

#[cfg(test)]
mod tests {
    use super::*;

    fn row(id: i64, parent: Option<i64>, level: i8, sort: i32) -> CategoryRow {
        CategoryRow {
            id,
            name: format!("c{id}"),
            parent_id: parent,
            level,
            attribute_set_id: None,
            attr_overrides: None,
            sort,
        }
    }

    #[test]
    fn subtree_ids_bfs_inclusive() {
        let all = vec![row(1, None, 1, 0), row(2, Some(1), 2, 0), row(3, Some(2), 3, 0), row(9, None, 1, 0)];
        assert_eq!(subtree_ids(&all, 1), vec![1, 2, 3]);
        assert_eq!(subtree_ids(&all, 9), vec![9]);
        assert!(subtree_ids(&all, 99).is_empty(), "不存在 → 空集");
    }

    #[test]
    fn compute_level_root_and_child() {
        assert_eq!(compute_level(None), 1);
        let p = row(1, None, 1, 0);
        assert_eq!(compute_level(Some(&p)), 2);
    }

    #[test]
    fn rollup_accumulates_to_ancestors() {
        let all = vec![row(1, None, 1, 0), row(2, Some(1), 2, 0), row(3, Some(2), 3, 0)];
        let mut leaf = HashMap::new();
        leaf.insert(3, 5i64);
        leaf.insert(2, 2i64);
        let out = rollup_counts(&all, &leaf);
        assert_eq!(out[&3], 5);
        assert_eq!(out[&2], 2 + 5);
        assert_eq!(out[&1], 2 + 5);
    }

    #[test]
    fn field_validation_shape_422501() {
        let e = CatalogError::field_validation(&[("name", "required")]);
        assert_eq!(e.code, 422501);
        assert_eq!(e.details.unwrap()["fields"]["name"], "required");
    }
}
