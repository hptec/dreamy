//! store 收尾端点:wishlist(收藏)+ browse_history(足迹)。

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement};
use serde_json::{json, Value};

use crate::service_category::CatalogError;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[extras] db error:{e}");
    CatalogError::new(500601)
}

/// wishlist 列表(带商品卡片)
pub async fn wishlist_list(db: &DatabaseConnection, customer_id: i64) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT w.product_id, p.name, p.slug, p.price, p.status, \
             (SELECT url FROM product_image i WHERE i.product_id = w.product_id AND i.kind = 1 ORDER BY i.sort ASC, i.id ASC LIMIT 1) AS image_url \
             FROM wishlist_item w JOIN product p ON p.id = w.product_id \
             WHERE w.customer_id = ? ORDER BY w.id DESC",
            [customer_id.into()],
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            json!({
                "product_id": r.try_get::<i64>("", "product_id").unwrap_or(0),
                "name": r.try_get::<String>("", "name").unwrap_or_default(),
                "slug": r.try_get::<String>("", "slug").unwrap_or_default(),
                "price": crate::cart::dec(r, "price").unwrap_or(0.0),
                "status": r.try_get::<i64>("", "status").unwrap_or(1),
                "image_url": r.try_get::<String>("", "image_url").ok(),
            })
        })
        .collect())
}

/// 加入收藏(幂等 INSERT IGNORE)
pub async fn wishlist_add(db: &DatabaseConnection, customer_id: i64, product_id: i64) -> Result<(), CatalogError> {
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT IGNORE INTO wishlist_item(customer_id, product_id, created_at, updated_at) VALUES (?,?,NOW(3),NOW(3))",
        [customer_id.into(), product_id.into()],
    ))
    .await
    .map_err(db_err)?;
    Ok(())
}

/// 移除收藏
pub async fn wishlist_remove(db: &DatabaseConnection, customer_id: i64, product_id: i64) -> Result<bool, CatalogError> {
    let res = db
        .execute(Statement::from_sql_and_values(
            DbBackend::MySql,
            "DELETE FROM wishlist_item WHERE customer_id = ? AND product_id = ?",
            [customer_id.into(), product_id.into()],
        ))
        .await
        .map_err(db_err)?;
    Ok(res.rows_affected() > 0)
}

/// 浏览足迹(最近 N 条;PUT 语义 = 记录一次浏览)
pub async fn browse_record(db: &DatabaseConnection, customer_id: i64, product_id: i64) -> Result<(), CatalogError> {
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO browse_history(customer_id, product_id, viewed_at, created_at, updated_at) VALUES (?,?,NOW(3),NOW(3),NOW(3))",
        [customer_id.into(), product_id.into()],
    ))
    .await
    .map_err(db_err)?;
    Ok(())
}

pub async fn browse_list(db: &DatabaseConnection, customer_id: i64, limit: i64) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT b.product_id, b.viewed_at, p.name, p.slug, p.price, p.status, \
             (SELECT url FROM product_image i WHERE i.product_id = b.product_id AND i.kind = 1 ORDER BY i.sort ASC, i.id ASC LIMIT 1) AS image_url \
             FROM browse_history b JOIN product p ON p.id = b.product_id \
             WHERE b.customer_id = ? ORDER BY b.id DESC LIMIT ?",
            [customer_id.into(), limit.into()],
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            json!({
                "product_id": r.try_get::<i64>("", "product_id").unwrap_or(0),
                "viewed_at": r.try_get::<String>("", "viewed_at").ok(),
                "name": r.try_get::<String>("", "name").unwrap_or_default(),
                "slug": r.try_get::<String>("", "slug").unwrap_or_default(),
                "price": crate::cart::dec(r, "price").unwrap_or(0.0),
                "status": r.try_get::<i64>("", "status").unwrap_or(1),
                "image_url": r.try_get::<String>("", "image_url").ok(),
            })
        })
        .collect())
}

/// uploads presign(stub:本地上传模式返回本地路径;R2 随外部集成接线)
pub fn presign(filename: &str, content_type: &str) -> Value {
    let ext = filename.rsplit('.').next().unwrap_or("bin");
    let key = format!("uploads/{}.{}", uuid::Uuid::new_v4().simple(), ext);
    json!({
        "key": key,
        "url": format!("/api/store/uploads/local/{}", filename),
        "method": "PUT",
        "content_type": content_type,
        "expires_in": 600,
    })
}
