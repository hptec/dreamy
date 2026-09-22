//! attribute 域:E-CAT-19~26(admin 属性集/属性字典 CRUD + 守卫)。
//! 对齐 AttributeDefService / AttributeSetService;错误码 404504/404505/409507/409503。

use std::collections::{HashMap, HashSet};

use sea_orm::{ConnectionTrait, TransactionTrait};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::service_category::{cat_err, CatalogError};

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[attr] db error:{e}");
    CatalogError::new(500501)
}

async fn audit(
    audit_db: &sea_orm::DatabaseConnection,
    operator_id: &str,
    action: &str,
    target: &str,
) {
    let oid: Option<i64> = operator_id.parse().ok();
    if let Err(e) = audit_db
        .execute(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "INSERT INTO operation_log(operator_id, operator_name, action, target, ip, user_agent, changes, created_at, updated_at) VALUES (?,NULL,?,?,NULL,NULL,NULL,NOW(3),NOW(3))",
            [oid.into(), action.into(), target.into()],
        ))
        .await
    {
        tracing::warn!("[attr] audit best-effort failed:{e}");
    }
}

async fn inserted_id(tx: &sea_orm::DatabaseTransaction) -> Result<u64, CatalogError> {
    tx.query_one(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        "SELECT LAST_INSERT_ID() AS id".to_string(),
    ))
    .await
    .map_err(db_err)?
    .and_then(|row| row.try_get::<u64>("", "id").ok())
    .ok_or_else(|| CatalogError::new(500501))
}

// ══════════════════ DTO ══════════════════

#[derive(Debug, Deserialize, Clone, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct DefTranslationIn {
    pub locale: String,
    pub label: Option<String>,
    pub options: Option<Value>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct DefUpsert {
    pub key: Option<String>,
    pub label: Option<String>,
    #[serde(rename = "type")]
    pub def_type: Option<i64>,
    pub options: Option<Vec<String>>,
    pub translations: Option<Vec<DefTranslationIn>>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct DefDto {
    pub id: i64,
    pub key: String,
    pub label: String,
    #[serde(rename = "type")]
    pub def_type: Option<i64>,
    pub options: Option<Vec<String>>,
    pub translations: Vec<Value>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct SetItemIn {
    pub attribute_id: Option<i64>,
    pub visibility: Option<i64>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct SetUpsert {
    pub label: Option<String>,
    pub items: Option<Vec<SetItemIn>>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct SetDto {
    pub id: i64,
    pub label: String,
    pub items: Vec<Value>,
    pub category_count: i64,
}

// ══════════════════ 属性字典(E-CAT-23~26)══════════════════

/// key pattern ^[a-z][a-z0-9_]*$
fn key_valid(k: &str) -> bool {
    let mut chars = k.chars();
    match chars.next() {
        Some(c) if c.is_ascii_lowercase() => {}
        _ => return false,
    }
    chars.all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_')
}

/// options 三态规则:1/2 必填非空去重;3/4 禁止
fn validate_options(def_type: i64, options: &Option<Vec<String>>, fields: &mut Vec<(&'static str, &'static str)>) {
    if def_type == 1 || def_type == 2 {
        match options {
            None => fields.push(("options", "required")),
            Some(o) if o.is_empty() => fields.push(("options", "required")),
            Some(o) if {
                let s: HashSet<&String> = o.iter().collect();
                s.len() != o.len()
            } =>
            {
                fields.push(("options", "duplicated"))
            }
            _ => {}
        }
    } else if options.as_ref().is_some_and(|o| !o.is_empty()) {
        fields.push(("options", "not_allowed"));
    }
}

fn validate_translations(
    translations: &Option<Vec<DefTranslationIn>>,
    options: &Option<Vec<String>>,
    fields: &mut Vec<(&'static str, &'static str)>,
) {
    if let Some(ts) = translations {
        let mut seen: HashSet<&str> = HashSet::new();
        for t in ts {
            if t.locale != "es" && t.locale != "fr" || !seen.insert(t.locale.as_str()) {
                fields.push(("translations", "invalid_locale"));
                break;
            }
            if let Some(Value::Array(t_opts)) = &t.options {
                let main_size = options.as_ref().map(|o| o.len()).unwrap_or(0);
                if t_opts.len() != main_size {
                    fields.push(("translations", "options_length_mismatch"));
                    break;
                }
            }
        }
    }
}

async fn def_exists(db: &sea_orm::DatabaseConnection, key: &str) -> Result<bool, CatalogError> {
    let rows = db
        .query_all(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "SELECT id FROM attribute_def WHERE `key` = ?",
            [key.into()],
        ))
        .await
        .map_err(db_err)?;
    Ok(!rows.is_empty())
}

/// E-CAT-23 属性字典列表(含三语 translations)
pub async fn def_list(db: &sea_orm::DatabaseConnection) -> Result<Vec<DefDto>, CatalogError> {
    let rows = db
        .query_all(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            "SELECT id, `key` AS k, label, type, options FROM attribute_def ORDER BY id ASC",
        ))
        .await
        .map_err(db_err)?;
    let mut out = vec![];
    for r in &rows {
        let id = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
        out.push(DefDto {
            id,
            key: r.try_get::<String>("", "k").unwrap_or_default(),
            label: r.try_get::<String>("", "label").unwrap_or_default(),
            def_type: r.try_get::<i64>("", "type").ok(),
            options: r.try_get::<Value>("", "options").ok().and_then(|v| {
                serde_json::from_value::<Vec<String>>(v).ok()
            }),
            translations: load_def_translations(db, id).await?,
        });
    }
    Ok(out)
}

async fn load_def_translations(db: &sea_orm::DatabaseConnection, def_id: i64) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!(
                "SELECT locale, label, options FROM attribute_def_translation WHERE attribute_def_id = {}",
                def_id
            ),
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            serde_json::json!({
                "locale": r.try_get::<String>("", "locale").unwrap_or_default(),
                "label": r.try_get::<String>("", "label").ok(),
                "options": r.try_get::<Value>("", "options").ok(),
            })
        })
        .collect())
}

/// E-CAT-24 新增属性定义(TX-CAT-012)
pub async fn def_create(
    db: &sea_orm::DatabaseConnection,
    audit_db: &sea_orm::DatabaseConnection,
    req: DefUpsert,
    operator: &str,
) -> Result<DefDto, CatalogError> {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let key = req.key.as_deref().map(str::trim).unwrap_or("");
    if key.is_empty() {
        fields.push(("key", "required"));
    } else if key.len() > 64 || !key_valid(key) {
        fields.push(("key", "pattern"));
    } else if def_exists(db, key).await? {
        fields.push(("key", "exists"));
    }
    let label = req.label.as_deref().map(str::trim).unwrap_or("");
    if label.is_empty() {
        fields.push(("label", "required"));
    } else if label.len() > 64 {
        fields.push(("label", "too_long"));
    }
    let Some(def_type) = req.def_type else {
        fields.push(("type", "invalid_enum"));
        return Err(CatalogError::field_validation(&fields));
    };
    if !(1..=4).contains(&def_type) {
        fields.push(("type", "invalid_enum"));
        return Err(CatalogError::field_validation(&fields));
    }
    validate_options(def_type, &req.options, &mut fields);
    validate_translations(&req.translations, &req.options, &mut fields);
    if !fields.is_empty() {
        return Err(CatalogError::field_validation(&fields));
    }
    let options_json: Option<Value> = if def_type == 1 || def_type == 2 {
        req.options.as_ref().map(|o| serde_json::json!(o))
    } else {
        None
    };
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        "INSERT INTO attribute_def(`key`, label, type, options, created_at, updated_at) VALUES (?,?,?,?,NOW(3),NOW(3))",
        [key.into(), label.into(), def_type.into(), options_json.clone().map(|v| v.to_string()).into()],
    ))
    .await
    .map_err(db_err)?;
    let id = inserted_id(&tx).await?;
    replace_def_translations(&tx, id, req.translations.as_ref()).await?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "创建属性定义", label).await;
    Ok(DefDto {
        id: id as i64,
        key: key.to_string(),
        label: label.to_string(),
        def_type: Some(def_type),
        options: req.options,
        translations: vec![],
    })
}

async fn replace_def_translations(
    tx: &sea_orm::DatabaseTransaction,
    def_id: u64,
    translations: Option<&Vec<DefTranslationIn>>,
) -> Result<(), CatalogError> {
    tx.execute(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("DELETE FROM attribute_def_translation WHERE attribute_def_id = {}", def_id),
    ))
    .await
    .map_err(db_err)?;
    if let Some(ts) = translations {
        for t in ts {
            tx.execute(sea_orm::Statement::from_sql_and_values(
                sea_orm::DatabaseBackend::MySql,
                "INSERT INTO attribute_def_translation(attribute_def_id, locale, label, options, created_at, updated_at) VALUES (?,?,?,?,NOW(3),NOW(3))",
                [
                    (def_id as i64).into(),
                    t.locale.clone().into(),
                    t.label.clone().into(),
                    t.options.clone().map(|v| v.to_string()).into(),
                ],
            ))
            .await
            .map_err(db_err)?;
        }
    }
    Ok(())
}

/// E-CAT-25 编辑(key/type 不可变;options 收缩守卫 409507)
pub async fn def_update(
    db: &sea_orm::DatabaseConnection,
    audit_db: &sea_orm::DatabaseConnection,
    id: i64,
    req: DefUpsert,
    operator: &str,
) -> Result<DefDto, CatalogError> {
    let existing = db
        .query_one(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "SELECT id, `key` AS k, label, type, options FROM attribute_def WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(CatalogError::new(404504))?;
    let existing_key: String = existing.try_get("", "k").unwrap_or_default();
    let existing_label: String = existing.try_get("", "label").unwrap_or_default();
    let existing_type: i64 = existing.try_get("", "type").unwrap_or(3);
    let existing_options: Option<Vec<String>> = existing
        .try_get::<Value>("", "options")
        .ok()
        .and_then(|v| serde_json::from_value(v).ok());
    if let Some(k) = &req.key {
        if k.trim() != existing_key {
            return Err(CatalogError::field_validation(&[("key", "immutable")]));
        }
    }
    if let Some(t) = req.def_type {
        if t != existing_type {
            return Err(CatalogError::field_validation(&[("type", "immutable")]));
        }
    }
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let label = req.label.as_deref().map(str::trim).unwrap_or("");
    if label.is_empty() {
        fields.push(("label", "required"));
    } else if label.len() > 64 {
        fields.push(("label", "too_long"));
    }
    validate_options(existing_type, &req.options, &mut fields);
    validate_translations(&req.translations, &req.options, &mut fields);
    if !fields.is_empty() {
        return Err(CatalogError::field_validation(&fields));
    }
    // options 收缩守卫:被商品 EAV 引用的 option 不允许移除 → 409507
    if existing_type == 1 || existing_type == 2 {
        if let Some(old_opts) = &existing_options {
            let removed: Vec<&String> = old_opts
                .iter()
                .filter(|o| req.options.as_ref().map(|n| !n.contains(o)).unwrap_or(true))
                .collect();
            if !removed.is_empty() {
                let placeholders = removed.iter().map(|_| "?").collect::<Vec<_>>().join(", ");
                let mut args: Vec<sea_orm::Value> = vec![id.into()];
                for o in &removed {
                    args.push((*o).clone().into());
                }
                let rows = db
                    .query_all(sea_orm::Statement::from_sql_and_values(
                        sea_orm::DatabaseBackend::MySql,
                        format!(
                            "SELECT COUNT(*) AS n FROM product_attribute_value WHERE attribute_id = ? AND `value` IN ({})",
                            placeholders
                        ),
                        args,
                    ))
                    .await
                    .map_err(db_err)?;
                let value_count = rows
                    .first()
                    .and_then(|r| r.try_get::<i64>("", "n").ok())
                    .unwrap_or(0);
                if value_count > 0 {
                    return Err(CatalogError::new(409507)
                        .with_detail("removed_options", serde_json::json!(removed))
                        .with_detail("product_value_count", Value::from(value_count)));
                }
            }
        }
    }
    let options_json: Option<Value> = if existing_type == 1 || existing_type == 2 {
        req.options.as_ref().map(|o| serde_json::json!(o))
    } else {
        None
    };
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        "UPDATE attribute_def SET label = ?, options = ?, updated_at = NOW(3) WHERE id = ?",
        [label.into(), options_json.map(|v| v.to_string()).into(), id.into()],
    ))
    .await
    .map_err(db_err)?;
    replace_def_translations(&tx, id as u64, req.translations.as_ref()).await?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "编辑属性定义", &existing_label).await;
    Ok(DefDto {
        id,
        key: existing_key,
        label: label.to_string(),
        def_type: Some(existing_type),
        options: req.options,
        translations: vec![],
    })
}

/// E-CAT-26 删除(守卫:属性集引用/商品 EAV 引用 → 409507)
pub async fn def_delete(
    db: &sea_orm::DatabaseConnection,
    audit_db: &sea_orm::DatabaseConnection,
    id: i64,
    operator: &str,
) -> Result<(), CatalogError> {
    let existing = db
        .query_one(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "SELECT id, label FROM attribute_def WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(CatalogError::new(404504))?;
    let label: String = existing.try_get("", "label").unwrap_or_default();
    let set_usage = db
        .query_one(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!("SELECT COUNT(*) AS n FROM attribute_set_item WHERE attribute_id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<i64>("", "n").ok())
        .unwrap_or(0);
    let value_count = db
        .query_one(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!("SELECT COUNT(*) AS n FROM product_attribute_value WHERE attribute_id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<i64>("", "n").ok())
        .unwrap_or(0);
    if set_usage > 0 || value_count > 0 {
        let mut err = CatalogError::new(409507);
        if set_usage > 0 {
            err = err.with_detail("attribute_set_count", Value::from(set_usage));
        }
        if value_count > 0 {
            err = err.with_detail("product_value_count", Value::from(value_count));
        }
        return Err(err);
    }
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("DELETE FROM attribute_def_translation WHERE attribute_def_id = {}", id),
    ))
    .await
    .map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("DELETE FROM attribute_def WHERE id = {}", id),
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "删除属性定义", &label).await;
    Ok(())
}

// ══════════════════ 属性集(E-CAT-19~22)══════════════════

/// E-CAT-19 列表(矩阵 + category_count 派生)
pub async fn set_list(db: &sea_orm::DatabaseConnection) -> Result<Vec<SetDto>, CatalogError> {
    let sets = db
        .query_all(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            "SELECT id, label FROM attribute_set ORDER BY id ASC",
        ))
        .await
        .map_err(db_err)?;
    let mut out = vec![];
    for s in &sets {
        let id = s.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
        let items = load_set_items(db, id).await?;
        let category_count = db
            .query_one(sea_orm::Statement::from_string(
                sea_orm::DatabaseBackend::MySql,
                format!("SELECT COUNT(*) AS n FROM category WHERE attribute_set_id = {}", id),
            ))
            .await
            .map_err(db_err)?
            .and_then(|r| r.try_get::<i64>("", "n").ok())
            .unwrap_or(0);
        out.push(SetDto {
            id,
            label: s.try_get::<String>("", "label").unwrap_or_default(),
            items,
            category_count,
        });
    }
    Ok(out)
}

async fn load_set_items(db: &sea_orm::DatabaseConnection, set_id: i64) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!(
                "SELECT attribute_id, visibility FROM attribute_set_item WHERE attribute_set_id = {} ORDER BY id ASC",
                set_id
            ),
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            serde_json::json!({
                "attribute_id": r.try_get::<i64>("", "attribute_id").unwrap_or(0),
                "visibility": r.try_get::<i64>("", "visibility").ok(),
            })
        })
        .collect())
}

async fn validate_set(db: &sea_orm::DatabaseConnection, req: &SetUpsert) -> Result<Vec<(i64, i8)>, CatalogError> {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let label = req.label.as_deref().map(str::trim).unwrap_or("");
    if label.is_empty() {
        fields.push(("label", "required"));
    } else if label.len() > 64 {
        fields.push(("label", "too_long"));
    }
    let Some(items) = &req.items else {
        return Err(CatalogError::field_validation(&[("items", "required")]));
    };
    let mut rows: Vec<(i64, i8)> = vec![];
    let mut seen: HashSet<i64> = HashSet::new();
    for item in items {
        let Some(aid) = item.attribute_id else {
            fields.push(("items", "attribute_not_exists"));
            continue;
        };
        if !seen.insert(aid) {
            fields.push(("items", "duplicated"));
            continue;
        }
        // visibility 三态(可空)
        if let Some(v) = item.visibility {
            if !(1..=3).contains(&v) {
                fields.push(("items", "invalid_enum"));
                continue;
            }
        }
        // attribute_id 存在
        let found = db
            .query_one(sea_orm::Statement::from_sql_and_values(
                sea_orm::DatabaseBackend::MySql,
                "SELECT id FROM attribute_def WHERE id = ?",
                [aid.into()],
            ))
            .await
            .map_err(db_err)?;
        if found.is_none() {
            fields.push(("items", "attribute_not_exists"));
            continue;
        }
        rows.push((aid, item.visibility.unwrap_or(1) as i8));
    }
    if !fields.is_empty() {
        return Err(CatalogError::field_validation(&fields));
    }
    Ok(rows)
}

async fn replace_set_items(
    tx: &sea_orm::DatabaseTransaction,
    set_id: i64,
    items: &[(i64, i8)],
) -> Result<(), CatalogError> {
    tx.execute(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("DELETE FROM attribute_set_item WHERE attribute_set_id = {}", set_id),
    ))
    .await
    .map_err(db_err)?;
    for (i, (aid, vis)) in items.iter().enumerate() {
        tx.execute(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "INSERT INTO attribute_set_item(attribute_set_id, attribute_id, visibility, sort_order, created_at, updated_at) VALUES (?,?,?,?,NOW(3),NOW(3))",
            [set_id.into(), (*aid).into(), (*vis).into(), (i as i64).into()],
        ))
        .await
        .map_err(db_err)?;
    }
    Ok(())
}

/// E-CAT-20 新增属性集(TX-CAT-009)
pub async fn set_create(
    db: &sea_orm::DatabaseConnection,
    audit_db: &sea_orm::DatabaseConnection,
    req: SetUpsert,
    operator: &str,
) -> Result<SetDto, CatalogError> {
    let items = validate_set(db, &req).await?;
    let label = req.label.as_deref().map(str::trim).unwrap_or_default().to_string();
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        "INSERT INTO attribute_set(label, created_at, updated_at) VALUES (?,NOW(3),NOW(3))",
        [label.clone().into()],
    ))
    .await
    .map_err(db_err)?;
    let id = inserted_id(&tx).await?;
    replace_set_items(&tx, id as i64, &items).await?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "创建属性集", &label).await;
    Ok(SetDto {
        id: id as i64,
        label,
        items: items
            .iter()
            .map(|(a, v)| serde_json::json!({"attribute_id": a, "visibility": v}))
            .collect(),
        category_count: 0,
    })
}

/// E-CAT-21 编辑(TX-CAT-010 矩阵全量重写)
pub async fn set_update(
    db: &sea_orm::DatabaseConnection,
    audit_db: &sea_orm::DatabaseConnection,
    id: i64,
    req: SetUpsert,
    operator: &str,
) -> Result<SetDto, CatalogError> {
    let existing = db
        .query_one(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "SELECT id, label FROM attribute_set WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(CatalogError::new(404505));
    let _ = existing.map_err(|mut e: CatalogError| {
        e.code = 404505;
        e
    })?;
    let items = validate_set(db, &req).await?;
    let label = req.label.as_deref().map(str::trim).unwrap_or_default().to_string();
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        "UPDATE attribute_set SET label = ?, updated_at = NOW(3) WHERE id = ?",
        [label.clone().into(), id.into()],
    ))
    .await
    .map_err(db_err)?;
    replace_set_items(&tx, id, &items).await?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "编辑属性集", &label).await;
    let category_count = db
        .query_one(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!("SELECT COUNT(*) AS n FROM category WHERE attribute_set_id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<i64>("", "n").ok())
        .unwrap_or(0);
    Ok(SetDto {
        id,
        label,
        items: items
            .iter()
            .map(|(a, v)| serde_json::json!({"attribute_id": a, "visibility": v}))
            .collect(),
        category_count,
    })
}

/// E-CAT-22 删除(守卫 409503→category 引用;此处 409506 同型)
pub async fn set_delete(
    db: &sea_orm::DatabaseConnection,
    audit_db: &sea_orm::DatabaseConnection,
    id: i64,
    operator: &str,
) -> Result<(), CatalogError> {
    let existing = db
        .query_one(sea_orm::Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            "SELECT id, label FROM attribute_set WHERE id = ?",
            [id.into()],
        ))
        .await
        .map_err(db_err)?
        .ok_or(CatalogError::new(404505))?;
    let label: String = existing.try_get("", "label").unwrap_or_default();
    let category_count = db
        .query_one(sea_orm::Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!("SELECT COUNT(*) AS n FROM category WHERE attribute_set_id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<i64>("", "n").ok())
        .unwrap_or(0);
    if category_count > 0 {
        return Err(CatalogError::new(409506).with_detail("category_count", Value::from(category_count)));
    }
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("DELETE FROM attribute_set_item WHERE attribute_set_id = {}", id),
    ))
    .await
    .map_err(db_err)?;
    tx.execute(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("DELETE FROM attribute_set WHERE id = {}", id),
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    audit(audit_db, operator, "删除属性集", &label).await;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn key_pattern() {
        assert!(key_valid("fabric"));
        assert!(key_valid("back_style2"));
        assert!(!key_valid("Fabric"));
        assert!(!key_valid("2fab"));
        assert!(!key_valid("fab-ric"));
        assert!(!key_valid(""));
    }

    #[test]
    fn options_rules_by_type() {
        let mut f: Vec<(&'static str, &'static str)> = vec![];
        validate_options(1, &Some(vec!["A".into(), "B".into()]), &mut f);
        assert!(f.is_empty(), "select 有 options 通过");
        let mut f2: Vec<(&'static str, &'static str)> = vec![];
        validate_options(1, &Some(vec!["A".into(), "A".into()]), &mut f2);
        assert_eq!(f2[0].1, "duplicated");
        let mut f3: Vec<(&'static str, &'static str)> = vec![];
        validate_options(3, &Some(vec!["A".into()]), &mut f3);
        assert_eq!(f3[0].1, "not_allowed", "text 禁止 options");
        let mut f4: Vec<(&'static str, &'static str)> = vec![];
        validate_options(4, &None, &mut f4);
        assert!(f4.is_empty(), "toggle 无 options 通过");
    }

    #[test]
    fn translations_options_length_mismatch() {
        let mut f: Vec<(&'static str, &'static str)> = vec![];
        let trs = vec![DefTranslationIn {
            locale: "es".into(),
            label: Some("T".into()),
            options: Some(serde_json::json!(["a"])),
        }];
        validate_translations(&Some(trs), &Some(vec!["a".into(), "b".into()]), &mut f);
        assert_eq!(f[0].1, "options_length_mismatch");
    }
}
