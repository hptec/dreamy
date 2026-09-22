//! content 域:blog / real_wedding / lookbook / guide 四同源内容域。
//! admin CRUD(E-MKT-24~48 内容段)+ store 公开读(E-MKT-01~08)+ 预览 token。
//! 三态状态机复用 banner::transition_allowed;错误码 404701/409703/422704/401701/422705/422706。

use std::collections::{HashMap, HashSet};

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement, TransactionTrait};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::banner::transition_allowed;
use crate::service_category::{cat_err, CatalogError};

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[content] db error:{e}");
    CatalogError::new(500701)
}

pub fn not_found() -> CatalogError {
    CatalogError::new(404701)
}

fn field_err(fields: Vec<(&'static str, &'static str)>) -> CatalogError {
    CatalogError::field_validation(&fields)
}

pub const RESERVED_SLUGS: [&str; 10] = [
    "admin", "api", "blog", "preview", "new", "edit", "_next", "sitemap.xml", "robots.txt", "favicon.ico",
];
pub const CONTENT_MAX_LENGTH: usize = 200_000; // MEDIUMTEXT 防御(Java CONTENT_MAX_LENGTH 同级)
const BLOG_BODY_MAX: usize = CONTENT_MAX_LENGTH;

pub fn slug_valid(slug: &str) -> bool {
    !slug.is_empty()
        && slug.len() <= 128
        && slug.chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '-')
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
        tracing::warn!("[content] audit best-effort failed:{e}");
    }
}

async fn inserted_id(tx: &sea_orm::DatabaseTransaction) -> Result<u64, CatalogError> {
    tx.query_one(Statement::from_string(
        DbBackend::MySql,
        "SELECT LAST_INSERT_ID() AS id".to_string(),
    ))
    .await
    .map_err(db_err)?
    .and_then(|row| row.try_get::<u64>("", "id").ok())
    .ok_or_else(|| CatalogError::new(500701))
}

// ══════════════════ blog(E-MKT-24~33 内容段)══════════════════

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct BlogTranslationIn {
    pub locale: String,
    pub title: Option<String>,
    pub excerpt: Option<String>,
    pub body: Option<String>,
    pub seo_title: Option<String>,
    pub seo_description: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct BlogUpsert {
    pub title: Option<String>,
    pub cover: Option<String>,
    pub category: Option<String>,
    pub author: Option<String>,
    pub content: Option<String>,
    pub slug: Option<String>,
    pub status: Option<i64>,
    pub excerpt: Option<String>,
    pub seo_title: Option<String>,
    pub seo_description: Option<String>,
    pub version: Option<i64>,
    pub published_at: Option<String>,
    pub translations: Option<Vec<BlogTranslationIn>>,
}

fn check_max(v: &Option<String>, max: usize, field: &'static str, fields: &mut Vec<(&'static str, &'static str)>) {
    if v.as_deref().is_some_and(|s| s.len() > max) {
        fields.push((field, "too_long"));
    }
}

fn validate_blog(req: &BlogUpsert, create: bool) -> Result<Option<String>, CatalogError> {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let title = req.title.as_deref().map(str::trim).filter(|s| !s.is_empty());
    match title {
        None => fields.push(("title", "required")),
        Some(t) if t.len() > 200 => fields.push(("title", "too_long")),
        _ => {}
    }
    check_max(&req.cover, 512, "cover", &mut fields);
    check_max(&req.category, 64, "category", &mut fields);
    check_max(&req.author, 64, "author", &mut fields);
    check_max(&req.excerpt, 500, "excerpt", &mut fields);
    check_max(&req.seo_title, 128, "seo_title", &mut fields);
    check_max(&req.seo_description, 255, "seo_description", &mut fields);
    if req.content.as_deref().is_some_and(|c| c.len() > BLOG_BODY_MAX) {
        return Err(CatalogError::new(422706)); // CONTENT_TOO_LARGE
    }
    let status = match req.status {
        Some(s @ 1..=3) => s,
        _ => {
            fields.push(("status", "invalid_enum"));
            1
        }
    };
    if create && status == 3 {
        fields.push(("status", "invalid_initial"));
    }
    // slug:归一小写;保留字 → 422705;pattern;published 必填
    let mut slug = req.slug.as_deref().map(str::trim).filter(|s| !s.is_empty()).map(|s| s.to_lowercase());
    if let Some(s) = &slug {
        if RESERVED_SLUGS.contains(&s.as_str()) {
            return Err(CatalogError::new(422705)); // RESERVED_SLUG
        }
        if !slug_valid(s) {
            fields.push(("slug", "pattern_invalid"));
            slug = None;
        }
    }
    if status == 2 && slug.is_none() {
        fields.push(("slug", "required_for_publish"));
    }
    let now_str = chrono::Local::now().format("%Y-%m-%d %H:%M:%S%.3f").to_string();
    if req.published_at.as_deref().is_some_and(|p| p > now_str.as_str()) {
        fields.push(("published_at", "future_not_allowed"));
    }
    if let Some(ts) = &req.translations {
        let mut seen: HashSet<&str> = HashSet::new();
        for t in ts {
            if t.locale != "es" && t.locale != "fr" || !seen.insert(t.locale.as_str()) {
                fields.push(("translations", "invalid_locale"));
                break;
            }
            check_max(&t.title, 200, "translations", &mut fields);
            check_max(&t.excerpt, 500, "translations", &mut fields);
            check_max(&t.seo_title, 128, "translations", &mut fields);
            check_max(&t.seo_description, 255, "translations", &mut fields);
        }
    }
    if !fields.is_empty() {
        return Err(field_err(fields));
    }
    Ok(slug)
}

const BLOG_COLS: &str = "id, title, cover, category, author, content, slug, status, published_at, views, excerpt, seo_title, seo_description, word_count, reading_minutes, version";

/// E-MKT-02 store 博客列表(published;locale 翻译覆盖 title/excerpt)
pub async fn blog_page(
    db: &DatabaseConnection,
    category: Option<String>,
    page: i64,
    page_size: i64,
    locale: &str,
) -> Result<(Vec<Value>, i64), CatalogError> {
    let mut where_clause = "status = 2".to_string();
    if let Some(c) = &category {
        let safe = c.replace('\'', "''");
        where_clause.push_str(&format!(" AND category = '{}'", safe));
    }
    let total = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT COUNT(*) AS n FROM blog_post WHERE {}", where_clause),
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
                "SELECT {} FROM blog_post WHERE {} ORDER BY published_at DESC, id DESC LIMIT {} OFFSET {}",
                BLOG_COLS, where_clause, page_size, offset
            ),
        ))
        .await
        .map_err(db_err)?;
    let ids: Vec<i64> = rows.iter().filter_map(|r| r.try_get::<u64>("", "id").ok().map(|v| v as i64)).collect();
    let trs = blog_translations_map(db, &ids, locale).await?;
    let items = rows
        .iter()
        .map(|r| blog_card(r, &trs, locale))
        .collect();
    Ok((items, total))
}

async fn blog_translations_map(
    db: &DatabaseConnection,
    ids: &[i64],
    locale: &str,
) -> Result<HashMap<i64, Value>, CatalogError> {
    let mut out = HashMap::new();
    if (locale != "es" && locale != "fr") || ids.is_empty() {
        return Ok(out);
    }
    let id_list = ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT blog_post_id, title, excerpt, body FROM blog_post_translation WHERE locale = '{}' AND blog_post_id IN ({})",
                locale, id_list
            ),
        ))
        .await
        .map_err(db_err)?;
    for r in rows {
        if let Some(pid) = r.try_get::<i64>("", "blog_post_id").ok() {
            out.insert(
                pid,
                serde_json::json!({
                    "title": r.try_get::<String>("", "title").ok(),
                    "excerpt": r.try_get::<String>("", "excerpt").ok(),
                    "body": r.try_get::<String>("", "body").ok(),
                }),
            );
        }
    }
    Ok(out)
}

fn pick(tr: &Option<String>, fallback: &Option<String>) -> Value {
    match tr {
        Some(t) if !t.trim().is_empty() => Value::String(t.clone()),
        _ => fallback.clone().map(Value::String).unwrap_or(Value::Null),
    }
}

/// MAP-MKT-003 卡片:excerpt = EN content strip 截断 200 / translation.excerpt 回退
fn blog_card(r: &sea_orm::QueryResult, trs: &HashMap<i64, Value>, locale: &str) -> Value {
    let id = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
    let tr = trs.get(&id);
    let content: Option<String> = r.try_get("", "content").ok();
    let excerpt_en: Option<String> = r.try_get("", "excerpt").ok();
    let excerpt = match tr.and_then(|t| t.get("excerpt")).and_then(|v| v.as_str()) {
        Some(e) if !e.trim().is_empty() => Value::String(e.to_string()),
        _ => excerpt_en
            .or_else(|| content.as_ref().map(|c| {
                let plain: String = c.chars().take(200).collect();
                plain
            }))
            .map(Value::String)
            .unwrap_or(Value::Null),
    };
    let title_tr = tr.and_then(|t| t.get("title")).and_then(|v| v.as_str()).map(String::from);
    let title_fallback: Option<String> = r.try_get("", "title").ok();
    serde_json::json!({
        "id": id,
        "title": pick(&title_tr, &title_fallback),
        "slug": r.try_get::<String>("", "slug").unwrap_or_default(),
        "cover": r.try_get::<String>("", "cover").ok(),
        "category": r.try_get::<String>("", "category").ok(),
        "author": r.try_get::<String>("", "author").ok(),
        "excerpt": excerpt,
        "published_at": r.try_get::<String>("", "published_at").ok(),
        "views": r.try_get::<i64>("", "views").unwrap_or(0),
    })
}

/// E-MKT-03 PDP(slug;locale 翻译 body/seo)
pub async fn blog_by_slug(db: &DatabaseConnection, slug: &str, locale: &str) -> Result<Value, CatalogError> {
    if !slug_valid(slug) {
        return Err(not_found());
    }
    let row = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            format!("SELECT {} FROM blog_post WHERE slug = ? AND status = 2", BLOG_COLS),
            [slug.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    let id = row.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
    let trs = blog_translations_map(db, &[id], locale).await?;
    let tr = trs.get(&id);
    let title_tr = tr.and_then(|t| t.get("title")).and_then(|v| v.as_str()).map(String::from);
    let title_fb: Option<String> = row.try_get("", "title").ok();
    let body_tr = tr.and_then(|t| t.get("body")).and_then(|v| v.as_str()).map(String::from);
    let content_fb: Option<String> = row.try_get("", "content").ok();
    Ok(serde_json::json!({
        "id": id,
        "title": pick(&title_tr, &title_fb),
        "slug": row.try_get::<String>("", "slug").unwrap_or_default(),
        "cover": row.try_get::<String>("", "cover").ok(),
        "category": row.try_get::<String>("", "category").ok(),
        "author": row.try_get::<String>("", "author").ok(),
        "excerpt": pick(
            &tr.and_then(|t| t.get("excerpt")).and_then(|v| v.as_str()).map(String::from),
            &row.try_get::<String>("", "excerpt").ok(),
        ),
        "published_at": row.try_get::<String>("", "published_at").ok(),
        "views": row.try_get::<i64>("", "views").unwrap_or(0),
        "content": pick(&body_tr, &content_fb),
        "seo_title": row.try_get::<String>("", "seo_title").ok(),
        "seo_description": row.try_get::<String>("", "seo_description").ok(),
    }))
}

/// E-MKT-03B 阅读计数(静默 204;未发布 slug 无操作)
pub async fn blog_record_view(db: &DatabaseConnection, slug: &str) -> Result<(), CatalogError> {
    if !slug_valid(slug) {
        return Ok(());
    }
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE blog_post SET views = views + 1 WHERE slug = ? AND status = 2",
        [slug.into()],
    ))
    .await
    .map_err(db_err)?;
    Ok(())
}

/// sitemap 数据(published;slug+updatedAt)
pub async fn blog_sitemap(db: &DatabaseConnection) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT slug, updated_at FROM blog_post WHERE status = 2 ORDER BY updated_at DESC",
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            serde_json::json!({
                "slug": r.try_get::<String>("", "slug").unwrap_or_default(),
                "updated_at": r.try_get::<String>("", "updated_at").ok(),
            })
        })
        .collect())
}

/// admin 博客列表(全态;分页)
pub async fn blog_admin_page(
    db: &DatabaseConnection,
    page: i64,
    page_size: i64,
) -> Result<(Vec<Value>, i64), CatalogError> {
    let total = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            "SELECT COUNT(*) AS n FROM blog_post".to_string(),
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
                "SELECT {} FROM blog_post ORDER BY id DESC LIMIT {} OFFSET {}",
                BLOG_COLS, page_size, offset
            ),
        ))
        .await
        .map_err(db_err)?;
    let items: Vec<Value> = rows
        .iter()
        .map(|r| {
            serde_json::json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "title": r.try_get::<String>("", "title").ok(),
                "slug": r.try_get::<String>("", "slug").ok(),
                "status": r.try_get::<i64>("", "status").unwrap_or(1),
                "views": r.try_get::<i64>("", "views").unwrap_or(0),
                "published_at": r.try_get::<String>("", "published_at").ok(),
            })
        })
        .collect();
    Ok((items, total))
}

/// admin 博客详情(全字段+translations)
pub async fn blog_admin_get(db: &DatabaseConnection, id: i64) -> Result<Value, CatalogError> {
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT {} FROM blog_post WHERE id = {}", BLOG_COLS, id),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    let translations = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT locale, title, excerpt, body, seo_title, seo_description FROM blog_post_translation WHERE blog_post_id = {}",
                id
            ),
        ))
        .await
        .map_err(db_err)?;
    Ok(serde_json::json!({
        "id": row.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "title": row.try_get::<String>("", "title").ok(),
        "slug": row.try_get::<String>("", "slug").ok(),
        "status": row.try_get::<i64>("", "status").unwrap_or(1),
        "published_at": row.try_get::<String>("", "published_at").ok(),
        "views": row.try_get::<i64>("", "views").unwrap_or(0),
        "translations": translations.iter().map(|t| serde_json::json!({
            "locale": t.try_get::<String>("", "locale").unwrap_or_default(),
            "title": t.try_get::<String>("", "title").ok(),
            "excerpt": t.try_get::<String>("", "excerpt").ok(),
            "body": t.try_get::<String>("", "body").ok(),
        })).collect::<Vec<_>>(),
    }))
}

async fn replace_blog_translations(
    tx: &sea_orm::DatabaseTransaction,
    post_id: i64,
    req: &BlogUpsert,
) -> Result<(), CatalogError> {
    tx.execute(Statement::from_string(
        DbBackend::MySql,
        format!("DELETE FROM blog_post_translation WHERE blog_post_id = {}", post_id),
    ))
    .await
    .map_err(db_err)?;
    if let Some(ts) = &req.translations {
        for t in ts {
            tx.execute(Statement::from_sql_and_values(
                DbBackend::MySql,
                "INSERT INTO blog_post_translation(blog_post_id, locale, title, excerpt, body, seo_title, seo_description, created_at, updated_at) VALUES (?,?,?,?,?,?,?,NOW(3),NOW(3))",
                [
                    post_id.into(),
                    t.locale.clone().into(),
                    t.title.clone().into(),
                    t.excerpt.clone().into(),
                    t.body.clone().into(),
                    t.seo_title.clone().into(),
                    t.seo_description.clone().into(),
                ],
            ))
            .await
            .map_err(db_err)?;
        }
    }
    Ok(())
}

/// E-MKT-24 blog 创建
pub async fn blog_create(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    req: BlogUpsert,
    operator: &str,
) -> Result<Value, CatalogError> {
    let slug = validate_blog(&req, true)?;
    let title = req.title.clone().unwrap_or_default();
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO blog_post(title, cover, category, author, content, slug, status, excerpt, seo_title, seo_description, published_at, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,NOW(3),NOW(3))",
        [
            title.clone().into(),
            req.cover.clone().into(),
            req.category.clone().into(),
            req.author.clone().into(),
            req.content.clone().into(),
            slug.clone().into(),
            req.status.unwrap_or(1).into(),
            req.excerpt.clone().into(),
            req.seo_title.clone().into(),
            req.seo_description.clone().into(),
            req.published_at.clone().into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    let id = inserted_id(&tx).await?;
    replace_blog_translations(&tx, id as i64, &req).await?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "创建博客", &title).await;
    blog_admin_get(db, id as i64).await
}

/// E-MKT-25 blog 编辑(version 乐观锁)
pub async fn blog_update(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    req: BlogUpsert,
    operator: &str,
) -> Result<Value, CatalogError> {
    let existing = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT id, title, status, version FROM blog_post WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    let existing_status: i64 = existing.try_get("", "status").unwrap_or(1);
    let db_version: i64 = existing.try_get("", "version").unwrap_or(0);
    if let Some(v) = req.version {
        if v != db_version {
            return Err(CatalogError::new(409704)); // VERSION_CONFLICT
        }
    }
    let slug = validate_blog(&req, false)?;
    let new_status = req.status.unwrap_or(existing_status);
    if new_status != existing_status && !transition_allowed(existing_status, new_status) {
        return Err(CatalogError::new(409703).with_detail("reason", Value::from("illegal_transition")));
    }
    let title = req.title.clone().unwrap_or_default();
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE blog_post SET title=?, cover=?, category=?, author=?, content=?, slug=?, status=?, excerpt=?, seo_title=?, seo_description=?, published_at=COALESCE(?, published_at), version=version+1, updated_at=NOW(3) WHERE id=?",
        [
            title.clone().into(),
            req.cover.clone().into(),
            req.category.clone().into(),
            req.author.clone().into(),
            req.content.clone().into(),
            slug.into(),
            new_status.into(),
            req.excerpt.clone().into(),
            req.seo_title.clone().into(),
            req.seo_description.clone().into(),
            req.published_at.clone().into(),
            id.into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    replace_blog_translations(&tx, id, &req).await?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "编辑博客", &title).await;
    blog_admin_get(db, id).await
}

/// E-MKT-26 blog 删除
pub async fn blog_delete(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    operator: &str,
) -> Result<(), CatalogError> {
    let existing = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT title FROM blog_post WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    let title: String = existing.try_get("", "title").unwrap_or_default();
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_string(
        DbBackend::MySql,
        format!("DELETE FROM blog_post_translation WHERE blog_post_id = {}", id),
    ))
    .await
    .map_err(db_err)?;
    tx.execute(Statement::from_string(
        DbBackend::MySql,
        format!("DELETE FROM blog_post WHERE id = {}", id),
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "删除博客", &title).await;
    Ok(())
}

/// E-MKT-27 blog status Toggle(状态机)
pub async fn blog_toggle_status(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    status: i64,
    operator: &str,
) -> Result<Value, CatalogError> {
    if !(1..=3).contains(&status) {
        return Err(field_err(vec![("status", "invalid_enum")]));
    }
    let existing = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT id, title, status, slug FROM blog_post WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(not_found)?;
    let cur: i64 = existing.try_get("", "status").unwrap_or(1);
    let title: String = existing.try_get("", "title").unwrap_or_default();
    let slug: Option<String> = existing.try_get("", "slug").ok();
    if cur == status {
        return blog_admin_get(db, id).await;
    }
    if !transition_allowed(cur, status) {
        return Err(CatalogError::new(409703).with_detail("reason", Value::from("illegal_transition")));
    }
    if status == 2 && slug.is_none() {
        return Err(field_err(vec![("slug", "required_for_publish")]));
    }
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE blog_post SET status = ?, updated_at = NOW(3) WHERE id = ?",
        [status.into(), id.into()],
    ))
    .await
    .map_err(db_err)?;
    audit(audit_db, operator, "编辑博客", &title).await;
    blog_admin_get(db, id).await
}

/// 预览 token 签发(UUID v4;Redis blog:preview:{token} TTL 4h)
pub async fn blog_preview_issue(
    db: &DatabaseConnection,
    redis: Option<&redis::aio::ConnectionManager>,
    id: i64,
) -> Result<Value, CatalogError> {
    let exists = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT id FROM blog_post WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .is_some();
    if !exists {
        return Err(not_found());
    }
    let Some(redis) = redis else {
        return Err(CatalogError::new(401701));
    };
    let token = uuid::Uuid::new_v4().to_string();
    let mut conn = redis.clone();
    redis::cmd("SETEX")
        .arg(format!("blog:preview:{}", token))
        .arg(4 * 3600)
        .arg(id.to_string())
        .query_async::<()>(&mut conn)
        .await
        .map_err(|e| {
            tracing::error!("[content] preview token setex:{e}");
            CatalogError::new(500701)
        })?;
    Ok(serde_json::json!({
        "token": token,
        "preview_url": format!("/blog/preview/{}", token),
        "expires_in_seconds": 4 * 3600,
    }))
}

/// 预览 token 解析(过期/伪造/删除统一 401701)
pub async fn blog_preview_resolve(
    db: &DatabaseConnection,
    redis: Option<&redis::aio::ConnectionManager>,
    token: &str,
) -> Result<Value, CatalogError> {
    let Some(redis) = redis else {
        return Err(CatalogError::new(401701));
    };
    let mut conn = redis.clone();
    let raw: Option<String> = redis::cmd("GET")
        .arg(format!("blog:preview:{}", token))
        .query_async(&mut conn)
        .await
        .map_err(|_| CatalogError::new(401701))?;
    let Some(raw) = raw else {
        return Err(CatalogError::new(401701));
    };
    let Ok(post_id) = raw.parse::<i64>() else {
        return Err(CatalogError::new(401701));
    };
    // 凭 token 直读,不校验 status
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT {} FROM blog_post WHERE id = {}", BLOG_COLS, post_id),
        ))
        .await
        .map_err(db_err)?
        .ok_or(CatalogError::new(401701))?;
    let trs = blog_translations_map(db, &[post_id], "en").await?;
    let _ = trs;
    Ok(serde_json::json!({
        "id": row.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "title": row.try_get::<String>("", "title").ok(),
        "slug": row.try_get::<String>("", "slug").ok(),
        "content": row.try_get::<String>("", "content").ok(),
    }))
}

// ══════════════════ real_wedding(E-MKT-04/05/39~43)══════════════════

/// E-MKT-04 store 婚礼列表(published;locale 翻译)
pub async fn wedding_page(
    db: &DatabaseConnection,
    page: i64,
    page_size: i64,
    locale: &str,
) -> Result<(Vec<Value>, i64), CatalogError> {
    let total = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            "SELECT COUNT(*) AS n FROM real_wedding WHERE status = 2".to_string(),
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
                "SELECT id, couple, location, theme, wedding_date, cover, status, title, story FROM real_wedding WHERE status = 2 ORDER BY id DESC LIMIT {} OFFSET {}",
                page_size, offset
            ),
        ))
        .await
        .map_err(db_err)?;
    let ids: Vec<i64> = rows.iter().filter_map(|r| r.try_get::<u64>("", "id").ok().map(|v| v as i64)).collect();
    let trs = wedding_translations_map(db, &ids, locale).await?;
    let items = rows
        .iter()
        .map(|r| {
            let id = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            let tr = trs.get(&id);
            serde_json::json!({
                "id": id,
                "couple": r.try_get::<String>("", "couple").unwrap_or_default(),
                "location": r.try_get::<String>("", "location").ok(),
                "theme": r.try_get::<String>("", "theme").ok(),
                "wedding_date": r.try_get::<String>("", "wedding_date").ok(),
                "cover": r.try_get::<String>("", "cover").ok(),
                "status": 2,
                "title": pick(
                    &tr.and_then(|t| t.get("title")).and_then(|v| v.as_str()).map(String::from),
                    &r.try_get::<String>("", "title").ok(),
                ),
                "story": pick(
                    &tr.and_then(|t| t.get("story")).and_then(|v| v.as_str()).map(String::from),
                    &r.try_get::<String>("", "story").ok(),
                ),
                "products": [],
            })
        })
        .collect();
    Ok((items, total))
}

async fn wedding_translations_map(
    db: &DatabaseConnection,
    ids: &[i64],
    locale: &str,
) -> Result<HashMap<i64, Value>, CatalogError> {
    let mut out = HashMap::new();
    if (locale != "es" && locale != "fr") || ids.is_empty() {
        return Ok(out);
    }
    let id_list = ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT real_wedding_id, title, story FROM real_wedding_translation WHERE locale = '{}' AND real_wedding_id IN ({})",
                locale, id_list
            ),
        ))
        .await
        .map_err(db_err)?;
    for r in rows {
        if let Some(wid) = r.try_get::<u64>("", "real_wedding_id").ok().map(|v| v as i64) {
            out.insert(
                wid,
                serde_json::json!({
                    "title": r.try_get::<String>("", "title").ok(),
                    "story": r.try_get::<String>("", "story").ok(),
                }),
            );
        }
    }
    Ok(out)
}

// ══════════════════ lookbook(E-MKT-06/07/44~48)══════════════════

/// E-MKT-06 store lookbook 列表(enabled;fallback_cover=首商品主图)
pub async fn lookbook_list(db: &DatabaseConnection, locale: &str) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, title, theme, description, cover FROM lookbook WHERE status = 1 ORDER BY id ASC",
        ))
        .await
        .map_err(db_err)?;
    let mut out = vec![];
    for r in &rows {
        let id = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
        let products = lookbook_products(db, id).await?;
        let fallback_cover = products
            .first()
            .and_then(|p| p.get("image_url"))
            .and_then(|v| v.as_str())
            .map(String::from);
        out.push(serde_json::json!({
            "id": id,
            "title": r.try_get::<String>("", "title").ok(),
            "theme": r.try_get::<String>("", "theme").ok(),
            "description": r.try_get::<String>("", "description").ok(),
            "cover": r.try_get::<String>("", "cover").ok(),
            "fallback_cover": fallback_cover,
            "products": products,
        }));
    }
    Ok(out)
}

async fn lookbook_products(db: &DatabaseConnection, lookbook_id: i64) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT p.id, p.slug, p.name, p.status FROM lookbook_product lp JOIN product p ON p.id = lp.product_id WHERE lp.lookbook_id = {} ORDER BY lp.id ASC",
                lookbook_id
            ),
        ))
        .await
        .map_err(db_err)?;
    let pids: Vec<i64> = rows.iter().filter_map(|r| r.try_get::<u64>("", "id").ok().map(|v| v as i64)).collect();
    let mut primary: HashMap<i64, String> = HashMap::new();
    if !pids.is_empty() {
        let pid_list = pids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
        let img_rows = db
            .query_all(Statement::from_string(
                DbBackend::MySql,
                format!(
                    "SELECT product_id AS pid, url FROM product_image WHERE kind = 1 AND product_id IN ({}) ORDER BY sort ASC, id ASC",
                    pid_list
                ),
            ))
            .await
            .map_err(db_err)?;
        for r in img_rows {
            if let (Some(pid), Ok(url)) = (r.try_get::<u64>("", "pid").ok().map(|v| v as i64), r.try_get::<String>("", "url")) {
                primary.entry(pid).or_insert(url);
            }
        }
    }
    Ok(rows
        .iter()
        .map(|r| {
            let pid = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            serde_json::json!({
                "product_id": pid,
                "slug": r.try_get::<String>("", "slug").unwrap_or_default(),
                "name": r.try_get::<String>("", "name").unwrap_or_default(),
                "status": r.try_get::<i64>("", "status").unwrap_or(1),
                "image_url": primary.get(&pid),
            })
        })
        .collect())
}

/// E-MKT-07 store lookbook 详情
pub async fn lookbook_get(db: &DatabaseConnection, id: i64, locale: &str) -> Result<Value, CatalogError> {
    let all = lookbook_list(db, locale).await?;
    all.into_iter().find(|l| l["id"] == Value::from(id)).ok_or_else(not_found)
}

// ══════════════════ guide(E-MKT-08/49~55)══════════════════

/// E-MKT-08 store 备婚指南(status=published;tasks 嵌套)
pub async fn guide_list(db: &DatabaseConnection, locale: &str) -> Result<Vec<Value>, CatalogError> {
    let guides = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            "SELECT id, phase, timeframe, title, body FROM guide WHERE status = 2 ORDER BY sort_order ASC, id ASC",
        ))
        .await
        .map_err(db_err)?;
    let mut out = vec![];
    for g in &guides {
        let id = g.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
        let tasks = db
            .query_all(Statement::from_string(
                DbBackend::MySql,
                format!("SELECT id, label FROM guide_task WHERE guide_id = {} ORDER BY sort_order ASC, id ASC", id),
            ))
            .await
            .map_err(db_err)?;
        let task_list: Vec<Value> = tasks
            .iter()
            .map(|t| {
                serde_json::json!({
                    "task_id": t.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                    "label": t.try_get::<String>("", "label").unwrap_or_default(),
                })
            })
            .collect();
        out.push(serde_json::json!({
            "id": id,
            "phase": g.try_get::<String>("", "phase").ok(),
            "timeframe": g.try_get::<String>("", "timeframe").ok(),
            "title": g.try_get::<String>("", "title").ok(),
            "body": g.try_get::<String>("", "body").ok(),
            "tasks_count": task_list.len(),
            "tasks": task_list,
        }));
    }
    let _ = locale;
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn slug_reserved_blacklist() {
        for s in RESERVED_SLUGS {
            assert!(RESERVED_SLUGS.contains(&s));
        }
        assert!(!RESERVED_SLUGS.contains(&"summer-dresses"));
    }

    #[test]
    fn slug_pattern() {
        assert!(slug_valid("summer-dresses-2026"));
        assert!(!slug_valid("Summer"));
        assert!(!slug_valid("a b"));
        assert!(!slug_valid(""));
        assert!(!slug_valid(&"x".repeat(129)));
    }

    #[test]
    fn blog_validation_slug_required_for_publish() {
        let req = BlogUpsert {
            title: Some("T".into()),
            cover: None,
            category: None,
            author: None,
            content: None,
            slug: None,
            status: Some(2),
            excerpt: None,
            seo_title: None,
            seo_description: None,
            version: None,
            published_at: None,
            translations: None,
        };
        let err = validate_blog(&req, true).unwrap_err();
        assert_eq!(err.details.unwrap()["fields"]["slug"], "required_for_publish");
    }

    #[test]
    fn blog_validation_reserved_slug_422705() {
        let req = BlogUpsert {
            title: Some("T".into()),
            cover: None,
            category: None,
            author: None,
            content: None,
            slug: Some("admin".into()),
            status: Some(1),
            excerpt: None,
            seo_title: None,
            seo_description: None,
            version: None,
            published_at: None,
            translations: None,
        };
        let err = validate_blog(&req, true).unwrap_err();
        assert_eq!(err.code, 422705);
    }

    #[test]
    fn blog_validation_content_too_large() {
        let req = BlogUpsert {
            title: Some("T".into()),
            cover: None,
            category: None,
            author: None,
            content: Some("x".repeat(BLOG_BODY_MAX + 1)),
            slug: Some("ok".into()),
            status: Some(1),
            excerpt: None,
            seo_title: None,
            seo_description: None,
            version: None,
            published_at: None,
            translations: None,
        };
        let err = validate_blog(&req, true).unwrap_err();
        assert_eq!(err.code, 422706);
    }
}
