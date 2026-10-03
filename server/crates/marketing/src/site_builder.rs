//! site_builder 域:announcements/home_sections/footer/navigation 的 admin CRUD + store 端公开读取。

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement, TransactionTrait};
use serde_json::{json, Value};

use crate::service_category::CatalogError;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[sb] db error:{e}");
    CatalogError::new(500601)
}

pub fn not_found() -> CatalogError {
    CatalogError::new(404501)
}

// ══════════ announcements ══════════

pub async fn announcement_list(db: &DatabaseConnection) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, enabled, priority, start_at, end_at, content FROM announcements ORDER BY priority DESC, id ASC",
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "enabled": r.try_get::<bool>("", "enabled").unwrap_or(false),
                "priority": r.try_get::<i64>("", "priority").unwrap_or(0),
                "start_at": r.try_get::<String>("", "start_at").ok(),
                "end_at": r.try_get::<String>("", "end_at").ok(),
                "content": r.try_get::<String>("", "content").unwrap_or_default(),
            })
        })
        .collect())
}

pub async fn announcement_get(db: &DatabaseConnection, id: i64) -> Result<Value, CatalogError> {
    db.query_one(Statement::from_string(
        DbBackend::MySql,
        format!("SELECT id, enabled, priority, start_at, end_at, content FROM announcements WHERE id = {}", id),
    ))
    .await
    .map_err(db_err)?
    .map(|r| {
        json!({
            "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
            "enabled": r.try_get::<bool>("", "enabled").unwrap_or(false),
            "priority": r.try_get::<i64>("", "priority").unwrap_or(0),
            "start_at": r.try_get::<String>("", "start_at").ok(),
            "end_at": r.try_get::<String>("", "end_at").ok(),
            "content": r.try_get::<String>("", "content").unwrap_or_default(),
        })
    })
    .ok_or_else(not_found)
}

#[derive(Debug, serde::Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct AnnouncementUpsert {
    pub content: Option<String>,
    pub priority: Option<i64>,
    pub start_at: Option<String>,
    pub end_at: Option<String>,
    pub enabled: Option<bool>,
}

pub async fn announcement_create(db: &DatabaseConnection, req: &AnnouncementUpsert) -> Result<Value, CatalogError> {
    let content = req.content.as_deref().map(str::trim).filter(|s| !s.is_empty());
    if content.is_none() {
        return Err(CatalogError::field_validation(&[("content", "required")]));
    }
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO announcements(enabled, priority, start_at, end_at, content, content_i18n_json, i18n_json, version, created_at, updated_at) VALUES (?,?,?,?,?,'{}','{}',1,NOW(3),NOW(3))",
        [
            (req.enabled.unwrap_or(false) as i64).into(),
            req.priority.unwrap_or(0).into(),
            req.start_at.clone().into(),
            req.end_at.clone().into(),
            content.unwrap().into(),
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
    tx.commit().await.map_err(db_err)?;
    announcement_get(db, id as i64).await
}

pub async fn announcement_update(db: &DatabaseConnection, id: i64, req: &AnnouncementUpsert) -> Result<Value, CatalogError> {
    let mut sets: Vec<String> = vec!["updated_at = NOW(3)".into(), "version = version + 1".into()];
    if let Some(c) = req.content.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
        sets.push(format!("content = '{}'", c.replace('\'', "''")));
    }
    if let Some(p) = req.priority {
        sets.push(format!("priority = {}", p));
    }
    if let Some(e) = req.enabled {
        sets.push(format!("enabled = {}", e as i64));
    }
    let res = db
        .execute(Statement::from_string(
            DbBackend::MySql,
            format!("UPDATE announcements SET {} WHERE id = {}", sets.join(", "), id),
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(not_found());
    }
    announcement_get(db, id).await
}

pub async fn announcement_toggle(db: &DatabaseConnection, id: i64) -> Result<Value, CatalogError> {
    let res = db
        .execute(Statement::from_string(
            DbBackend::MySql,
            format!("UPDATE announcements SET enabled = NOT enabled, version = version + 1, updated_at = NOW(3) WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(not_found());
    }
    announcement_get(db, id).await
}

pub async fn announcement_delete(db: &DatabaseConnection, id: i64) -> Result<(), CatalogError> {
    let res = db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "DELETE FROM announcements WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(not_found());
    }
    Ok(())
}

// ══════════ home_sections ══════════

pub async fn section_list(db: &DatabaseConnection) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, section_type, enabled, sort_order, data_json, i18n_json, label FROM home_sections ORDER BY sort_order ASC, id ASC",
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            let data: Option<String> = r.try_get("", "data_json").ok();
            json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "section_type": r.try_get::<String>("", "section_type").unwrap_or_default(),
                "enabled": r.try_get::<bool>("", "enabled").unwrap_or(false),
                "sort_order": r.try_get::<i64>("", "sort_order").unwrap_or(0),
                "label": r.try_get::<String>("", "label").ok(),
                "i18n_json": r.try_get::<String>("", "i18n_json").ok(),
                "data": data.and_then(|d| serde_json::from_str::<Value>(&d).ok()),
            })
        })
        .collect())
}

pub async fn section_get(db: &DatabaseConnection, id: i64) -> Result<Value, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT id, section_type, enabled, sort_order, data_json, label FROM home_sections WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?;
    section_list_from_rows(rows).into_iter().next().ok_or_else(not_found)
}

fn section_list_from_rows(rows: Vec<sea_orm::QueryResult>) -> Vec<Value> {
    rows.iter()
        .map(|r| {
            let data: Option<String> = r.try_get("", "data_json").ok();
            json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "section_type": r.try_get::<String>("", "section_type").unwrap_or_default(),
                "enabled": r.try_get::<bool>("", "enabled").unwrap_or(false),
                "sort_order": r.try_get::<i64>("", "sort_order").unwrap_or(0),
                "label": r.try_get::<String>("", "label").ok(),
                "i18n_json": r.try_get::<String>("", "i18n_json").ok(),
                "data": data.and_then(|d| serde_json::from_str::<Value>(&d).ok()),
            })
        })
        .collect()
}

#[derive(Debug, serde::Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct SectionUpsert {
    pub section_type: Option<String>,
    pub label: Option<String>,
    pub data: Option<Value>,
    pub enabled: Option<bool>,
    pub sort_order: Option<i64>,
}

pub async fn section_create(db: &DatabaseConnection, req: &SectionUpsert) -> Result<Value, CatalogError> {
    let Some(stype) = req.section_type.as_deref().map(str::trim).filter(|s| !s.is_empty()) else {
        return Err(CatalogError::field_validation(&[("section_type", "required")]));
    };
    let data_json = req.data.as_ref().map(|d| d.to_string()).unwrap_or_else(|| "{}".into());
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO home_sections(section_type, enabled, sort_order, data_json, label, i18n_json, version, created_at, updated_at) VALUES (?,?,?,?,?,'{}',1,NOW(3),NOW(3))",
        [
            stype.into(),
            (req.enabled.unwrap_or(false) as i64).into(),
            req.sort_order.unwrap_or(0).into(),
            data_json.into(),
            req.label.clone().into(),
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
    tx.commit().await.map_err(db_err)?;
    section_get(db, id as i64).await
}

pub async fn section_update(db: &DatabaseConnection, id: i64, req: &SectionUpsert) -> Result<Value, CatalogError> {
    let mut sets: Vec<String> = vec!["updated_at = NOW(3)".into(), "version = version + 1".into()];
    if let Some(d) = &req.data {
        sets.push(format!("data_json = '{}'", d.to_string().replace('\'', "''")));
    }
    if let Some(l) = &req.label {
        sets.push(format!("label = '{}'", l.replace('\'', "''")));
    }
    if let Some(e) = req.enabled {
        sets.push(format!("enabled = {}", e as i64));
    }
    if let Some(s) = req.sort_order {
        sets.push(format!("sort_order = {}", s));
    }
    let res = db
        .execute(Statement::from_string(
            DbBackend::MySql,
            format!("UPDATE home_sections SET {} WHERE id = {}", sets.join(", "), id),
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(not_found());
    }
    section_get(db, id).await
}

pub async fn section_toggle(db: &DatabaseConnection, id: i64) -> Result<Value, CatalogError> {
    let res = db
        .execute(Statement::from_string(
            DbBackend::MySql,
            format!("UPDATE home_sections SET enabled = NOT enabled, version = version + 1, updated_at = NOW(3) WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(not_found());
    }
    section_get(db, id).await
}

/// sort 批量(ids 顺序即 sort_order)
pub async fn section_sort(db: &DatabaseConnection, ids: &[i64]) -> Result<(), CatalogError> {
    let tx = db.begin().await.map_err(db_err)?;
    for (i, id) in ids.iter().enumerate() {
        tx.execute(Statement::from_string(
            DbBackend::MySql,
            format!("UPDATE home_sections SET sort_order = {}, version = version + 1, updated_at = NOW(3) WHERE id = {}", i, id),
        ))
        .await
        .map_err(db_err)?;
    }
    tx.commit().await.map_err(db_err)?;
    Ok(())
}

pub async fn section_delete(db: &DatabaseConnection, id: i64) -> Result<(), CatalogError> {
    let res = db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "DELETE FROM home_sections WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(not_found());
    }
    Ok(())
}

// ══════════ footer ══════════

pub async fn footer_get(db: &DatabaseConnection) -> Result<Value, CatalogError> {
    let cols = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, title, sort_order, enabled FROM footer_columns ORDER BY sort_order ASC, id ASC",
        ))
        .await
        .map_err(db_err)?;
    // perf-review #6:逐列查链接 → 单条 IN 批查
    let ids: Vec<String> = cols
        .iter()
        .filter_map(|c| c.try_get::<u64>("", "id").ok().map(|v| v.to_string()))
        .collect();
    let mut links_by: std::collections::HashMap<i64, Vec<Value>> = std::collections::HashMap::new();
    if !ids.is_empty() {
        let list = ids.join(",");
        let links = db
            .query_all(Statement::from_string(
                DbBackend::MySql,
                format!(
                    "SELECT id, column_id, label, url, target, sort_order FROM footer_links                      WHERE column_id IN ({list}) ORDER BY sort_order ASC, id ASC"
                ),
            ))
            .await
            .map_err(db_err)?;
        for l in links.iter() {
            let cid = l.try_get::<u64>("", "column_id").ok().map(|v| v as i64).unwrap_or(0);
            links_by.entry(cid).or_default().push(json!({
                "id": l.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "label": l.try_get::<String>("", "label").unwrap_or_default(),
                "url": l.try_get::<String>("", "url").unwrap_or_default(),
                "target": l.try_get::<String>("", "target").ok(),
                "sort_order": l.try_get::<i64>("", "sort_order").unwrap_or(0),
            }));
        }
    }
    let out: Vec<Value> = cols
        .iter()
        .map(|c| {
            let cid = c.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            json!({
                "id": cid,
                "title": c.try_get::<String>("", "title").unwrap_or_default(),
                "sort_order": c.try_get::<i64>("", "sort_order").unwrap_or(0),
                "enabled": c.try_get::<bool>("", "enabled").unwrap_or(true),
                "links": links_by.remove(&cid).unwrap_or_default(),
            })
        })
        .collect();
    Ok(json!({ "columns": out }))
}

#[derive(Debug, serde::Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct FooterColumnIn {
    pub title: Option<String>,
    pub links: Option<Vec<FooterLinkIn>>,
}

#[derive(Debug, serde::Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct FooterLinkIn {
    pub label: Option<String>,
    pub url: Option<String>,
    pub target: Option<String>,
}

/// footer 全量替换(删旧插新,事务)
pub async fn footer_put(db: &DatabaseConnection, columns: &[FooterColumnIn]) -> Result<Value, CatalogError> {
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_string(
        DbBackend::MySql,
        "DELETE FROM footer_links".to_string(),
    ))
    .await
    .map_err(db_err)?;
    tx.execute(Statement::from_string(
        DbBackend::MySql,
        "DELETE FROM footer_columns".to_string(),
    ))
    .await
    .map_err(db_err)?;
    for (i, col) in columns.iter().enumerate() {
        let title = col.title.as_deref().unwrap_or("").trim().to_string();
        tx.execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO footer_columns(title, i18n_json, sort_order, enabled, version, created_at, updated_at) VALUES (?,'{}',?,1,1,NOW(3),NOW(3))",
            [title.into(), (i as i64).into()],
        ))
        .await
        .map_err(db_err)?;
        let cid = tx
            .query_one(Statement::from_string(
                DbBackend::MySql,
                "SELECT LAST_INSERT_ID() AS id".to_string(),
            ))
            .await
            .map_err(db_err)?
            .and_then(|r| r.try_get::<u64>("", "id").ok())
            .unwrap_or(0);
        for (j, link) in col.links.iter().flatten().enumerate() {
            tx.execute(Statement::from_sql_and_values(
                DbBackend::MySql,
                "INSERT INTO footer_links(column_id, label, url, target, i18n_json, sort_order, version, created_at, updated_at) VALUES (?,?,?,?,'{}',?,1,NOW(3),NOW(3))",
                [
                    (cid as i64).into(),
                    link.label.clone().unwrap_or_default().into(),
                    link.url.clone().unwrap_or_default().into(),
                    link.target.clone().unwrap_or_default().into(),
                    (j as i64).into(),
                ],
            ))
            .await
            .map_err(db_err)?;
        }
    }
    tx.commit().await.map_err(db_err)?;
    footer_get(db).await
}

// ══════════ navigation ══════════

pub async fn navigation_get(db: &DatabaseConnection) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, parent_id, label, url, target, sort_order, enabled FROM navigation_items ORDER BY sort_order ASC, id ASC",
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "parent_id": r.try_get::<i64>("", "parent_id").ok(),
                "label": r.try_get::<String>("", "label").unwrap_or_default(),
                "url": r.try_get::<String>("", "url").unwrap_or_default(),
                "target": r.try_get::<String>("", "target").ok(),
                "sort_order": r.try_get::<i64>("", "sort_order").unwrap_or(0),
                "enabled": r.try_get::<bool>("", "enabled").unwrap_or(true),
            })
        })
        .collect())
}

/// navigation 全量替换
pub async fn navigation_put(db: &DatabaseConnection, items: &[Value]) -> Result<(), CatalogError> {
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_string(
        DbBackend::MySql,
        "DELETE FROM navigation_items".to_string(),
    ))
    .await
    .map_err(db_err)?;
    for (i, it) in items.iter().enumerate() {
        tx.execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO navigation_items(label, url, target, i18n_json, sort_order, enabled, version, created_at, updated_at) VALUES (?,?,?,'{}',?,?,1,NOW(3),NOW(3))",
            [
                it["label"].as_str().unwrap_or_default().into(),
                it["url"].as_str().unwrap_or_default().into(),
                it["target"].as_str().unwrap_or("_self").into(),
                it["sort_order"].as_i64().unwrap_or(i as i64).into(),
                (it["enabled"].as_bool().unwrap_or(true) as i64).into(),
            ],
        ))
        .await
        .map_err(db_err)?;
    }
    tx.commit().await.map_err(db_err)?;
    Ok(())
}

/// store 端公开读取(首页装修 + 导航 + footer + announcements)
pub async fn store_layout(db: &DatabaseConnection) -> Result<Value, CatalogError> {
    let sections = section_list(db).await?;
    let nav = navigation_get(db).await?;
    let footer = footer_get(db).await?;
    let mut anns = announcement_list(db).await?;
    anns.retain(|a| a["enabled"].as_bool().unwrap_or(false));
    Ok(json!({
        "home_sections": sections.into_iter().filter(|s| s["enabled"].as_bool().unwrap_or(false)).collect::<Vec<_>>(),
        "navigation": nav,
        "footer": footer,
        "announcements": anns,
    }))
}

/// 导航引用目标下拉(Java NavigationService.linkOptions;type=LinkType.key,3..9 为引用型,1=custom/2=page 无引用)。
/// 返回 {id,label,meta};每类上限 50,keyword 过滤 label 列。
pub async fn link_options(db: &DatabaseConnection, link_type: i64, keyword: &str) -> Result<Vec<Value>, CatalogError> {
    let kw = keyword.trim().to_lowercase();
    let like = format!("%{}%", kw);
    let sql: String = match link_type {
        3 => "SELECT id, name AS label, CONCAT('L', level) AS meta FROM category WHERE ? = '' OR LOWER(name) LIKE ? ORDER BY level, id LIMIT 50".to_string(),
        4 => "SELECT id, name AS label, NULL AS meta FROM collection WHERE status = 1 AND (? = '' OR LOWER(name) LIKE ?) ORDER BY id LIMIT 50".to_string(),
        5 => "SELECT id, name AS label, slug AS meta FROM product WHERE status = 2 AND (? = '' OR LOWER(name) LIKE ?) ORDER BY id LIMIT 50".to_string(),
        6 => "SELECT id, title AS label, slug AS meta FROM blog_post WHERE status = 2 AND (? = '' OR LOWER(title) LIKE ?) ORDER BY id LIMIT 50".to_string(),
        7 => "SELECT id, couple AS label, title AS meta FROM real_wedding WHERE status = 2 AND (? = '' OR LOWER(couple) LIKE ?) ORDER BY id LIMIT 50".to_string(),
        8 => "SELECT id, title AS label, theme AS meta FROM lookbook WHERE status = 2 AND (? = '' OR LOWER(title) LIKE ?) ORDER BY id LIMIT 50".to_string(),
        9 => "SELECT id, title AS label, phase AS meta FROM guide WHERE status = 2 AND (? = '' OR LOWER(title) LIKE ?) ORDER BY id LIMIT 50".to_string(),
        _ => return Ok(Vec::new()),
    };
    let rows = db
        .query_all(Statement::from_sql_and_values(DbBackend::MySql, sql, [kw.into(), like.into()]))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "label": r.try_get::<String>("", "label").unwrap_or_default(),
                "meta": r.try_get::<String>("", "meta").ok(),
            })
        })
        .collect())
}
// ══════════ store content 四件套(Java StoreContentService 移植,静态 i18n 期) ══════════

fn resolve_i18n(i18n_json: Option<&str>, locale: &str) -> Value {
    let Some(raw) = i18n_json else { return json!({}) };
    let node: Value = match serde_json::from_str(raw) {
        Ok(v) => v,
        Err(_) => return json!({}),
    };
    let pick = |loc: &str| node.get(loc).cloned().filter(|v| v.is_object());
    pick(locale).or_else(|| pick("en")).unwrap_or_else(|| json!({}))
}

fn resolve_i18n_field(i18n_json: Option<&str>, locale: &str, field: &str, fallback: &str) -> String {
    let Some(raw) = i18n_json else { return fallback.to_string() };
    let node: Value = match serde_json::from_str(raw) {
        Ok(v) => v,
        Err(_) => return fallback.to_string(),
    };
    for loc in [locale, "en"] {
        if let Some(v) = node.get(loc).and_then(|l| l.get(field)).and_then(|f| f.as_str()) {
            return v.to_string();
        }
    }
    fallback.to_string()
}

fn read_cfg(data_json: Option<&str>) -> Value {
    data_json
        .and_then(|s| serde_json::from_str(s).ok())
        .unwrap_or(Value::Null)
}

fn cfg_int(cfg: &Value, key: &str, default: i64, min: i64, max: i64) -> i64 {
    let v = cfg.get(key).and_then(|v| v.as_i64()).unwrap_or(default);
    if (min..=max).contains(&v) { v } else { default }
}

fn cfg_ids(cfg: &Value, key: &str) -> Vec<i64> {
    cfg.get(key)
        .and_then(|v| v.as_array())
        .map(|a| a.iter().filter_map(|x| x.as_i64()).filter(|v| *v > 0).collect())
        .unwrap_or_default()
}

const PRODUCT_CARD_COLS: &str = "p.id, p.slug, p.name, p.price, p.is_new, p.is_best,  (SELECT i.url FROM product_image i WHERE i.product_id = p.id AND i.kind = 1 ORDER BY i.sort ASC, i.id ASC LIMIT 1) AS image_url";

fn card_json(r: &sea_orm::QueryResult) -> Value {
    json!({
        "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "slug": r.try_get::<String>("", "slug").unwrap_or_default(),
        "name": r.try_get::<String>("", "name").unwrap_or_default(),
        "price": r.try_get::<f64>("", "price").unwrap_or(0.0),
        "image_url": r.try_get::<String>("", "image_url").ok(),
        "is_new": r.try_get::<bool>("", "is_new").unwrap_or(false),
        "is_best": r.try_get::<bool>("", "is_best").unwrap_or(false),
    })
}

/// hero/featured_banner:banner.rs store_list(状态=已发布+投放时间窗+i18n)
async fn derive_banner_section(db: &DatabaseConnection, position: i64, locale: &str) -> Value {
    match crate::banner::store_list(db, Some(position), locale).await {
        Ok(items) if !items.is_empty() => {
            let mut hero = json!({ "banners": items });
            // 兼容旧消费端:首张扁平字段
            if let Some(first) = hero["banners"].as_array().and_then(|a| a.first()).cloned() {
                hero["title"] = first.get("title").cloned().unwrap_or(Value::Null);
                hero["subtitle"] = first.get("subtitle").cloned().unwrap_or(Value::Null);
                hero["cta_text"] = first.get("cta_text").cloned().unwrap_or(Value::Null);
                hero["cta_link"] = first.get("cta_link").cloned().unwrap_or(Value::Null);
                hero["cta_text_secondary"] = first.get("cta_text_secondary").cloned().unwrap_or(Value::Null);
                hero["cta_link_secondary"] = first.get("cta_link_secondary").cloned().unwrap_or(Value::Null);
                hero["image_url"] = first.get("image_url").cloned().unwrap_or(Value::Null);
            }
            hero
        }
        _ => json!({}),
    }
}

/// theme_cards:Theme 分组下的启用集合;封面=集合内排序最前的已上架商品主图;product_count published 口径
async fn derive_theme_cards(db: &DatabaseConnection, cfg: &Value, i18n_json: Option<&str>, locale: &str) -> Value {
    let mut data = resolve_i18n(i18n_json, locale);
    let legacy_limit = cfg_int(cfg, "limit", 6, 1, 8);
    let count = cfg_int(cfg, "count", legacy_limit, 1, 8);
    let mode = cfg.get("mode").and_then(|v| v.as_str()).unwrap_or("auto").to_string();
    let ids_manual = cfg_ids(cfg, "collection_ids");
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT c.id, c.name FROM collection c JOIN collection_group g ON g.id = c.collection_group_id              WHERE LOWER(g.name) LIKE '%theme%' AND c.status = 1 ORDER BY c.id",
        ))
        .await;
    let mut cards: Vec<Value> = Vec::new();
    if let Ok(rows) = rows {
        let mut selected: Vec<(i64, String)> = rows
            .iter()
            .filter_map(|r| {
                let id = r.try_get::<u64>("", "id").ok().map(|v| v as i64)?;
                let name = r.try_get::<String>("", "name").ok()?;
                Some((id, name))
            })
            .collect();
        if mode == "manual" {
            selected.retain(|(id, _)| ids_manual.contains(id));
            selected.sort_by_key(|(id, _)| ids_manual.iter().position(|m| m == id).unwrap_or(0));
        } else {
            selected.truncate(count as usize);
        }
        // perf-review #1:逐集合 2 查 → 2 条批查(GROUP BY 计数 + 窗口函数取每集合首图)
        let id_list = selected.iter().map(|(id, _)| id.to_string()).collect::<Vec<_>>().join(",");
        let mut counts: std::collections::HashMap<i64, i64> = std::collections::HashMap::new();
        let mut covers: std::collections::HashMap<i64, String> = std::collections::HashMap::new();
        if !id_list.is_empty() {
            let count_rows = db
                .query_all(Statement::from_string(
                    DbBackend::MySql,
                    format!(
                        "SELECT pc.collection_id AS cid, COUNT(*) AS n FROM product_collection pc                          JOIN product p ON p.id = pc.product_id AND p.status = 2                          WHERE pc.collection_id IN ({id_list}) GROUP BY pc.collection_id"
                    ),
                ))
                .await
                .unwrap_or_default();
            for r in count_rows.iter() {
                if let (Some(cid), Some(n)) = (
                    r.try_get::<u64>("", "cid").ok().map(|v| v as i64),
                    r.try_get::<u64>("", "n").ok().map(|v| v as i64),
                ) {
                    counts.insert(cid, n);
                }
            }
            let cover_rows = db
                .query_all(Statement::from_string(
                    DbBackend::MySql,
                    format!(
                        "SELECT collection_id, url FROM (                            SELECT pc.collection_id AS collection_id, i.url AS url,                                    ROW_NUMBER() OVER (PARTITION BY pc.collection_id ORDER BY pc.sort ASC, i.sort ASC, i.id ASC) AS rn                             FROM product_collection pc                             JOIN product_image i ON i.product_id = pc.product_id AND i.kind = 1                             JOIN product p ON p.id = pc.product_id AND p.status = 2                             WHERE pc.collection_id IN ({id_list})                          ) t WHERE rn = 1"
                    ),
                ))
                .await
                .unwrap_or_default();
            for r in cover_rows.iter() {
                if let (Some(cid), Some(url)) = (
                    r.try_get::<u64>("", "collection_id").ok().map(|v| v as i64),
                    r.try_get::<String>("", "url").ok(),
                ) {
                    covers.insert(cid, url);
                }
            }
        }
        for (id, name) in selected {
            cards.push(json!({
                "id": id,
                "name": name,
                "product_count": counts.get(&id).copied().unwrap_or(0),
                "image_url": covers.get(&id),
            }));
        }
    }
    let _ = locale;
    data["cards"] = Value::Array(cards);
    data
}

/// product_rail:new_arrival(最新)/best_seller(销量聚合)/recommend(人工 ids)/category(分类过滤)
async fn derive_product_rail(db: &DatabaseConnection, cfg: &Value, i18n_json: Option<&str>, locale: &str) -> Value {
    let mut data = resolve_i18n(i18n_json, locale);
    let limit = cfg_int(cfg, "limit", 8, 1, 12);
    let source = cfg.get("source").and_then(|v| v.as_str()).unwrap_or("new_arrival").to_string();
    let category_id = cfg.get("category_id").and_then(|v| v.as_i64());
    let raw_sort = cfg.get("sort").and_then(|v| v.as_str()).unwrap_or("newest");
    let sort = match raw_sort {
        "new" => "newest",
        "best" => "recommended",
        s @ ("newest" | "price_asc" | "price_desc" | "recommended") => s,
        _ => "newest",
    };
    let order = match sort {
        "price_asc" => "p.price ASC, p.id DESC",
        "price_desc" => "p.price DESC, p.id DESC",
        "recommended" => "p.is_best DESC, p.id DESC",
        _ => "p.id DESC",
    };
    let rows = match source.as_str() {
        "recommend" => {
            let ids = cfg_ids(cfg, "product_ids");
            if ids.is_empty() {
                Vec::new()
            } else {
                let list = ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
                db.query_all(Statement::from_string(
                    DbBackend::MySql,
                    format!(
                        "SELECT {PRODUCT_CARD_COLS} FROM product p WHERE p.status = 2 AND p.id IN ({list})"
                    ),
                ))
                .await
                .unwrap_or_default()
            }
        }
        "category" => match category_id {
            None => Vec::new(),
            Some(cid) => {
                db.query_all(Statement::from_sql_and_values(
                    DbBackend::MySql,
                    format!(
                        "SELECT {PRODUCT_CARD_COLS} FROM product p WHERE p.status = 2 AND p.category_id = ? ORDER BY {order} LIMIT {limit}"
                    ),
                    [cid.into()],
                ))
                .await
                .unwrap_or_default()
            }
        },
        "best_seller" => {
            db.query_all(Statement::from_string(
                DbBackend::MySql,
                format!(
                    "SELECT {PRODUCT_CARD_COLS} FROM product p WHERE p.status = 2 ORDER BY p.sales_30d DESC, p.id DESC LIMIT {limit}"
                ),
            ))
            .await
            .unwrap_or_default()
        }
        _ => {
            db.query_all(Statement::from_string(
                DbBackend::MySql,
                format!("SELECT {PRODUCT_CARD_COLS} FROM product p WHERE p.status = 2 ORDER BY {order} LIMIT {limit}"),
            ))
            .await
            .unwrap_or_default()
        }
    };
    let mut products: Vec<Value> = rows
        .iter()
        .map(card_json)
        .collect();
    if source == "recommend" {
        let ids = cfg_ids(cfg, "product_ids");
        products.sort_by_key(|p| {
            let id = p["id"].as_i64().unwrap_or(0);
            ids.iter().position(|m| *m == id).unwrap_or(usize::MAX)
        });
        products.truncate(limit as usize);
    }
    data["products"] = Value::Array(products);
    data
}

/// editorial_feature:真实婚礼 wedding_date DESC(唯一可靠语义,不模拟人气/随机)
async fn derive_editorial_feature(db: &DatabaseConnection, cfg: &Value, i18n_json: Option<&str>, locale: &str) -> Value {
    let mut data = resolve_i18n(i18n_json, locale);
    let limit = cfg_int(cfg, "limit", 3, 1, 6);
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT id, couple, location, theme, wedding_date, title, cover FROM real_wedding                  WHERE status = 2 ORDER BY wedding_date DESC, id DESC LIMIT {limit}"
            ),
        ))
        .await
        .unwrap_or_default();
    let stories: Vec<Value> = rows
        .iter()
        .map(|r| {
            json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "couple": r.try_get::<String>("", "couple").unwrap_or_default(),
                "location": r.try_get::<String>("", "location").ok(),
                "theme": r.try_get::<String>("", "theme").ok(),
                "wedding_date": r.try_get::<String>("", "wedding_date").ok(),
                "cover": r.try_get::<String>("", "cover").ok(),
                "title": r.try_get::<String>("", "title").ok(),
            })
        })
        .collect();
    data["stories"] = Value::Array(stories);
    data
}

/// NavPageKey:page_key → 站内路径(Java NavPageKey 全 21 键)
fn page_key_path(page_key: &str) -> Option<String> {
    Some(
        match page_key {
            "home" => "/",
            "products" => "/products",
            "wedding-dresses" => "/wedding-dresses",
            "special-occasion" => "/special-occasion",
            "accessories" => "/accessories",
            "outdoor-weddings" => "/outdoor-weddings",
            "real-weddings" => "/real-weddings",
            "inspiration" => "/inspiration",
            "blog" => "/blog",
            "wedding-guides" => "/wedding-guides",
            "showroom" => "/showroom",
            "about" => "/about",
            "contact" => "/contact",
            "faq" => "/faq",
            "search" => "/search",
            "cart" => "/cart",
            "account-login" => "/account/login",
            "account-orders" => "/account/orders",
            "account-wishlist" => "/account/wishlist",
            "track-order" => "/track-order",
            _ => return None,
        }
        .to_string(),
    )
}

/// 导航 URL 解析(Java resolveNavUrl):引用目标不存在/未发布 → None → 该项隐藏防死链
async fn resolve_nav_url(db: &DatabaseConnection, link_type: i64, ref_id: Option<i64>, url: Option<&str>, page_key: Option<&str>) -> Option<String> {
    match link_type {
        1 => {
            let u = url?.trim().to_string();
            if u.is_empty() { None } else { Some(u) }
        }
        2 => page_key_path(page_key?),
        3 => {
            let rid = ref_id?;
            let name: String = db
                .query_one(Statement::from_string(DbBackend::MySql, format!("SELECT name FROM category WHERE id = {rid}")))
                .await
                .ok()?
                .and_then(|r| r.try_get::<String>("", "name").ok())?;
            Some(format!("/products?cat={}", urlencoding::encode(&name)))
        }
        4 => {
            let rid = ref_id?;
            let ok: Option<u64> = db
                .query_one(Statement::from_string(DbBackend::MySql, format!("SELECT id FROM collection WHERE id = {rid} AND status = 1")))
                .await
                .ok()?
                .and_then(|r| r.try_get::<u64>("", "id").ok());
            ok.map(|_| format!("/products?collection={rid}"))
        }
        5 => {
            let rid = ref_id?;
            let slug: Option<String> = db
                .query_one(Statement::from_string(DbBackend::MySql, format!("SELECT slug FROM product WHERE id = {rid} AND status = 2")))
                .await
                .ok()?
                .and_then(|r| r.try_get::<String>("", "slug").ok());
            slug.map(|s| format!("/product/{s}"))
        }
        6 => {
            let rid = ref_id?;
            let slug: Option<String> = db
                .query_one(Statement::from_string(DbBackend::MySql, format!("SELECT slug FROM blog_post WHERE id = {rid} AND status = 2")))
                .await
                .ok()?
                .and_then(|r| r.try_get::<String>("", "slug").ok());
            slug.map(|s| format!("/blog/{s}"))
        }
        7 => {
            let rid = ref_id?;
            let ok: Option<u64> = db
                .query_one(Statement::from_string(DbBackend::MySql, format!("SELECT id FROM real_wedding WHERE id = {rid} AND status = 2")))
                .await
                .ok()?
                .and_then(|r| r.try_get::<u64>("", "id").ok());
            ok.map(|_| format!("/real-weddings/{rid}"))
        }
        8 => {
            let rid = ref_id?;
            let ok: Option<u64> = db
                .query_one(Statement::from_string(DbBackend::MySql, format!("SELECT id FROM lookbook WHERE id = {rid} AND status = 2")))
                .await
                .ok()?
                .and_then(|r| r.try_get::<u64>("", "id").ok());
            ok.map(|_| "/inspiration".to_string())
        }
        9 => {
            let rid = ref_id?;
            let ok: Option<u64> = db
                .query_one(Statement::from_string(DbBackend::MySql, format!("SELECT id FROM guide WHERE id = {rid} AND status = 2")))
                .await
                .ok()?
                .and_then(|r| r.try_get::<u64>("", "id").ok());
            ok.map(|_| "/wedding-guides".to_string())
        }
        _ => None,
    }
}

/// GET /api/store/content/home → { sections: [{sectionType, data}] }
pub async fn store_home(db: &DatabaseConnection, locale: &str) -> Result<Value, CatalogError> {
    let sections = section_list(db).await?;
    let mut out: Vec<Value> = Vec::new();
    for s in sections.iter().filter(|s| s["enabled"].as_bool().unwrap_or(false)) {
        let stype = s["section_type"].as_str().unwrap_or("custom").to_string();
        let i18n_json: Option<String> = s.get("i18n_json").and_then(|v| v.as_str()).map(String::from);
        // section_list 已带 data(解析后的 data_json)与 i18n_json,无需回查(perf-review #1)
        let cfg = s.get("data").cloned().unwrap_or(Value::Null);
        let mut data = match stype.as_str() {
            "hero" => derive_banner_section(db, 1, locale).await,
            "featured_banner" => derive_banner_section(db, 2, locale).await,
            "newsletter" => resolve_i18n(i18n_json.as_deref(), locale),
            "theme_cards" => derive_theme_cards(db, &cfg, i18n_json.as_deref(), locale).await,
            "product_rail" => derive_product_rail(db, &cfg, i18n_json.as_deref(), locale).await,
            "editorial_feature" => derive_editorial_feature(db, &cfg, i18n_json.as_deref(), locale).await,
            _ => {
                // custom/default:cfg 即 section_list 解析好的 data_json
                let mut d = match cfg {
                    v @ Value::Object(_) => v,
                    _ => json!({}),
                };
                if let Some(obj) = d.as_object_mut() {
                    for (k, v) in resolve_i18n(i18n_json.as_deref(), locale)
                        .as_object()
                        .map(|m| m.clone())
                        .unwrap_or_default()
                    {
                        obj.insert(k, v);
                    }
                }
                d
            }
        };
        if (stype == "hero" || stype == "featured_banner") && data.as_object().map(|m| m.is_empty()).unwrap_or(true) {
            continue; // Java DG-01:空派生跳过该分区
        }
        out.push(json!({ "sectionType": stype, "data": data }));
    }
    Ok(json!({ "sections": out }))
}

/// GET /api/store/content/navigation → { items: [{id,parentId,label,url,target,linkType,sortOrder,megaMenu}] }
pub async fn store_navigation(db: &DatabaseConnection, locale: &str) -> Result<Value, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, parent_id, label, url, target, link_type, ref_id, page_key, mega_menu_json, i18n_json, sort_order FROM navigation_items WHERE enabled = 1 ORDER BY sort_order ASC, id ASC",
        ))
        .await
        .map_err(db_err)?;
    let mut items: Vec<Value> = Vec::new();
    for r in rows.iter() {
        let link_type = r.try_get::<i64>("", "link_type").unwrap_or(1);
        let ref_id = r.try_get::<u64>("", "ref_id").ok().map(|v| v as i64);
        let page_key = r.try_get::<String>("", "page_key").ok();
        let raw_url = r.try_get::<String>("", "url").ok();
        let Some(resolved) = resolve_nav_url(db, link_type, ref_id, raw_url.as_deref(), page_key.as_deref()).await else {
            continue; // 死链隐藏
        };
        let label_raw = r.try_get::<String>("", "label").unwrap_or_default();
        let i18n_json = r.try_get::<String>("", "i18n_json").ok();
        let label = resolve_i18n_field(i18n_json.as_deref(), locale, "label", &label_raw);
        let mega_menu = r
            .try_get::<String>("", "mega_menu_json")
            .ok()
            .and_then(|s| serde_json::from_str::<Value>(&s).ok());
        items.push(json!({
            "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
            "parentId": r.try_get::<u64>("", "parent_id").ok().map(|v| v as i64),
            "label": label,
            "url": resolved,
            "target": r.try_get::<String>("", "target").unwrap_or_else(|_| "self".to_string()),
            "linkType": link_type,
            "sortOrder": r.try_get::<i64>("", "sort_order").unwrap_or(0),
            "megaMenu": mega_menu,
        }));
    }
    Ok(json!({ "items": items }))
}

/// GET /api/store/content/footer → { columns: [{id,title,sortOrder,links:[{id,label,url,target,sortOrder}]}] }
pub async fn store_footer(db: &DatabaseConnection, locale: &str) -> Result<Value, CatalogError> {
    let columns = footer_get(db).await?;
    let cols = columns.get("columns").cloned().unwrap_or_else(|| json!([]));
    let mut arr = cols.as_array().cloned().unwrap_or_default();
    for c in arr.iter_mut() {
        if let Some(title) = c.get("title").and_then(|v| v.as_str()).map(String::from) {
            c["title"] = json!(resolve_i18n_field(None, locale, "title", &title));
        }
    }
    Ok(json!({ "columns": arr }))
}

/// GET /api/store/content/announcements → { announcements: [{id,priority,content,startAt,endAt}] }(时间窗内)
pub async fn store_announcements(db: &DatabaseConnection, locale: &str) -> Result<Value, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, priority, content, start_at, end_at FROM announcements              WHERE enabled = 1 AND (start_at IS NULL OR start_at <= NOW(3)) AND (end_at IS NULL OR end_at >= NOW(3))              ORDER BY priority DESC, id ASC",
        ))
        .await
        .map_err(db_err)?;
    let list: Vec<Value> = rows
        .iter()
        .map(|r| {
            let content = r.try_get::<String>("", "content").unwrap_or_default();
            json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "priority": r.try_get::<i64>("", "priority").unwrap_or(0),
                "content": resolve_i18n_field(None, locale, "content", &content),
                "startAt": r.try_get::<String>("", "start_at").ok(),
                "endAt": r.try_get::<String>("", "end_at").ok(),
            })
        })
        .collect();
    Ok(json!({ "announcements": list }))
}
