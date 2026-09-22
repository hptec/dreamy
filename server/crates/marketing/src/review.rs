//! review 域:store 提交/列表/我的评价 + admin 审核/回复/删除。
//! 对齐 StoreReviewService / AdminReviewService;错误码 403801/404801/409801/422801(域段 8)。

use std::collections::HashMap;

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement, TransactionTrait};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::service_category::CatalogError;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[review] db error:{e}");
    CatalogError::new(500801)
}

fn trading(code: i32) -> CatalogError {
    CatalogError::new(code)
}

fn field_err(fields: Vec<(&'static str, &'static str)>) -> CatalogError {
    CatalogError::field_validation(&fields)
}

pub fn http_status_review(code: i32) -> u16 {
    match code {
        403801 => 403,
        404801 => 404,
        409801 => 409,
        422801 => 422,
        _ => 500,
    }
}

// 错误码
const REVIEW_NOT_ALLOWED: i32 = 403801;
const REVIEW_NOT_FOUND: i32 = 404801;
const ALREADY_REVIEWED: i32 = 409801;
const FIELD_VALIDATION: i32 = 422801;

const REVIEW_COLS: &str = "id, product_id, user_id, customer_name, rating, content, status, featured, submitted_at, reply_author, reply_content, reply_time";

fn row_to_value(r: &sea_orm::QueryResult, images: Vec<Value>) -> Value {
    serde_json::json!({
        "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
        "product_id": r.try_get::<i64>("", "product_id").unwrap_or(0),
        "customer_name": r.try_get::<String>("", "customer_name").unwrap_or_default(),
        "rating": r.try_get::<i64>("", "rating").unwrap_or(0),
        "content": r.try_get::<String>("", "content").ok(),
        "status": r.try_get::<i64>("", "status").unwrap_or(1),
        "featured": r.try_get::<bool>("", "featured").unwrap_or(false),
        "submitted_at": r.try_get::<String>("", "submitted_at").ok(),
        "images": images,
        "reply_author": r.try_get::<String>("", "reply_author").ok(),
        "reply_content": r.try_get::<String>("", "reply_content").ok(),
        "reply_time": r.try_get::<String>("", "reply_time").ok(),
    })
}

async fn load_images(db: &DatabaseConnection, review_ids: &[i64]) -> Result<HashMap<i64, Vec<Value>>, CatalogError> {
    let mut out: HashMap<i64, Vec<Value>> = HashMap::new();
    if review_ids.is_empty() {
        return Ok(out);
    }
    let id_list = review_ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT review_id, id, url, rejected FROM review_image WHERE review_id IN ({})", id_list),
        ))
        .await
        .map_err(db_err)?;
    for r in rows {
        let rid = r.try_get::<i64>("", "review_id").unwrap_or(0);
        out.entry(rid).or_default().push(serde_json::json!({
            "id": r.try_get::<i64>("", "id").unwrap_or(0),
            "url": r.try_get::<String>("", "url").unwrap_or_default(),
            "rejected": r.try_get::<bool>("", "rejected").unwrap_or(false),
        }));
    }
    Ok(out)
}

async fn rating_recacble(db: &DatabaseConnection, product_id: i64) -> Result<Option<(f64, i64)>, CatalogError> {
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT AVG(rating) AS avg_r, COUNT(*) AS n FROM review WHERE product_id = {} AND status = 2",
                product_id
            ),
        ))
        .await
        .map_err(db_err)?;
    Ok(row.and_then(|r| {
        let avg: Option<f64> = r.try_get("", "avg_r").ok();
        let n: i64 = r.try_get("", "n").unwrap_or(0);
        avg.map(|a| (a, n))
    }))
}

async fn recalc_rating(db: &DatabaseConnection, product_id: i64) -> Result<(), CatalogError> {
    if let Some((avg, n)) = rating_recacble(db, product_id).await? {
        db.execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "UPDATE product SET rating_avg = ?, rating_count = ?, updated_at = NOW(3) WHERE id = ?",
            [avg.into(), n.into(), product_id.into()],
        ))
        .await
        .map_err(db_err)?;
    }
    Ok(())
}

/// V-REV-007 images ≤9,url 必填 ≤512
fn validate_images(images: &Option<Vec<String>>, fields: &mut Vec<(&'static str, &'static str)>) -> Vec<String> {
    let Some(imgs) = images else {
        return vec![];
    };
    if imgs.len() > 9 {
        fields.push(("images", "too_many"));
        return vec![];
    }
    for url in imgs {
        if url.is_empty() || url.len() > 512 {
            fields.push(("images", "url_invalid"));
            break;
        }
    }
    imgs.clone()
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct ReviewCreate {
    pub product_id: Option<i64>,
    pub rating: Option<i64>,
    pub content: Option<String>,
    pub images: Option<Vec<String>>,
}

/// E-createReview(TX-REV-001:购买资格→唯一性→INSERT pending→rating 回写)
pub async fn create(
    db: &DatabaseConnection,
    customer_id: i64,
    customer_name: &str,
    req: &ReviewCreate,
) -> Result<Value, CatalogError> {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let Some(pid) = req.product_id else {
        return Err(field_err(vec![("product_id", "required")]));
    };
    let rating = match req.rating {
        Some(r) if (1..=5).contains(&r) => r,
        Some(_) => {
            fields.push(("rating", "out_of_range"));
            0
        }
        None => {
            fields.push(("rating", "required"));
            0
        }
    };
    let content = req.content.as_deref().map(str::trim).filter(|s| !s.is_empty());
    if let Some(c) = content {
        if c.len() > 5000 {
            fields.push(("content", "too_long"));
        }
    }
    let images = validate_images(&req.images, &mut fields);
    if !fields.is_empty() {
        return Err(field_err(fields));
    }
    // 商品存在且 published
    let product = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT status FROM product WHERE id = ?",
            [pid.into()],
        ))
        .await
        .map_err(db_err)?
        .filter(|r| r.try_get::<i64>("", "status").unwrap_or(0) == 2);
    if product.is_none() {
        return Err(trading(404501));
    }
    let tx = db.begin().await.map_err(db_err)?;
    // 购买资格(该用户有含此商品的已完成订单)——跨域校验:orders/order_line 在同库
    let purchased = tx
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT COUNT(*) AS n FROM orders o JOIN order_line ol ON ol.order_id = o.id \
             WHERE o.customer_id = ? AND ol.product_id = ? AND o.status IN (2, 3, 4, 8)",
            [customer_id.into(), pid.into()],
        ))
        .await
        .map_err(db_err)?
        .map(|r| r.try_get::<i64>("", "n").unwrap_or(0))
        .unwrap_or(0);
    let _ = purchased;
    if purchased == 0 {
        return Err(trading(REVIEW_NOT_ALLOWED));
    }
    // 唯一性(uk_review_user_product)
    let dup = tx
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id FROM review WHERE user_id = ? AND product_id = ?",
            [customer_id.into(), pid.into()],
        ))
        .await
        .map_err(db_err)?;
    if dup.is_some() {
        return Err(trading(ALREADY_REVIEWED));
    }
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO review(product_id, user_id, customer_name, rating, content, status, featured, submitted_at, created_at, updated_at) VALUES (?,?,?,?,?,1,0,NOW(3),NOW(3),NOW(3))",
        [pid.into(), customer_id.into(), customer_name.into(), rating.into(), content.into()],
    ))
    .await
    .map_err(|e| {
        if e.to_string().contains("1062") {
            trading(ALREADY_REVIEWED)
        } else {
            db_err(e)
        }
    })?;
    let review_id = tx
        .query_one(Statement::from_string(
            DbBackend::MySql,
            "SELECT LAST_INSERT_ID() AS id".to_string(),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<u64>("", "id").ok())
        .unwrap_or(0);
    for url in &images {
        tx.execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "INSERT INTO review_image(review_id, url, rejected, created_at, updated_at) VALUES (?,?,0,NOW(3),NOW(3))",
            [(review_id as i64).into(), url.clone().into()],
        ))
        .await
        .map_err(db_err)?;
    }
    tx.commit().await.map_err(db_err)?;
    // rating 回写(pending 不计入;审核通过后由 admin 端回写)
    Ok(serde_json::json!({
        "id": review_id as i64,
        "product_id": pid,
        "rating": rating,
        "status": 1,
    }))
}

/// E-listStoreReviews(APPROVED;featured 优先 + 时间倒序)
pub async fn store_list(
    db: &DatabaseConnection,
    product_id: i64,
    page: i64,
    page_size: i64,
) -> Result<(Vec<Value>, i64), CatalogError> {
    let total = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT COUNT(*) AS n FROM review WHERE product_id = {} AND status = 2",
                product_id
            ),
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
                "SELECT {} FROM review WHERE product_id = {} AND status = 2 ORDER BY featured DESC, submitted_at DESC LIMIT {} OFFSET {}",
                REVIEW_COLS, product_id, page_size, offset
            ),
        ))
        .await
        .map_err(db_err)?;
    let ids: Vec<i64> = rows.iter().filter_map(|r| r.try_get::<u64>("", "id").ok().map(|v| v as i64)).collect();
    let images = load_images(db, &ids).await?;
    Ok((
        rows.iter().map(|r| {
            let rid = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            row_to_value(r, images.get(&rid).cloned().unwrap_or_default())
        }).collect(),
        total,
    ))
}

/// E-listMyReviews(本人全部状态)
pub async fn my_reviews(
    db: &DatabaseConnection,
    customer_id: i64,
    page: i64,
    page_size: i64,
) -> Result<(Vec<Value>, i64), CatalogError> {
    let total = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT COUNT(*) AS n FROM review WHERE user_id = ?",
            [customer_id.into()],
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<i64>("", "n").ok())
        .unwrap_or(0);
    let offset = (page - 1) * page_size;
    let rows = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            format!(
                "SELECT {} FROM review WHERE user_id = ? ORDER BY submitted_at DESC LIMIT {} OFFSET {}",
                REVIEW_COLS, page_size, offset
            ),
            [customer_id.into()],
        ))
        .await
        .map_err(db_err)?;
    let ids: Vec<i64> = rows.iter().filter_map(|r| r.try_get::<u64>("", "id").ok().map(|v| v as i64)).collect();
    let images = load_images(db, &ids).await?;
    Ok((
        rows.iter().map(|r| {
            let rid = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            row_to_value(r, images.get(&rid).cloned().unwrap_or_default())
        }).collect(),
        total,
    ))
}

/// admin 列表(status 筛选)
pub async fn admin_list(
    db: &DatabaseConnection,
    status: Option<i64>,
    page: i64,
    page_size: i64,
) -> Result<(Vec<Value>, i64), CatalogError> {
    let where_clause = status.map(|s| format!(" WHERE status = {}", s)).unwrap_or_default();
    let total = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT COUNT(*) AS n FROM review{}", where_clause),
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
                "SELECT {} FROM review{} ORDER BY submitted_at DESC LIMIT {} OFFSET {}",
                REVIEW_COLS, where_clause, page_size, offset
            ),
        ))
        .await
        .map_err(db_err)?;
    let ids: Vec<i64> = rows.iter().filter_map(|r| r.try_get::<u64>("", "id").ok().map(|v| v as i64)).collect();
    let images = load_images(db, &ids).await?;
    Ok((
        rows.iter().map(|r| {
            let rid = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            let mut v = row_to_value(r, images.get(&rid).cloned().unwrap_or_default());
            if let Some(product_id) = v["product_id"].as_i64() {
                v["product_name"] = Value::String(format!("product#{}", product_id));
            }
            v
        }).collect(),
        total,
    ))
}

/// admin 审核(approve/reject + rating 回写)
pub async fn admin_moderate(
    db: &DatabaseConnection,
    id: i64,
    approve: bool,
) -> Result<(), CatalogError> {
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!("SELECT id, product_id, status FROM review WHERE id = {}", id),
        ))
        .await
        .map_err(db_err)?
        .ok_or(trading(404801))?;
    let product_id = row.try_get::<i64>("", "product_id").unwrap_or(0);
    let new_status = if approve { 2 } else { 3 };
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE review SET status = ?, updated_at = NOW(3) WHERE id = ?",
        [new_status.into(), id.into()],
    ))
    .await
    .map_err(db_err)?;
    recalc_rating(db, product_id).await
}

/// admin 回复
pub async fn admin_reply(
    db: &DatabaseConnection,
    id: i64,
    author: &str,
    content: &str,
) -> Result<(), CatalogError> {
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE review SET reply_author = ?, reply_content = ?, reply_time = NOW(3), updated_at = NOW(3) WHERE id = ?",
        [author.into(), content.into(), id.into()],
    ))
    .await
    .map_err(db_err)?;
    Ok(())
}

/// admin 删除回复
pub async fn admin_delete_reply(db: &DatabaseConnection, id: i64) -> Result<(), CatalogError> {
    db.execute(Statement::from_string(
        DbBackend::MySql,
        format!(
            "UPDATE review SET reply_author = NULL, reply_content = NULL, reply_time = NULL, updated_at = NOW(3) WHERE id = {}",
            id
        ),
    ))
    .await
    .map_err(db_err)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn images_limit_nine() {
        let mut f: Vec<(&'static str, &'static str)> = vec![];
        let imgs: Vec<String> = (0..9).map(|i| format!("url{i}")).collect();
        validate_images(&Some(imgs), &mut f);
        assert!(f.is_empty());
        let imgs10: Vec<String> = (0..10).map(|i| format!("url{i}")).collect();
        validate_images(&Some(imgs10), &mut f);
        assert_eq!(f[0].1, "too_many");
    }
}
