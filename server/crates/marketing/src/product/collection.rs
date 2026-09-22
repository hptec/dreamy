//! collection 域:E-CAT-07 store 导航 + E-CAT-28~37 admin 分组/集合/商品挂载。
//! 对齐 CollectionAdminService / StoreCollectionService;错误码 404505/409506。

use std::collections::{HashMap, HashSet};

use sea_orm::{ConnectionTrait, TransactionTrait};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::service_category::{cat_err, CatalogError};

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[coll] db error:{e}");
    CatalogError::new(500501)
}

fn coll_not_found() -> CatalogError {
    CatalogError::new(404505)
}

/// 审计(best-effort;operation_log 在主库 dreamy_server,与业务库事务解耦——
/// Java 侧同库同事务的语义在跨库阶段无法保持,commit 后主写 + 失败仅告警)
async fn audit(
    db: &sea_orm::DatabaseConnection,
    operator_id: &str,
    action: &str,
    target: &str,
) {
    let oid: Option<i64> = operator_id.parse().ok();
    if let Err(e) = db
        .execute(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "INSERT INTO operation_log(operator_id, operator_name, action, target, ip, user_agent, changes, created_at, updated_at) VALUES (?,NULL,?,?,NULL,NULL,NULL,NOW(3),NOW(3))",
            [oid.into(), action.into(), target.into()],
        ))
        .await
    {
        tracing::warn!("[coll] audit best-effort failed:{e}");
    }
}

// ══════════════════ store 侧(E-CAT-07)══════════════════

/// StoreCollectionItem
#[derive(Debug, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct StoreCollectionItem {
    pub id: i64,
    pub name: String,
    pub product_count: i64,
    pub image_url: Option<String>,
}

/// StoreCollectionGroup
#[derive(Debug, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct StoreCollectionGroup {
    pub id: i64,
    pub name: String,
    pub description: Option<String>,
    pub collections: Vec<StoreCollectionItem>,
}

/// E-CAT-07 集合导航(enabled 按分组;published 口径 product_count;locale 翻译回退 EN)
pub async fn store_groups(
    db: &sea_orm::DatabaseConnection,
    group_id: Option<i64>,
    locale: &str,
) -> Result<Vec<StoreCollectionGroup>, CatalogError> {
    // 分组(过滤;不存在 → 空 items)
    let groups_sql = match group_id {
        Some(gid) => format!("SELECT id, name, description FROM collection_group WHERE id = {}", gid),
        None => "SELECT id, name, description FROM collection_group".to_string(),
    };
    let groups = db
        .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, groups_sql))
        .await
        .map_err(db_err)?;
    let mut group_rows: Vec<(i64, String, Option<String>)> = vec![];
    for g in &groups {
        group_rows.push((
            g.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
            g.try_get::<String>("", "name").unwrap_or_default(),
            g.try_get::<String>("", "description").ok(),
        ));
    }
    if group_rows.is_empty() {
        return Ok(vec![]);
    }
    // enabled 集合(限定分组)
    let gids = group_rows.iter().map(|g| g.0.to_string()).collect::<Vec<_>>().join(",");
    let coll_sql = format!(
        "SELECT id, collection_group_id, name FROM collection WHERE status = 1 AND collection_group_id IN ({}) ORDER BY id ASC",
        gids
    );
    let colls = db
        .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, coll_sql))
        .await
        .map_err(db_err)?;
    let mut collections: Vec<(i64, i64, String)> = vec![];
    for c in &colls {
        collections.push((
            c.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
            c.try_get::<i64>("", "collection_group_id").unwrap_or(0),
            c.try_get::<String>("", "name").unwrap_or_default(),
        ));
    }
    // product_count(published 口径)
    let mut counts: HashMap<i64, i64> = HashMap::new();
    if !collections.is_empty() {
        let cids = collections.iter().map(|c| c.0.to_string()).collect::<Vec<_>>().join(",");
        let sql = format!(
            "SELECT pc.collection_id AS cid, COUNT(*) AS n FROM product_collection pc JOIN product p ON p.id = pc.product_id WHERE p.status = 2 AND pc.collection_id IN ({}) GROUP BY pc.collection_id",
            cids
        );
        let rows = db
            .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
            .await
            .map_err(db_err)?;
        for r in rows {
            if let (Some(cid), Some(n)) = (
                r.try_get::<i64>("", "cid").ok(),
                r.try_get::<i64>("", "n").ok(),
            ) {
                counts.insert(cid, n);
            }
        }
    }
    // locale 翻译(group/collection)
    let group_names = translations_map(db, "collection_group_translation", "collection_group_id", "name",
        &group_rows.iter().map(|g| g.0).collect::<Vec<_>>(), locale).await?;
    let coll_names = translations_map(db, "collection_translation", "collection_id", "label",
        &collections.iter().map(|c| c.0).collect::<Vec<_>>(), locale).await?;
    // 集合封面图(排序最前的已上架商品主图;前 8 候选)
    let collection_images = resolve_collection_images(db, &collections.iter().map(|c| c.0).collect::<Vec<_>>()).await?;
    // 组装
    let mut by_group: HashMap<i64, Vec<StoreCollectionItem>> = HashMap::new();
    for (id, gid, name) in &collections {
        by_group.entry(*gid).or_default().push(StoreCollectionItem {
            id: *id,
            name: coll_names.get(id).cloned().unwrap_or_else(|| name.clone()),
            product_count: counts.get(id).copied().unwrap_or(0),
            image_url: collection_images.get(id).cloned(),
        });
    }
    Ok(group_rows
        .into_iter()
        .map(|(id, name, desc)| StoreCollectionGroup {
            id,
            name: group_names.get(&id).cloned().unwrap_or(name),
            description: desc,
            collections: by_group.remove(&id).unwrap_or_default(),
        })
        .collect())
}

/// 通用翻译映射(locale ∈ es/fr)
async fn translations_map(
    db: &sea_orm::DatabaseConnection,
    table: &str,
    id_col: &str,
    value_col: &str,
    ids: &[i64],
    locale: &str,
) -> Result<HashMap<i64, String>, CatalogError> {
    let mut out = HashMap::new();
    if (locale != "es" && locale != "fr") || ids.is_empty() {
        return Ok(out);
    }
    let id_list = ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
    let sql = format!(
        "SELECT {} AS pid, {} AS v FROM {} WHERE locale = '{}' AND {} IN ({})",
        id_col, value_col, table, locale, id_col, id_list
    );
    let rows = db
        .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
        .await
        .map_err(db_err)?;
    for r in rows {
        if let (Some(pid), Ok(v)) = (r.try_get::<i64>("", "pid").ok(), r.try_get::<String>("", "v")) {
            out.insert(pid, v);
        }
    }
    Ok(out)
}

/// 每个集合:排序最前的已上架商品主图(前 8 候选;gallery 优先)
async fn resolve_collection_images(
    db: &sea_orm::DatabaseConnection,
    collection_ids: &[i64],
) -> Result<HashMap<i64, String>, CatalogError> {
    if collection_ids.is_empty() {
        return Ok(HashMap::new());
    }
    let cids = collection_ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
    // 候选:每集合前 8 个 product_id(product_collection.id 序)
    let sql = format!(
        "SELECT collection_id AS cid, product_id AS pid, ROW_NUMBER() OVER (PARTITION BY collection_id ORDER BY id ASC) AS rn \
         FROM product_collection WHERE collection_id IN ({})",
        cids
    );
    let rows = db
        .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
        .await
        .map_err(db_err)?;
    let mut candidates: HashMap<i64, Vec<i64>> = HashMap::new();
    let mut all_pids: Vec<i64> = vec![];
    let mut seen: HashSet<i64> = HashSet::new();
    for r in rows {
        let rn: i64 = r.try_get("", "rn").unwrap_or(99);
        if rn > 8 {
            continue;
        }
        if let (Some(cid), Some(pid)) = (
            r.try_get::<i64>("", "cid").ok(),
            r.try_get::<i64>("", "pid").ok(),
        ) {
            candidates.entry(cid).or_default().push(pid);
            if seen.insert(pid) {
                all_pids.push(pid);
            }
        }
    }
    if all_pids.is_empty() {
        return Ok(HashMap::new());
    }
    // published 过滤 + 主图
    let pid_list = all_pids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
    let pub_sql = format!("SELECT id FROM product WHERE id IN ({}) AND status = 2", pid_list);
    let pub_rows = db
        .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, pub_sql))
        .await
        .map_err(db_err)?;
    let published: HashSet<i64> = pub_rows
        .iter()
        .filter_map(|r| r.try_get::<i64>("", "id").ok())
        .collect();
    let img_sql = format!(
        "SELECT product_id AS pid, url FROM product_image WHERE kind = 1 AND product_id IN ({}) ORDER BY sort ASC, id ASC",
        pid_list
    );
    let img_rows = db
        .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, img_sql))
        .await
        .map_err(db_err)?;
    let mut primary: HashMap<i64, String> = HashMap::new();
    for r in img_rows {
        if let (Some(pid), Ok(url)) = (r.try_get::<i64>("", "pid").ok(), r.try_get::<String>("", "url")) {
            primary.entry(pid).or_insert(url);
        }
    }
    let mut out: HashMap<i64, String> = HashMap::new();
    for (cid, pids) in candidates {
        for pid in pids {
            if published.contains(&pid) {
                if let Some(url) = primary.get(&pid) {
                    out.insert(cid, url.clone());
                    break;
                }
            }
        }
    }
    Ok(out)
}

// ══════════════════ admin 侧(E-CAT-28~37)══════════════════

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct GroupUpsert {
    pub name: Option<String>,
    pub description: Option<String>,
    pub translations: Option<Vec<TranslationIn>>,
}

#[derive(Debug, Deserialize, Clone, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct TranslationIn {
    pub locale: String,
    pub name: Option<String>,
    pub label: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct CollectionUpsert {
    pub collection_group_id: Option<i64>,
    pub name: Option<String>,
    pub status: Option<i64>,
    pub translations: Option<Vec<TranslationIn>>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct GroupDto {
    pub id: i64,
    pub name: String,
    pub description: Option<String>,
    pub collection_count: i64,
    pub translations: Vec<Value>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct CollectionDto {
    pub id: i64,
    pub collection_group_id: i64,
    pub name: String,
    pub status: i64,
    pub product_count: i64,
    pub fallback_cover_urls: Vec<String>,
    pub translations: Vec<Value>,
}

/// INSERT 后取自增 id(同事务 SELECT LAST_INSERT_ID();ExecResult.last_insert_id 在驱动上不可靠)
async fn inserted_id(tx: &sea_orm::DatabaseTransaction) -> Result<u64, CatalogError> {
    let r = tx
        .query_one(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            "SELECT LAST_INSERT_ID() AS id".to_string(),
        ))
        .await
        .map_err(db_err)?;
    r.and_then(|row| row.try_get::<u64>("", "id").ok())
        .ok_or_else(|| CatalogError::new(500501))
}

fn field_err(fields: Vec<(&'static str, &'static str)>) -> CatalogError {
    CatalogError::field_validation(&fields)
}

fn validate_group(req: &GroupUpsert) -> Result<(), CatalogError> {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let name = req.name.as_deref().map(str::trim).unwrap_or("");
    if name.is_empty() {
        fields.push(("name", "required"));
    } else if name.len() > 64 {
        fields.push(("name", "too_long"));
    }
    if req.description.as_deref().is_some_and(|d| d.len() > 255) {
        fields.push(("description", "too_long"));
    }
    if let Some(ts) = &req.translations {
        let mut seen: HashSet<&str> = HashSet::new();
        for t in ts {
            if t.locale != "es" && t.locale != "fr" || !seen.insert(t.locale.as_str()) {
                fields.push(("translations", "invalid_locale"));
                break;
            }
        }
    }
    if fields.is_empty() {
        Ok(())
    } else {
        Err(field_err(fields))
    }
}

async fn validate_collection(db: &sea_orm::DatabaseConnection, req: &CollectionUpsert) -> Result<(), CatalogError> {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let Some(gid) = req.collection_group_id else {
        return Err(field_err(vec![("collection_group_id", "required")]));
    };
    let found = db
        .query_one(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "SELECT id FROM collection_group WHERE id = ?",
            [gid.into()],
        ))
        .await
        .map_err(db_err)?;
    if found.is_none() {
        return Err(coll_not_found());
    }
    let name = req.name.as_deref().map(str::trim).unwrap_or("");
    if name.is_empty() {
        fields.push(("name", "required"));
    } else if name.len() > 64 {
        fields.push(("name", "too_long"));
    }
    match req.status {
        None => fields.push(("status", "required")),
        Some(s) if s != 1 && s != 2 => fields.push(("status", "invalid_enum")),
        _ => {}
    }
    if let Some(ts) = &req.translations {
        let mut seen: HashSet<&str> = HashSet::new();
        for t in ts {
            if t.locale != "es" && t.locale != "fr" || !seen.insert(t.locale.as_str()) {
                fields.push(("translations", "invalid_locale"));
                break;
            }
        }
    }
    if fields.is_empty() {
        Ok(())
    } else {
        Err(field_err(fields))
    }
}

/// E-CAT-28~30 分组 CRUD
pub async fn admin_list_groups(db: &sea_orm::DatabaseConnection) -> Result<Vec<GroupDto>, CatalogError> {
    let groups = db
        .query_all(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            "SELECT g.id, g.name, g.description, (SELECT COUNT(*) FROM collection c WHERE c.collection_group_id = g.id) AS cnt FROM collection_group g ORDER BY g.id",
        ))
        .await
        .map_err(db_err)?;
    let mut out = vec![];
    for g in groups {
        let id = g.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
        out.push(GroupDto {
            id,
            name: g.try_get::<String>("", "name").unwrap_or_default(),
            description: g.try_get::<String>("", "description").ok(),
            collection_count: g.try_get::<i64>("", "cnt").unwrap_or(0),
            translations: load_group_translations(db, id).await?,
        });
    }
    Ok(out)
}

async fn load_group_translations(db: &sea_orm::DatabaseConnection, gid: i64) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!(
                "SELECT locale, name FROM collection_group_translation WHERE collection_group_id = {}",
                gid
            ),
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            serde_json::json!({
                "locale": r.try_get::<String>("", "locale").unwrap_or_default(),
                "name": r.try_get::<String>("", "name").ok(),
            })
        })
        .collect())
}

pub async fn admin_create_group(
    db: &sea_orm::DatabaseConnection,
    audit_db: &sea_orm::DatabaseConnection,
    req: GroupUpsert,
    operator: &str,
) -> Result<GroupDto, CatalogError> {
    validate_group(&req)?;
    let name = req.name.as_deref().map(str::trim).unwrap_or_default().to_string();
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        "INSERT INTO collection_group(name, description, created_at, updated_at) VALUES (?,?,NOW(3),NOW(3))",
        [name.clone().into(), req.description.clone().into()],
    ))
    .await
    .map_err(db_err)?;
    let id = inserted_id(&tx).await?;
    replace_group_translations(&tx, id, req.translations.as_ref()).await?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "创建集合分组", &name).await;
    Ok(GroupDto { id: id as i64, name, description: req.description, collection_count: 0, translations: vec![] })
}

async fn replace_group_translations(
    tx: &sea_orm::DatabaseTransaction,
    gid: u64,
    translations: Option<&Vec<TranslationIn>>,
) -> Result<(), CatalogError> {
    tx.execute(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("DELETE FROM collection_group_translation WHERE collection_group_id = {}", gid),
    ))
    .await
    .map_err(db_err)?;
    if let Some(ts) = translations {
        for t in ts {
            tx.execute(sea_orm::Statement::from_sql_and_values(
                sea_orm::DatabaseBackend::MySql,
                "INSERT INTO collection_group_translation(collection_group_id, locale, name, created_at, updated_at) VALUES (?,?,?,NOW(3),NOW(3))",
                [(gid as i64).into(), t.locale.clone().into(), t.name.clone().or_else(|| t.label.clone()).into()],
            ))
            .await
            .map_err(db_err)?;
        }
    }
    Ok(())
}

pub async fn admin_update_group(
    db: &sea_orm::DatabaseConnection,
    audit_db: &sea_orm::DatabaseConnection,
    id: i64,
    req: GroupUpsert,
    operator: &str,
) -> Result<GroupDto, CatalogError> {
    let existing = db
        .query_one(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "SELECT id, name, description FROM collection_group WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(coll_not_found())?;
    validate_group(&req)?;
    let name = req.name.as_deref().map(str::trim).unwrap_or_default().to_string();
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        "UPDATE collection_group SET name = ?, description = ?, updated_at = NOW(3) WHERE id = ?",
        [name.clone().into(), req.description.clone().into(), id.into()],
    ))
    .await
    .map_err(db_err)?;
    replace_group_translations(&tx, id as u64, req.translations.as_ref()).await?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "编辑集合分组", &name).await;
    let cnt = db
        .query_one(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!("SELECT COUNT(*) AS n FROM collection WHERE collection_group_id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<i64>("", "n").ok())
        .unwrap_or(0);
    let _ = existing;
    Ok(GroupDto { id, name, description: req.description, collection_count: cnt, translations: vec![] })
}

pub async fn admin_delete_group(db: &sea_orm::DatabaseConnection, audit_db: &sea_orm::DatabaseConnection, id: i64, operator: &str) -> Result<(), CatalogError> {
    let existing = db
        .query_one(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "SELECT id, name FROM collection_group WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(coll_not_found())?;
    let cnt = db
        .query_one(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!("SELECT COUNT(*) AS n FROM collection WHERE collection_group_id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<i64>("", "n").ok())
        .unwrap_or(0);
    if cnt > 0 {
        return Err(CatalogError::new(409506).with_detail("collection_count", Value::from(cnt)));
    }
    let name: String = existing.try_get("", "name").unwrap_or_default();
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("DELETE FROM collection_group WHERE id = {}", id),
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "删除集合分组", &name).await;
    Ok(())
}

/// E-CAT-31~34 集合 CRUD(product_count 全量口径 + fallback_cover_urls 前4)
pub async fn admin_list_collections(
    db: &sea_orm::DatabaseConnection,
    group_id: Option<i64>,
) -> Result<Vec<CollectionDto>, CatalogError> {
    let sql = match group_id {
        Some(g) => format!("SELECT id, collection_group_id, name, status FROM collection WHERE collection_group_id = {} ORDER BY id", g),
        None => "SELECT id, collection_group_id, name, status FROM collection ORDER BY id".to_string(),
    };
    let rows = db
        .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
        .await
        .map_err(db_err)?;
    let mut collections: Vec<(i64, i64, String, i64)> = vec![];
    for r in &rows {
        collections.push((
            r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
            r.try_get::<i64>("", "collection_group_id").unwrap_or(0),
            r.try_get::<String>("", "name").unwrap_or_default(),
            r.try_get::<i64>("", "status").unwrap_or(1),
        ));
    }
    let mut counts = HashMap::new();
    if !collections.is_empty() {
        let cids = collections.iter().map(|c| c.0.to_string()).collect::<Vec<_>>().join(",");
        let sql = format!(
            "SELECT collection_id AS cid, COUNT(*) AS n FROM product_collection WHERE collection_id IN ({}) GROUP BY collection_id",
            cids
        );
        if let Ok(crows) = db
            .query_all(sea_orm::Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
            .await
        {
            for r in crows {
                if let (Some(cid), Some(n)) = (r.try_get::<i64>("", "cid").ok(), r.try_get::<i64>("", "n").ok()) {
                    counts.insert(cid, n);
                }
            }
        }
    }
    let mut out = vec![];
    for (id, gid, name, status) in &collections {
        out.push(CollectionDto {
            id: *id,
            collection_group_id: *gid,
            name: name.clone(),
            status: *status,
            product_count: counts.get(id).copied().unwrap_or(0),
            fallback_cover_urls: fallback_covers(db, *id, 4).await?,
            translations: load_collection_translations(db, *id).await?,
        });
    }
    Ok(out)
}

async fn load_collection_translations(db: &sea_orm::DatabaseConnection, cid: i64) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!("SELECT locale, label FROM collection_translation WHERE collection_id = {}", cid),
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            serde_json::json!({
                "locale": r.try_get::<String>("", "locale").unwrap_or_default(),
                "label": r.try_get::<String>("", "label").ok(),
            })
        })
        .collect())
}

/// 前 N 个商品主图(gallery 按 product_collection.id 序)
async fn fallback_covers(db: &sea_orm::DatabaseConnection, cid: i64, n: usize) -> Result<Vec<String>, CatalogError> {
    let rows = db
        .query_all(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!(
                "SELECT product_id AS pid FROM product_collection WHERE collection_id = {} ORDER BY id ASC LIMIT {}",
                cid, n
            ),
        ))
        .await
        .map_err(db_err)?;
    let pids: Vec<i64> = rows
        .iter()
        .filter_map(|r| r.try_get::<i64>("", "pid").ok())
        .collect();
    if pids.is_empty() {
        return Ok(vec![]);
    }
    let pid_list = pids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
    let img_rows = db
        .query_all(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!(
                "SELECT product_id AS pid, url FROM product_image WHERE kind = 1 AND product_id IN ({}) ORDER BY sort ASC, id ASC",
                pid_list
            ),
        ))
        .await
        .map_err(db_err)?;
    let mut primary: HashMap<i64, String> = HashMap::new();
    for r in img_rows {
        if let (Some(pid), Ok(url)) = (r.try_get::<i64>("", "pid").ok(), r.try_get::<String>("", "url")) {
            primary.entry(pid).or_insert(url);
        }
    }
    Ok(pids.iter().filter_map(|pid| primary.get(pid).cloned()).collect())
}

pub async fn admin_create_collection(
    db: &sea_orm::DatabaseConnection,
    audit_db: &sea_orm::DatabaseConnection,
    req: CollectionUpsert,
    operator: &str,
) -> Result<CollectionDto, CatalogError> {
    validate_collection(db, &req).await?;
    let name = req.name.as_deref().map(str::trim).unwrap_or_default().to_string();
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        "INSERT INTO collection(collection_group_id, name, status, created_at, updated_at) VALUES (?,?,?,NOW(3),NOW(3))",
        [req.collection_group_id.into(), name.clone().into(), req.status.unwrap_or(1).into()],
    ))
    .await
    .map_err(db_err)?;
    let id = inserted_id(&tx).await?;
    replace_collection_translations(&tx, id, req.translations.as_ref()).await?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "创建集合", &name).await;
    Ok(CollectionDto {
        id: id as i64,
        collection_group_id: req.collection_group_id.unwrap_or(0),
        name,
        status: req.status.unwrap_or(1),
        product_count: 0,
        fallback_cover_urls: fallback_covers(db, id as i64, 4).await?,
        translations: vec![],
    })
}

async fn replace_collection_translations(
    tx: &sea_orm::DatabaseTransaction,
    cid: u64,
    translations: Option<&Vec<TranslationIn>>,
) -> Result<(), CatalogError> {
    tx.execute(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("DELETE FROM collection_translation WHERE collection_id = {}", cid),
    ))
    .await
    .map_err(db_err)?;
    if let Some(ts) = translations {
        for t in ts {
            tx.execute(sea_orm::Statement::from_sql_and_values(
                sea_orm::DatabaseBackend::MySql,
                "INSERT INTO collection_translation(collection_id, locale, label, created_at, updated_at) VALUES (?,?,?,NOW(3),NOW(3))",
                [(cid as i64).into(), t.locale.clone().into(), t.label.clone().or_else(|| t.name.clone()).into()],
            ))
            .await
            .map_err(db_err)?;
        }
    }
    Ok(())
}

pub async fn admin_update_collection(
    db: &sea_orm::DatabaseConnection,
    audit_db: &sea_orm::DatabaseConnection,
    id: i64,
    req: CollectionUpsert,
    operator: &str,
) -> Result<CollectionDto, CatalogError> {
    let existing = db
        .query_one(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "SELECT id, name FROM collection WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(coll_not_found())?;
    validate_collection(db, &req).await?;
    let name = req.name.as_deref().map(str::trim).unwrap_or_default().to_string();
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        "UPDATE collection SET collection_group_id = ?, name = ?, status = ?, updated_at = NOW(3) WHERE id = ?",
        [req.collection_group_id.into(), name.clone().into(), req.status.unwrap_or(1).into(), id.into()],
    ))
    .await
    .map_err(db_err)?;
    replace_collection_translations(&tx, id as u64, req.translations.as_ref()).await?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "编辑集合", &name).await;
    let _ = existing;
    let cnt = db
        .query_one(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!("SELECT COUNT(*) AS n FROM product_collection WHERE collection_id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<i64>("", "n").ok())
        .unwrap_or(0);
    Ok(CollectionDto {
        id,
        collection_group_id: req.collection_group_id.unwrap_or(0),
        name,
        status: req.status.unwrap_or(1),
        product_count: cnt,
        fallback_cover_urls: fallback_covers(db, id, 4).await?,
        translations: vec![],
    })
}

pub async fn admin_delete_collection(db: &sea_orm::DatabaseConnection, audit_db: &sea_orm::DatabaseConnection, id: i64, operator: &str) -> Result<(), CatalogError> {
    let existing = db
        .query_one(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "SELECT id, name FROM collection WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(coll_not_found())?;
    let name: String = existing.try_get("", "name").unwrap_or_default();
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("DELETE FROM product_collection WHERE collection_id = {}", id),
    ))
    .await
    .map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("DELETE FROM collection_translation WHERE collection_id = {}", id),
    ))
    .await
    .map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("DELETE FROM collection WHERE id = {}", id),
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "删除集合", &name).await;
    Ok(())
}

/// E-CAT-35 集合商品列表
pub async fn admin_list_collection_products(
    db: &sea_orm::DatabaseConnection,
    collection_id: i64,
) -> Result<Vec<Value>, CatalogError> {
    db.query_one(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("SELECT id FROM collection WHERE id = {}", collection_id),
    ))
    .await
    .map_err(db_err)?
    .ok_or(coll_not_found())?;
    let rows = db
        .query_all(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!(
                "SELECT pc.product_id AS pid, pc.sort, p.name, p.slug, p.status \
                 FROM product_collection pc JOIN product p ON p.id = pc.product_id \
                 WHERE pc.collection_id = {} ORDER BY pc.id ASC",
                collection_id
            ),
        ))
        .await
        .map_err(db_err)?;
    if rows.is_empty() {
        return Ok(vec![]);
    }
    let pids: Vec<i64> = rows
        .iter()
        .filter_map(|r| r.try_get::<i64>("", "pid").ok())
        .collect();
    if pids.is_empty() {
        return Ok(vec![]);
    }
    let pid_list = pids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
    let img_rows = db
        .query_all(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!(
                "SELECT product_id AS pid, url FROM product_image WHERE kind = 1 AND product_id IN ({}) ORDER BY sort ASC, id ASC",
                pid_list
            ),
        ))
        .await
        .map_err(db_err)?;
    let mut primary: HashMap<i64, String> = HashMap::new();
    for r in img_rows {
        if let (Some(pid), Ok(url)) = (r.try_get::<i64>("", "pid").ok(), r.try_get::<String>("", "url")) {
            primary.entry(pid).or_insert(url);
        }
    }
    Ok(rows
        .iter()
        .map(|r| {
            let pid = r.try_get::<i64>("", "pid").unwrap_or(0);
            serde_json::json!({
                "product_id": pid,
                "name": r.try_get::<String>("", "name").unwrap_or_default(),
                "slug": r.try_get::<String>("", "slug").unwrap_or_default(),
                "status": r.try_get::<i64>("", "status").ok(),
                "image_url": primary.get(&pid),
                "sort": r.try_get::<i64>("", "sort").unwrap_or(0),
            })
        })
        .collect())
}

/// E-CAT-36 全量覆盖集合商品(按入参顺序写 sort;product 不存在 → 404501 detail product_id)
pub async fn admin_replace_collection_products(
    db: &sea_orm::DatabaseConnection,
    audit_db: &sea_orm::DatabaseConnection,
    collection_id: i64,
    product_ids: Vec<i64>,
    operator: &str,
) -> Result<(), CatalogError> {
    let existing = db
        .query_one(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!("SELECT id, name FROM collection WHERE id = {}", collection_id),
        ))
        .await
        .map_err(db_err)?
        .ok_or(coll_not_found())?;
    let name: String = existing.try_get("", "name").unwrap_or_default();
    if !product_ids.is_empty() {
        let pid_list = product_ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
        let rows = db
            .query_all(sea_orm::Statement::from_string(
                sea_orm::DatabaseBackend::MySql,
                format!("SELECT CAST(id AS SIGNED) AS pid FROM product WHERE id IN ({})", pid_list),
            ))
            .await
            .map_err(db_err)?;
        let found: HashSet<i64> = rows
            .iter()
            .filter_map(|r| r.try_get::<i64>("", "pid").ok())
            .collect();
        for pid in &product_ids {
            if !found.contains(pid) {
                return Err(CatalogError::new(404501).with_detail("product_id", Value::from(*pid)));
            }
        }
    }
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("DELETE FROM product_collection WHERE collection_id = {}", collection_id),
    ))
    .await
    .map_err(db_err)?;
    for (i, pid) in product_ids.iter().enumerate() {
        tx.execute(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "INSERT INTO product_collection(product_id, collection_id, sort, created_at, updated_at) VALUES (?,?,?,NOW(3),NOW(3))",
            [(*pid).into(), collection_id.into(), (i as i64).into()],
        ))
        .await
        .map_err(db_err)?;
    }
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "编辑集合商品", &name).await;
    Ok(())
}

/// E-CAT-37 单条摘除(affected=0 → 404505)
pub async fn admin_remove_collection_product(
    db: &sea_orm::DatabaseConnection,
    collection_id: i64,
    product_id: i64,
) -> Result<(), CatalogError> {
    db.query_one(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("SELECT id FROM collection WHERE id = {}", collection_id),
    ))
    .await
    .map_err(db_err)?
    .ok_or(coll_not_found())?;
    let res = db
        .execute(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "DELETE FROM product_collection WHERE collection_id = ? AND product_id = ?",
            [collection_id.into(), product_id.into()],
        ))
        .await
        .map_err(db_err)?;
    if res.rows_affected() == 0 {
        return Err(coll_not_found());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validate_group_rejects_bad_locale() {
        let req = GroupUpsert {
            name: Some("G".into()),
            description: None,
            translations: Some(vec![TranslationIn { locale: "de".into(), name: None, label: None }]),
        };
        assert!(validate_group(&req).is_err());
    }

    #[test]
    fn validate_group_ok() {
        let req = GroupUpsert {
            name: Some("Theme".into()),
            description: None,
            translations: Some(vec![TranslationIn { locale: "es".into(), name: Some("T".into()), label: None }]),
        };
        assert!(validate_group(&req).is_ok());
    }
}
