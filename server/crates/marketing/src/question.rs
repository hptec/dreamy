//! question 域:store 提问/列表 + admin 回答/可见性/批量审核。

use sea_orm::ConnectionTrait;
use serde_json::{json, Value};

use crate::service_category::CatalogError;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[question] db error:{e}");
    CatalogError::new(500801)
}

/// product_question 表结构:product_id, user_id, user_name, content, answer, answered_by, status(1=pending 2=answered), visible
pub async fn store_list(
    db: &sea_orm::DatabaseConnection,
    product_id: i64,
) -> Result<Vec<Value>, CatalogError> {
    let rows = db.query_all(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!(
            "SELECT id, question, answer, asker, visible, asked_at, answer_time FROM product_question \
             WHERE product_id = {} AND visible = 1 ORDER BY id DESC",
            product_id
        ),
    )).await.map_err(db_err)?;
    Ok(rows.iter().map(|r| json!({
        "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "content": r.try_get::<String>("", "question").unwrap_or_default(),
        "answer": r.try_get::<String>("", "answer").ok(),
        "user_name": r.try_get::<String>("", "asker").unwrap_or_default(),
        "visible": r.try_get::<bool>("", "visible").unwrap_or(true),
        "asked_at": r.try_get::<String>("", "asked_at").ok(),
        "answer_time": r.try_get::<String>("", "answer_time").ok(),
    })).collect())
}

pub async fn create(
    db: &sea_orm::DatabaseConnection,
    product_id: i64,
    user_id: i64,
    user_name: &str,
    content: &str,
) -> Result<Value, CatalogError> {
    db.execute(sea_orm::Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        "INSERT INTO product_question(product_id, user_id, asker, question, visible, asked_at, created_at, updated_at) VALUES (?,?,?,?,1,NOW(3),NOW(3),NOW(3))",
        [
            product_id.into(),
            user_id.into(),
            user_name.into(),
            content.into(),
        ],
    )).await.map_err(db_err)?;
    Ok(json!({"submitted": true}))
}

pub async fn admin_list(
    db: &sea_orm::DatabaseConnection,
    status: Option<i64>,
) -> Result<Vec<Value>, CatalogError> {
    let where_clause = String::new();
    let rows = db.query_all(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!(
            "SELECT id, product_id, asker, question, answer, visible, asked_at, answer_time FROM product_question{} ORDER BY id DESC",
            where_clause
        ),
    )).await.map_err(db_err)?;
    Ok(rows.iter().map(|r| json!({
        "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "product_id": r.try_get::<i64>("", "product_id").unwrap_or(0),
        "user_name": r.try_get::<String>("", "asker").unwrap_or_default(),
        "content": r.try_get::<String>("", "question").unwrap_or_default(),
        "answer": r.try_get::<String>("", "answer").ok(),
        "visible": r.try_get::<bool>("", "visible").unwrap_or(true),
        "asked_at": r.try_get::<String>("", "asked_at").ok(),
        "answer_time": r.try_get::<String>("", "answer_time").ok(),
    })).collect())
}

pub async fn admin_answer(
    db: &sea_orm::DatabaseConnection,
    id: i64,
    answer: &str,
) -> Result<(), CatalogError> {
    db.execute(sea_orm::Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        "UPDATE product_question SET answer = ?, answer_time = NOW(3), updated_at = NOW(3) WHERE id = ?",
        [answer.into(), id.into()],
    )).await.map_err(db_err)?;
    Ok(())
}

pub async fn admin_delete(db: &sea_orm::DatabaseConnection, id: i64) -> Result<(), CatalogError> {
    db.execute(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("DELETE FROM product_question WHERE id = {}", id),
    )).await.map_err(db_err)?;
    Ok(())
}

pub async fn admin_visibility(
    db: &sea_orm::DatabaseConnection,
    id: i64,
    visible: bool,
) -> Result<(), CatalogError> {
    db.execute(sea_orm::Statement::from_sql_and_values(
        sea_orm::DatabaseBackend::MySql,
        "UPDATE product_question SET visible = ?, updated_at = NOW(3) WHERE id = ?",
        [(visible as i64).into(), id.into()],
    )).await.map_err(db_err)?;
    Ok(())
}

pub async fn admin_batch_visible(
    db: &sea_orm::DatabaseConnection,
    ids: &[i64],
    visible: bool,
) -> Result<usize, CatalogError> {
    let id_list = ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
    let res = db.execute(sea_orm::Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        format!("UPDATE product_question SET visible = {}, updated_at = NOW(3) WHERE id IN ({})", visible as i64, id_list),
    )).await.map_err(db_err)?;
    Ok(res.rows_affected() as usize)
}
