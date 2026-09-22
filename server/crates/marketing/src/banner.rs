//! banner 域(E-MKT-21~25):admin CRUD + 状态机 Toggle + 时间窗。
//! 对齐 AdminBannerService + ContentStateGuards;错误码 404701/409703/422704。
//! 缓存失效任务与窗口调度随 cache 域迁移接线(本批先落核心契约)。

use std::collections::{HashMap, HashSet};

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement, TransactionTrait};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::service_category::{cat_err, CatalogError};

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[banner] db error:{e}");
    CatalogError::new(500701)
}

fn not_found() -> CatalogError {
    CatalogError::new(404701)
}

fn state_invalid(reason: &str) -> CatalogError {
    CatalogError::new(409703).with_detail("reason", Value::from(reason))
}

fn field_err(fields: Vec<(&'static str, &'static str)>) -> CatalogError {
    CatalogError::field_validation(&fields)
}

async fn audit(
    audit_db: &sea_orm::DatabaseConnection,
    operator_id: &str,
    action: &str,
    target: &str,
    changes: Option<&str>,
) {
    let oid: Option<i64> = operator_id.parse().ok();
    if let Err(e) = audit_db
        .execute(sea_orm::Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO operation_log(operator_id, operator_name, action, target, ip, user_agent, changes, created_at, updated_at) VALUES (?,NULL,?,?,NULL,NULL,?,NOW(3),NOW(3))",
            [oid.into(), action.into(), target.into(), changes.into()],
        ))
        .await
    {
        tracing::warn!("[banner] audit best-effort failed:{e}");
    }
}

/// 三态内容状态机(draft→published / published→archived / archived→published)
pub fn transition_allowed(from: i64, to: i64) -> bool {
    matches!((from, to), (1, 2) | (2, 3) | (3, 2))
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct BannerTranslationIn {
    pub locale: String,
    pub image_url: Option<String>,
    pub title: Option<String>,
    pub subtitle: Option<String>,
    pub cta_text: Option<String>,
    pub cta_text_secondary: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct BannerUpsert {
    pub name: Option<String>,
    pub image_url: Option<String>,
    pub position: Option<i64>,
    pub start_time: Option<String>,
    pub end_time: Option<String>,
    pub status: Option<i64>,
    pub sort: Option<i64>,
    pub title: Option<String>,
    pub subtitle: Option<String>,
    pub cta_text: Option<String>,
    pub cta_link: Option<String>,
    pub cta_text_secondary: Option<String>,
    pub cta_link_secondary: Option<String>,
    pub translations: Option<Vec<BannerTranslationIn>>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct BannerDto {
    pub id: i64,
    pub name: String,
    pub image_url: String,
    pub position: i64,
    pub start_time: Option<String>,
    pub end_time: Option<String>,
    pub status: i64,
    pub sort: i64,
    pub title: Option<String>,
    pub subtitle: Option<String>,
    pub cta_text: Option<String>,
    pub cta_link: Option<String>,
    pub cta_text_secondary: Option<String>,
    pub cta_link_secondary: Option<String>,
    pub translations: Vec<Value>,
}

fn check_max(v: &Option<String>, max: usize, field: &'static str, fields: &mut Vec<(&'static str, &'static str)>) -> Option<String> {
    match v.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
        Some(s) if s.len() > max => {
            fields.push((field, "too_long"));
            None
        }
        other => other.map(String::from),
    }
}

#[derive(Debug)]
struct Normalized {
    name: String,
    image_url: String,
    position: i64,
    status: i64,
    sort: i64,
    title: Option<String>,
    subtitle: Option<String>,
    cta_text: Option<String>,
    cta_link: Option<String>,
    cta_text_secondary: Option<String>,
    cta_link_secondary: Option<String>,
    start_time: Option<String>,
    end_time: Option<String>,
}

fn validate_upsert(req: &BannerUpsert) -> Result<Normalized, CatalogError> {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let name = req.name.as_deref().map(str::trim).filter(|s| !s.is_empty());
    let name = match name {
        None => {
            fields.push(("name", "required"));
            String::new()
        }
        Some(n) if n.len() > 128 => {
            fields.push(("name", "too_long"));
            String::new()
        }
        Some(n) => n.to_string(),
    };
    let image_url = req.image_url.as_deref().map(str::trim).filter(|s| !s.is_empty());
    let image_url = match image_url {
        None => {
            fields.push(("image_url", "required"));
            String::new()
        }
        Some(u) if u.len() > 512 => {
            fields.push(("image_url", "too_long"));
            String::new()
        }
        Some(u) => u.to_string(),
    };
    // V-MKT-040 position 必填枚举 1..3
    let position = match req.position {
        Some(p @ 1..=3) => p,
        _ => {
            fields.push(("position", "invalid_enum"));
            1
        }
    };
    // V-MKT-041 end_time > start_time
    if let (Some(s), Some(e)) = (&req.start_time, &req.end_time) {
        if s >= e {
            fields.push(("end_time", "before_start"));
        }
    }
    // V-MKT-042 status 枚举;创建态禁 archived(2/3 枚举内)
    let status = match req.status {
        Some(s @ 1..=3) => s,
        _ => {
            fields.push(("status", "invalid_enum"));
            1
        }
    };
    // V-MKT-043 sort ≥0
    let sort = match req.sort {
        None => {
            fields.push(("sort", "required"));
            0
        }
        Some(s) if s < 0 => {
            fields.push(("sort", "range_invalid"));
            0
        }
        Some(s) => s,
    };
    // V-MKT-044 文案列长;FEATURED(2) 禁 secondary CTA
    let title = check_max(&req.title, 255, "title", &mut fields);
    let subtitle = check_max(&req.subtitle, 255, "subtitle", &mut fields);
    let cta_text = check_max(&req.cta_text, 64, "cta_text", &mut fields);
    let cta_link = check_max(&req.cta_link, 512, "cta_link", &mut fields);
    let (cta_text_secondary, cta_link_secondary) = if position == 2 {
        (None, None)
    } else {
        (
            check_max(&req.cta_text_secondary, 64, "cta_text_secondary", &mut fields),
            check_max(&req.cta_link_secondary, 512, "cta_link_secondary", &mut fields),
        )
    };
    // translations
    if let Some(ts) = &req.translations {
        let mut seen: HashSet<&str> = HashSet::new();
        for t in ts {
            if t.locale != "es" && t.locale != "fr" {
                fields.push(("translations", "invalid_locale"));
                break;
            }
            if !seen.insert(t.locale.as_str()) {
                fields.push(("translations", "duplicate_locale"));
                break;
            }
            if t.image_url.as_deref().is_some_and(|v| v.len() > 512) {
                fields.push(("translations", "image_url_too_long"));
                break;
            }
            if t.title.as_deref().is_some_and(|v| v.len() > 255) {
                fields.push(("translations", "title_too_long"));
                break;
            }
            if t.subtitle.as_deref().is_some_and(|v| v.len() > 255) {
                fields.push(("translations", "subtitle_too_long"));
                break;
            }
            if t.cta_text.as_deref().is_some_and(|v| v.len() > 64) {
                fields.push(("translations", "cta_text_too_long"));
                break;
            }
        }
    }
    if !fields.is_empty() {
        return Err(field_err(fields));
    }
    Ok(Normalized {
        name,
        image_url,
        position,
        status,
        sort,
        title,
        subtitle,
        cta_text,
        cta_link,
        cta_text_secondary,
        cta_link_secondary,
        start_time: req.start_time.clone(),
        end_time: req.end_time.clone(),
    })
}

fn row_to_dto(r: &sea_orm::QueryResult, translations: Vec<Value>) -> BannerDto {
    BannerDto {
        id: r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        name: r.try_get::<String>("", "name").unwrap_or_default(),
        image_url: r.try_get::<String>("", "image_url").unwrap_or_default(),
        position: r.try_get::<i64>("", "position").unwrap_or(1),
        start_time: r.try_get::<String>("", "start_time").ok(),
        end_time: r.try_get::<String>("", "end_time").ok(),
        status: r.try_get::<i64>("", "status").unwrap_or(1),
        sort: r.try_get::<i64>("", "sort").unwrap_or(0),
        title: r.try_get::<String>("", "title").ok(),
        subtitle: r.try_get::<String>("", "subtitle").ok(),
        cta_text: r.try_get::<String>("", "cta_text").ok(),
        cta_link: r.try_get::<String>("", "cta_link").ok(),
        cta_text_secondary: r.try_get::<String>("", "cta_text_secondary").ok(),
        cta_link_secondary: r.try_get::<String>("", "cta_link_secondary").ok(),
        translations,
    }
}

const COLS: &str = "id, name, image_url, position, start_time, end_time, status, sort, title, subtitle, cta_text, cta_link, cta_text_secondary, cta_link_secondary";

async fn find_by_id(db: &DatabaseConnection, id: i64) -> Result<Option<BannerDto>, CatalogError> {
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT {} FROM banner WHERE id = {}", COLS, id),
        ))
        .await
        .map_err(db_err)?;
    match row {
        Some(r) => {
            let translations = load_translations(db, id).await?;
            Ok(Some(row_to_dto(&r, translations)))
        }
        None => Ok(None),
    }
}

async fn load_translations(db: &DatabaseConnection, banner_id: i64) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT locale, image_url, title, subtitle, cta_text, cta_text_secondary FROM banner_translation WHERE banner_id = {}",
                banner_id
            ),
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            serde_json::json!({
                "locale": r.try_get::<String>("", "locale").unwrap_or_default(),
                "image_url": r.try_get::<String>("", "image_url").ok(),
                "title": r.try_get::<String>("", "title").ok(),
                "subtitle": r.try_get::<String>("", "subtitle").ok(),
                "cta_text": r.try_get::<String>("", "cta_text").ok(),
                "cta_text_secondary": r.try_get::<String>("", "cta_text_secondary").ok(),
            })
        })
        .collect())
}

async fn replace_translations(
    tx: &sea_orm::DatabaseTransaction,
    banner_id: i64,
    req: &BannerUpsert,
) -> Result<(), CatalogError> {
    tx.execute(Statement::from_string(
        DbBackend::MySql,
        format!("DELETE FROM banner_translation WHERE banner_id = {}", banner_id),
    ))
    .await
    .map_err(db_err)?;
    if let Some(ts) = &req.translations {
        for t in ts {
            tx.execute(Statement::from_sql_and_values(
                DbBackend::MySql,
                "INSERT INTO banner_translation(banner_id, locale, image_url, title, subtitle, cta_text, cta_text_secondary, created_at, updated_at) VALUES (?,?,?,?,?,?,?,NOW(3),NOW(3))",
                [
                    banner_id.into(),
                    t.locale.clone().into(),
                    t.image_url.clone().into(),
                    t.title.clone().into(),
                    t.subtitle.clone().into(),
                    t.cta_text.clone().into(),
                    t.cta_text_secondary.clone().into(),
                ],
            ))
            .await
            .map_err(db_err)?;
        }
    }
    Ok(())
}

/// E-MKT-21 列表(position 筛选 ORDER BY sort)
pub async fn list(db: &DatabaseConnection, position: Option<i64>) -> Result<Vec<BannerDto>, CatalogError> {
    if let Some(p) = position {
        if !(1..=3).contains(&p) {
            return Err(field_err(vec![("position", "invalid_enum")]));
        }
    }
    let sql = match position {
        Some(p) => format!("SELECT {} FROM banner WHERE position = {} ORDER BY sort ASC, id ASC", COLS, p),
        None => format!("SELECT {} FROM banner ORDER BY sort ASC, id ASC", COLS),
    };
    let rows = db.query_all(Statement::from_string(DbBackend::MySql, sql)).await.map_err(db_err)?;
    let mut out = vec![];
    for r in &rows {
        let id = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
        let translations = load_translations(db, id).await?;
        out.push(row_to_dto(r, translations));
    }
    Ok(out)
}

/// E-MKT-22 创建(TX-MKT-007;创建态禁 archived)
pub async fn create(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    req: BannerUpsert,
    operator: &str,
) -> Result<BannerDto, CatalogError> {
    let n = validate_upsert(&req)?;
    if n.status == 3 {
        return Err(field_err(vec![("status", "invalid_initial")]));
    }
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO banner(name, image_url, position, start_time, end_time, status, sort, title, subtitle, cta_text, cta_link, cta_text_secondary, cta_link_secondary, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(3),NOW(3))",
        [
            n.name.clone().into(),
            n.image_url.clone().into(),
            n.position.into(),
            n.start_time.clone().into(),
            n.end_time.clone().into(),
            n.status.into(),
            n.sort.into(),
            n.title.clone().into(),
            n.subtitle.clone().into(),
            n.cta_text.clone().into(),
            n.cta_link.clone().into(),
            n.cta_text_secondary.clone().into(),
            n.cta_link_secondary.clone().into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    let id = tx
        .query_one(Statement::from_string(DbBackend::MySql, "SELECT LAST_INSERT_ID() AS id".to_string()))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<u64>("", "id").ok())
        .unwrap_or(0);
    replace_translations(&tx, id as i64, &req).await?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "创建Banner", &n.name, None).await;
    find_by_id(db, id as i64).await?.ok_or_else(not_found)
}

/// E-MKT-23 编辑(TX-MKT-008;状态迁移 guard)
pub async fn update(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    req: BannerUpsert,
    operator: &str,
) -> Result<BannerDto, CatalogError> {
    let existing = find_by_id(db, id).await?.ok_or_else(not_found)?;
    let n = validate_upsert(&req)?;
    if existing.status != n.status && !transition_allowed(existing.status, n.status) {
        return Err(state_invalid("illegal_transition"));
    }
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE banner SET name=?, image_url=?, position=?, start_time=?, end_time=?, status=?, sort=?, title=?, subtitle=?, cta_text=?, cta_link=?, cta_text_secondary=?, cta_link_secondary=?, updated_at=NOW(3) WHERE id=?",
        [
            n.name.clone().into(),
            n.image_url.clone().into(),
            n.position.into(),
            n.start_time.clone().into(),
            n.end_time.clone().into(),
            n.status.into(),
            n.sort.into(),
            n.title.clone().into(),
            n.subtitle.clone().into(),
            n.cta_text.clone().into(),
            n.cta_link.clone().into(),
            n.cta_text_secondary.clone().into(),
            n.cta_link_secondary.clone().into(),
            id.into(),
        ],
    ))
    .await
    .map_err(db_err)?;
    replace_translations(&tx, id, &req).await?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "编辑Banner", &existing.name, None).await;
    find_by_id(db, id).await?.ok_or_else(not_found)
}

/// E-MKT-24 删除(TX-MKT-009;全态可删)
pub async fn delete(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    operator: &str,
) -> Result<(), CatalogError> {
    let existing = find_by_id(db, id).await?.ok_or_else(not_found)?;
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_string(
        DbBackend::MySql,
        format!("DELETE FROM banner_translation WHERE banner_id = {}", id),
    ))
    .await
    .map_err(db_err)?;
    tx.execute(Statement::from_string(
        DbBackend::MySql,
        format!("DELETE FROM banner WHERE id = {}", id),
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "删除Banner", &existing.name, None).await;
    Ok(())
}

/// E-MKT-25 Toggle(幂等短路;迁移 guard;changes 记 before/after)
pub async fn toggle_status(
    db: &DatabaseConnection,
    audit_db: &DatabaseConnection,
    id: i64,
    status: i64,
    operator: &str,
) -> Result<BannerDto, CatalogError> {
    if !(1..=3).contains(&status) {
        return Err(field_err(vec![("status", "invalid_enum")]));
    }
    let existing = find_by_id(db, id).await?.ok_or_else(not_found)?;
    if existing.status == status {
        return Ok(existing); // 幂等短路
    }
    if !transition_allowed(existing.status, status) {
        return Err(state_invalid("illegal_transition"));
    }
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE banner SET status = ?, updated_at = NOW(3) WHERE id = ?",
        [status.into(), id.into()],
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    let changes = format!(
        r#"{{"status":{{"before":{},"after":{}}}}}"#,
        existing.status, status
    );
    audit(audit_db, operator, "编辑Banner", &existing.name, Some(&changes)).await;
    find_by_id(db, id).await?.ok_or_else(not_found)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn state_machine_topology() {
        assert!(transition_allowed(1, 2), "draft→published");
        assert!(transition_allowed(2, 3), "published→archived");
        assert!(transition_allowed(3, 2), "archived→published(republish)");
        assert!(!transition_allowed(1, 3), "draft→archived 非法");
        assert!(!transition_allowed(2, 1), "published→draft 非法");
        assert!(!transition_allowed(1, 1), "同态不经 guard");
    }

    #[test]
    fn featured_bans_secondary_cta() {
        let req = BannerUpsert {
            name: Some("B".into()),
            image_url: Some("https://x/y.png".into()),
            position: Some(2),
            start_time: None,
            end_time: None,
            status: Some(1),
            sort: Some(0),
            title: None,
            subtitle: None,
            cta_text: None,
            cta_link: None,
            cta_text_secondary: Some("secondary".into()),
            cta_link_secondary: None,
            translations: None,
        };
        // FEATURED 下 secondary 被静默置 null(Java 侧同义:position==FEATURED ? null : ...)
        let n = validate_upsert(&req).expect("应通过");
        assert_eq!(n.cta_text_secondary, None);
        assert_eq!(n.cta_link_secondary, None);
    }

    #[test]
    fn end_time_before_start_rejected() {
        let req = BannerUpsert {
            name: Some("B".into()),
            image_url: Some("https://x/y.png".into()),
            position: Some(1),
            start_time: Some("2026-10-01 00:00:00".into()),
            end_time: Some("2026-09-01 00:00:00".into()),
            status: Some(1),
            sort: Some(0),
            title: None,
            subtitle: None,
            cta_text: None,
            cta_link: None,
            cta_text_secondary: None,
            cta_link_secondary: None,
            translations: None,
        };
        let err = validate_upsert(&req).unwrap_err();
        let f = err.details.unwrap()["fields"].clone();
        assert_eq!(f["end_time"], "before_start");
    }
}


// ══════════════════ store 侧(E-MKT-01)══════════════════

/// MAP-MKT-001:published + 投放窗口谓词 + locale 翻译覆盖;不暴露 status/窗口字段
pub async fn store_list(
    db: &DatabaseConnection,
    position: Option<i64>,
    locale: &str,
) -> Result<Vec<Value>, CatalogError> {
    if let Some(p) = position {
        if !(1..=3).contains(&p) {
            return Err(field_err(vec![("position", "invalid_enum")]));
        }
    }
    let pos_filter = position.map(|p| format!(" AND position = {}", p)).unwrap_or_default();
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT {} FROM banner WHERE status = 2 AND (start_time IS NULL OR start_time <= NOW(3)) AND (end_time IS NULL OR end_time > NOW(3)){} ORDER BY sort ASC, id ASC",
                COLS, pos_filter
            ),
        ))
        .await
        .map_err(db_err)?;
    let ids: Vec<i64> = rows.iter().filter_map(|r| r.try_get::<u64>("", "id").ok().map(|v| v as i64)).collect();
    let mut trs: HashMap<i64, Value> = HashMap::new();
    if (locale == "es" || locale == "fr") && !ids.is_empty() {
        let id_list = ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
        let trows = db
            .query_all(Statement::from_string(
                DbBackend::MySql,
                format!(
                    "SELECT banner_id, image_url, title, subtitle, cta_text, cta_text_secondary FROM banner_translation WHERE locale = '{}' AND banner_id IN ({})",
                    locale, id_list
                ),
            ))
            .await
            .map_err(db_err)?;
        for t in trows {
            if let Some(bid) = t.try_get::<u64>("", "banner_id").ok().map(|v| v as i64) {
                trs.insert(
                    bid,
                    serde_json::json!({
                        "image_url": t.try_get::<String>("", "image_url").ok(),
                        "title": t.try_get::<String>("", "title").ok(),
                        "subtitle": t.try_get::<String>("", "subtitle").ok(),
                        "cta_text": t.try_get::<String>("", "cta_text").ok(),
                        "cta_text_secondary": t.try_get::<String>("", "cta_text_secondary").ok(),
                    }),
                );
            }
        }
    }
    let items = rows
        .iter()
        .map(|r| {
            let id = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            let pos: i64 = r.try_get("", "position").unwrap_or(1);
            let tr = trs.get(&id);
            let coalesce = |tr_v: Option<&Value>, db_v: &Option<String>| -> Value {
                match tr_v.and_then(|v| v.as_str()) {
                    Some(s) if !s.trim().is_empty() => Value::String(s.to_string()),
                    _ => db_v.clone().map(Value::String).unwrap_or(Value::Null),
                }
            };
            let image_url_db: Option<String> = r.try_get("", "image_url").ok();
            let title_db: Option<String> = r.try_get("", "title").ok();
            let subtitle_db: Option<String> = r.try_get("", "subtitle").ok();
            let cta_text_db: Option<String> = r.try_get("", "cta_text").ok();
            let cta_link_db: Option<String> = r.try_get("", "cta_link").ok();
            let cta_ts_db: Option<String> = r.try_get("", "cta_text_secondary").ok();
            let cta_ls_db: Option<String> = r.try_get("", "cta_link_secondary").ok();
            let supports_secondary = pos != 2;
            let name_db: Option<String> = r.try_get("", "name").ok();
            serde_json::json!({
                "id": id,
                "name": name_db,
                "image_url": coalesce(tr.map(|t| t.get("image_url")).unwrap_or(None), &image_url_db),
                "position": pos,
                "sort": r.try_get::<i64>("", "sort").unwrap_or(0),
                "title": coalesce(tr.map(|t| t.get("title")).unwrap_or(None), &title_db),
                "subtitle": coalesce(tr.map(|t| t.get("subtitle")).unwrap_or(None), &subtitle_db),
                "cta_text": coalesce(tr.map(|t| t.get("cta_text")).unwrap_or(None), &cta_text_db),
                "cta_link": cta_link_db,
                "cta_text_secondary": if supports_secondary { coalesce(tr.map(|t| t.get("cta_text_secondary")).unwrap_or(None), &cta_ts_db) } else { Value::Null },
                "cta_link_secondary": if supports_secondary { cta_ls_db.map(Value::String).unwrap_or(Value::Null) } else { Value::Null },
            })
        })
        .collect();
    Ok(items)
}
