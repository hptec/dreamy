//! product admin 端:列表/详情/CRUD/状态切换/flags/批量。

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement};
use serde_json::{json, Value};

use crate::service_category::CatalogError;

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
