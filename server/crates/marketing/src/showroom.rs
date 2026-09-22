//! showroom 域:婚礼选品分享间(create/列表/详情/编辑/删除 + items/members/votes/comments)。
//! 对齐 StoreShowroomService;invite_token UUID v4 + version 递增。

use std::collections::HashMap;

use sea_orm::{ConnectionTrait, DatabaseConnection, DbBackend, Statement, TransactionTrait};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::service_category::CatalogError;

fn db_err(e: sea_orm::DbErr) -> CatalogError {
    tracing::error!("[showroom] db error:{e}");
    CatalogError::new(500801)
}

pub fn not_found() -> CatalogError {
    CatalogError::new(404907)
}

pub fn field_err(fields: Vec<(&'static str, &'static str)>) -> CatalogError {
    CatalogError::field_validation(&fields)
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct ShowroomUpsert {
    pub name: Option<String>,
    pub wedding_date: Option<String>,
}

fn validate(req: &ShowroomUpsert) -> Result<(String, Option<String>), CatalogError> {
    let mut fields: Vec<(&'static str, &'static str)> = vec![];
    let name = req.name.as_deref().map(str::trim).filter(|s| !s.is_empty());
    let name = match name {
        None => {
            fields.push(("name", "required"));
            String::new()
        }
        Some(n) if n.len() > 64 => {
            fields.push(("name", "too_long"));
            String::new()
        }
        Some(n) => n.to_string(),
    };
    if let Some(wd) = &req.wedding_date {
        if chrono::NaiveDate::parse_from_str(wd, "%Y-%m-%d").is_err() {
            fields.push(("wedding_date", "invalid"));
        }
    }
    if !fields.is_empty() {
        return Err(field_err(fields));
    }
    Ok((name, req.wedding_date.clone()))
}

async fn load_items(db: &DatabaseConnection, showroom_id: i64) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT si.id, si.product_id, si.color, p.name AS product_name, p.slug, p.price, \
                 p.status, (SELECT url FROM product_image i WHERE i.product_id = si.product_id AND i.kind = 1 ORDER BY i.sort ASC, i.id ASC LIMIT 1) AS image_url \
                 FROM showroom_item si JOIN product p ON p.id = si.product_id WHERE si.showroom_id = {} ORDER BY si.id ASC",
                showroom_id
            ),
        ))
        .await
        .map_err(db_err)?;
    let item_ids: Vec<i64> = rows.iter().filter_map(|r| r.try_get::<u64>("", "id").ok().map(|v| v as i64)).collect();
    let votes = load_votes(db, &item_ids).await?;
    Ok(rows
        .iter()
        .map(|r| {
            let iid = r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
            let (up, down) = votes.get(&iid).copied().unwrap_or((0, 0));
            serde_json::json!({
                "id": iid,
                "product_id": r.try_get::<i64>("", "product_id").unwrap_or(0),
                "color": r.try_get::<String>("", "color").ok(),
                "product": {
                    "name": r.try_get::<String>("", "product_name").unwrap_or_default(),
                    "slug": r.try_get::<String>("", "slug").unwrap_or_default(),
                    "price": crate::cart::dec(r, "price").unwrap_or(0.0),
                    "status": r.try_get::<i64>("", "status").unwrap_or(1),
                    "image_url": r.try_get::<String>("", "image_url").ok(),
                },
                "votes_up": up,
                "votes_down": down,
            })
        })
        .collect())
}

async fn load_votes(db: &DatabaseConnection, item_ids: &[i64]) -> Result<HashMap<i64, (i64, i64)>, CatalogError> {
    let mut out: HashMap<i64, (i64, i64)> = HashMap::new();
    if item_ids.is_empty() {
        return Ok(out);
    }
    let id_list = item_ids.iter().map(|i| i.to_string()).collect::<Vec<_>>().join(",");
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT showroom_item_id AS iid, vote, COUNT(*) AS n FROM showroom_vote WHERE showroom_item_id IN ({}) GROUP BY showroom_item_id, vote",
                id_list
            ),
        ))
        .await
        .map_err(db_err)?;
    for r in rows {
        let iid = r.try_get::<i64>("", "iid").unwrap_or(0);
        let vote = r.try_get::<i64>("", "vote").unwrap_or(0);
        let n = r.try_get::<i64>("", "n").unwrap_or(0);
        let e = out.entry(iid).or_insert((0, 0));
        if vote == 1 {
            e.0 = n;
        } else if vote == -1 {
            e.1 = n;
        }
    }
    Ok(out)
}

async fn load_members(db: &DatabaseConnection, showroom_id: i64) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT id, nickname, email, assigned_item_id FROM showroom_member WHERE showroom_id = {} ORDER BY id ASC",
                showroom_id
            ),
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            serde_json::json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "nickname": r.try_get::<String>("", "nickname").unwrap_or_default(),
                "email": r.try_get::<String>("", "email").unwrap_or_default(),
                "assigned_item_id": r.try_get::<i64>("", "assigned_item_id").ok(),
            })
        })
        .collect())
}

async fn detail(db: &DatabaseConnection, id: i64, owner_id: Option<i64>) -> Result<Value, CatalogError> {
    let where_owner = owner_id.map(|o| format!(" AND owner_id = {}", o)).unwrap_or_default();
    let row = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            format!(
                "SELECT id, owner_id, name, wedding_date, invite_token, invite_version FROM showroom WHERE id = {}{}",
                id, where_owner
            ),
        ))
        .await
        .map_err(db_err)?
        .ok_or_else(crate::shipment::not_found)?;
    let sid = row.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0);
    let items = load_items(db, sid).await?;
    let members = load_members(db, sid).await?;
    Ok(serde_json::json!({
        "id": sid,
        "owner_id": row.try_get::<i64>("", "owner_id").unwrap_or(0),
        "name": row.try_get::<String>("", "name").unwrap_or_default(),
        "wedding_date": row.try_get::<String>("", "wedding_date").ok(),
        "invite_token": row.try_get::<String>("", "invite_token").ok(),
        "items": items,
        "members": members,
    }))
}

/// E-createShowroom(name 必填 ≤64;wedding_date 可选)
pub async fn create(db: &DatabaseConnection, owner_id: i64, req: &ShowroomUpsert) -> Result<Value, CatalogError> {
    let (name, wedding_date) = validate(req)?;
    let invite_token = uuid::Uuid::new_v4().to_string();
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO showroom(owner_id, name, wedding_date, invite_token, invite_version, created_at, updated_at) VALUES (?,?,?,?,1,NOW(3),NOW(3))",
        [
            owner_id.into(),
            name.clone().into(),
            wedding_date.clone().into(),
            invite_token.clone().into(),
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
    detail(db, id as i64, Some(owner_id)).await
}

/// E-listShowrooms(owner 全部)
pub async fn list(db: &DatabaseConnection, owner_id: i64) -> Result<Vec<Value>, CatalogError> {
    let rows = db
        .query_all(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id, name, wedding_date FROM showroom WHERE owner_id = ? ORDER BY id DESC",
            [owner_id.into()],
        ))
        .await
        .map_err(db_err)?;
    Ok(rows
        .iter()
        .map(|r| {
            serde_json::json!({
                "id": r.try_get::<u64>("", "id").map(|v| v as i64).unwrap_or(0),
                "name": r.try_get::<String>("", "name").unwrap_or_default(),
                "wedding_date": r.try_get::<String>("", "wedding_date").ok(),
            })
        })
        .collect())
}

/// E-getShowroomForOwner
pub async fn get_for_owner(db: &DatabaseConnection, owner_id: i64, id: i64) -> Result<Value, CatalogError> {
    detail(db, id, Some(owner_id)).await
}

/// E-updateShowroom(name/wedding_date)
pub async fn update(db: &DatabaseConnection, owner_id: i64, id: i64, req: &ShowroomUpsert) -> Result<Value, CatalogError> {
    let (name, wedding_date) = validate(req)?;
    let exists = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id FROM showroom WHERE id = ? AND owner_id = ?",
            [id.into(), owner_id.into()],
        ))
        .await
        .map_err(db_err)?
        .is_some();
    if !exists {
        return Err(not_found());
    }
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE showroom SET name = ?, wedding_date = ?, updated_at = NOW(3) WHERE id = ? AND owner_id = ?",
        [name.into(), wedding_date.into(), id.into(), owner_id.into()],
    ))
    .await
    .map_err(db_err)?;
    detail(db, id, Some(owner_id)).await
}

/// E-deleteShowroom
pub async fn delete(db: &DatabaseConnection, owner_id: i64, id: i64) -> Result<(), CatalogError> {
    let tx = db.begin().await.map_err(db_err)?;
    for table in ["showroom_vote", "showroom_comment", "showroom_item", "showroom_member"] {
        let sub = if table == "showroom_vote" || table == "showroom_comment" {
            format!(
                "DELETE FROM {} WHERE showroom_item_id IN (SELECT id FROM showroom_item WHERE showroom_id = {})",
                table, id
            )
        } else {
            format!("DELETE FROM {} WHERE showroom_id = {}", table, id)
        };
        tx.execute(Statement::from_string(DbBackend::MySql, sub)).await.map_err(db_err)?;
    }
    tx.execute(Statement::from_string(
        DbBackend::MySql,
        format!("DELETE FROM showroom WHERE id = {} AND owner_id = {}", id, owner_id),
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    Ok(())
}

/// E-resetInviteToken(新 UUID + version+1)
pub async fn reset_invite(db: &DatabaseConnection, owner_id: i64, id: i64) -> Result<Value, CatalogError> {
    let token = uuid::Uuid::new_v4().to_string();
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE showroom SET invite_token = ?, invite_version = invite_version + 1, updated_at = NOW(3) WHERE id = ? AND owner_id = ?",
        [token.clone().into(), id.into(), owner_id.into()],
    ))
    .await
    .map_err(db_err)?;
    Ok(serde_json::json!({"invite_token": token}))
}

/// 添加成员(邀请)
pub async fn add_member(
    db: &DatabaseConnection,
    owner_id: i64,
    showroom_id: i64,
    nickname: &str,
    email: &str,
) -> Result<Value, CatalogError> {
    let exists = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id FROM showroom WHERE id = ? AND owner_id = ?",
            [showroom_id.into(), owner_id.into()],
        ))
        .await
        .map_err(db_err)?
        .is_some();
    if !exists {
        return Err(not_found());
    }
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO showroom_member(showroom_id, nickname, email, created_at, updated_at) VALUES (?,?,?,NOW(3),NOW(3))",
        [showroom_id.into(), nickname.into(), email.to_lowercase().into()],
    ))
    .await
    .map_err(db_err)?;
    let id = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            "SELECT LAST_INSERT_ID() AS id".to_string(),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<u64>("", "id").ok())
        .unwrap_or(0);
    Ok(serde_json::json!({"id": id as i64, "nickname": nickname, "email": email}))
}

/// 添加商品项
pub async fn add_item(
    db: &DatabaseConnection,
    owner_id: i64,
    showroom_id: i64,
    product_id: i64,
    color: Option<String>,
) -> Result<Value, CatalogError> {
    let exists = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id FROM showroom WHERE id = ? AND owner_id = ?",
            [showroom_id.into(), owner_id.into()],
        ))
        .await
        .map_err(db_err)?
        .is_some();
    if !exists {
        return Err(not_found());
    }
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO showroom_item(showroom_id, product_id, color, created_at, updated_at) VALUES (?,?,?,NOW(3),NOW(3))",
        [showroom_id.into(), product_id.into(), color.clone().into()],
    ))
    .await
    .map_err(db_err)?;
    let id = db
        .query_one(Statement::from_string(
            DbBackend::MySql,
            "SELECT LAST_INSERT_ID() AS id".to_string(),
        ))
        .await
        .map_err(db_err)?
        .and_then(|r| r.try_get::<u64>("", "id").ok())
        .unwrap_or(0);
    Ok(serde_json::json!({"id": id as i64, "product_id": product_id, "color": color}))
}

/// 投票(成员对商品项;1=喜欢 -1=不喜欢;uk 幂等 upsert)
pub async fn vote(
    db: &DatabaseConnection,
    member_id: i64,
    item_id: i64,
    vote: i64,
) -> Result<(), CatalogError> {
    if !matches!(vote, 1 | -1) {
        return Err(field_err(vec![("vote", "invalid")]));
    }
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO showroom_vote(showroom_item_id, member_id, vote, created_at, updated_at) VALUES (?,?,?,NOW(3),NOW(3))          ON DUPLICATE KEY UPDATE vote = VALUES(vote), updated_at = NOW(3)",
        [item_id.into(), member_id.into(), vote.into()],
    ))
    .await
    .map_err(db_err)?;
    Ok(())
}

/// 评论
pub async fn comment(
    db: &DatabaseConnection,
    member_id: i64,
    item_id: i64,
    content: &str,
) -> Result<Value, CatalogError> {
    let content = content.trim();
    if content.is_empty() || content.len() > 500 {
        return Err(field_err(vec![("content", "length")]));
    }
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "INSERT INTO showroom_comment(showroom_item_id, member_id, content, created_at, updated_at) VALUES (?,?,?,NOW(3),NOW(3))",
        [item_id.into(), member_id.into(), content.into()],
    ))
    .await
    .map_err(db_err)?;
    Ok(serde_json::json!({"content": content}))
}

/// 分配商品给成员
pub async fn assign(
    db: &DatabaseConnection,
    owner_id: i64,
    showroom_id: i64,
    member_id: i64,
    item_id: i64,
) -> Result<Value, CatalogError> {
    let exists = db
        .query_one(Statement::from_sql_and_values(
            DbBackend::MySql,
            "SELECT id FROM showroom WHERE id = ? AND owner_id = ?",
            [showroom_id.into(), owner_id.into()],
        ))
        .await
        .map_err(db_err)?
        .is_some();
    if !exists {
        return Err(not_found());
    }
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "UPDATE showroom_member SET assigned_item_id = ?, updated_at = NOW(3) WHERE id = ? AND showroom_id = ?",
        [item_id.into(), member_id.into(), showroom_id.into()],
    ))
    .await
    .map_err(db_err)?;
    Ok(serde_json::json!({"assigned": true}))
}

/// 删除成员/商品项
pub async fn delete_member(db: &DatabaseConnection, owner_id: i64, showroom_id: i64, member_id: i64) -> Result<(), CatalogError> {
    db.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "DELETE m FROM showroom_member m JOIN showroom s ON s.id = m.showroom_id WHERE m.id = ? AND m.showroom_id = ? AND s.owner_id = ?",
        [member_id.into(), showroom_id.into(), owner_id.into()],
    ))
    .await
    .map_err(db_err)?;
    Ok(())
}

pub async fn delete_item(db: &DatabaseConnection, owner_id: i64, showroom_id: i64, item_id: i64) -> Result<(), CatalogError> {
    let tx = db.begin().await.map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "DELETE v FROM showroom_vote v JOIN showroom_item si ON si.id = v.showroom_item_id JOIN showroom s ON s.id = si.showroom_id WHERE v.showroom_item_id = ? AND si.showroom_id = ? AND s.owner_id = ?",
        [item_id.into(), showroom_id.into(), owner_id.into()],
    ))
    .await
    .map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "DELETE c FROM showroom_comment c JOIN showroom_item si ON si.id = c.showroom_item_id JOIN showroom s ON s.id = si.showroom_id WHERE c.showroom_item_id = ? AND si.showroom_id = ? AND s.owner_id = ?",
        [item_id.into(), showroom_id.into(), owner_id.into()],
    ))
    .await
    .map_err(db_err)?;
    tx.execute(Statement::from_sql_and_values(
        DbBackend::MySql,
        "DELETE si FROM showroom_item si JOIN showroom s ON s.id = si.showroom_id WHERE si.id = ? AND si.showroom_id = ? AND s.owner_id = ?",
        [item_id.into(), showroom_id.into(), owner_id.into()],
    ))
    .await
    .map_err(db_err)?;
    tx.commit().await.map_err(db_err)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validate_name_rules() {
        let req = ShowroomUpsert { name: Some("".into()), wedding_date: None };
        assert!(validate(&req).is_err());
        let req2 = ShowroomUpsert { name: Some("Emma's Picks".into()), wedding_date: Some("2026-10-01".into()) };
        assert!(validate(&req2).is_ok());
    }
}
