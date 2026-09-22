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
            "SELECT id, section_type, enabled, sort_order, data_json, label FROM home_sections ORDER BY sort_order ASC, id ASC",
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
    let mut out: Vec<Value> = vec![];
    for c in &cols {
        let cid = c.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
        let links = db
            .query_all(Statement::from_string(
                DbBackend::MySql,
                format!("SELECT id, label, url, target, sort_order FROM footer_links WHERE column_id = {} ORDER BY sort_order ASC, id ASC", cid),
            ))
            .await
            .map_err(db_err)?;
        out.push(json!({
            "id": cid,
            "title": c.try_get::<String>("", "title").unwrap_or_default(),
            "sort_order": c.try_get::<i64>("", "sort_order").unwrap_or(0),
            "enabled": c.try_get::<bool>("", "enabled").unwrap_or(true),
            "links": links.iter().map(|l| json!({
                "id": l.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "label": l.try_get::<String>("", "label").unwrap_or_default(),
                "url": l.try_get::<String>("", "url").unwrap_or_default(),
                "target": l.try_get::<String>("", "target").ok(),
                "sort_order": l.try_get::<i64>("", "sort_order").unwrap_or(0),
            })).collect::<Vec<_>>(),
        }));
    }
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
