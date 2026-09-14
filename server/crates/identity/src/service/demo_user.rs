//! Demo 种子用户通道(Java ShowroomSeedInitializer/ReviewSeedInitializer 经 gRPC 调用)。
//! 幂等:按 email(规范化小写)归并,已存在直接返回既有 id。

use common::state::SharedState;
use sea_orm::{ActiveModelTrait, ColumnTrait, EntityTrait, QueryFilter};

use crate::entity::user;
use crate::enums::{UserStatus, UserTier};
use crate::service::SvcError;

pub async fn ensure(state: &SharedState, email: &str, name: &str) -> Result<i64, SvcError> {
    let email = email.trim().to_lowercase();
    if email.is_empty() {
        return Err(SvcError::InvalidArg("email 为空".into()));
    }
    if let Some(existing) = user::Entity::find()
        .filter(user::Column::Email.eq(email.as_str()))
        .one(&state.db)
        .await?
    {
        return Ok(existing.id as i64);
    }
    let model = user::ActiveModel {
        email: sea_orm::Set(email.clone()),
        email_verified: sea_orm::Set(1),
        name: sea_orm::Set(if name.trim().is_empty() {
            None
        } else {
            Some(name.trim().to_string())
        }),
        tier: sea_orm::Set(UserTier::Regular.code() as i8),
        status: sea_orm::Set(UserStatus::Active.code() as i8),
        anonymized: sea_orm::Set(0),
        version: sea_orm::Set(0),
        joined_at: sea_orm::Set(Some(chrono::Local::now().naive_local())),
        ..Default::default()
    };
    match model.insert(&state.db).await {
        Ok(inserted) => Ok(inserted.id as i64),
        // 并发下唯一键冲突 → 幂等回读
        Err(_) => user::Entity::find()
            .filter(user::Column::Email.eq(email.as_str()))
            .one(&state.db)
            .await?
            .map(|m| m.id as i64)
            .ok_or(SvcError::NotFound),
    }
}
