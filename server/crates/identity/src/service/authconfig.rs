//! 认证配置(单例 id=1)+ 数值区间校验(40002)。

use common::state::SharedState;
use sea_orm::{ActiveModelTrait, ColumnTrait, DatabaseTransaction, EntityTrait, QueryFilter, Set};

use crate::entity::auth_config;
use crate::service::SvcError;

pub struct AuthConfigData {
    pub email_enabled: bool,
    pub google_enabled: bool,
    pub apple_enabled: bool,
    pub otp_length: i8,
    pub otp_ttl_minutes: i32,
    pub otp_resend_seconds: i32,
    pub otp_max_attempts: i32,
    pub min_methods: i8,
    pub google_client_id: Option<String>,
    pub apple_service_id: Option<String>,
}

pub async fn get(state: &SharedState) -> Result<AuthConfigData, SvcError> {
    let row = auth_config::Entity::find_by_id(1u64)
        .one(&state.db)
        .await?
        .ok_or(SvcError::code(50001))?;
    Ok(to_data(&row))
}

fn to_data(m: &auth_config::Model) -> AuthConfigData {
    AuthConfigData {
        email_enabled: m.email_enabled != 0,
        google_enabled: m.google_enabled != 0,
        apple_enabled: m.apple_enabled != 0,
        otp_length: m.otp_length,
        otp_ttl_minutes: m.otp_ttl_minutes,
        otp_resend_seconds: m.otp_resend_seconds,
        otp_max_attempts: m.otp_max_attempts,
        min_methods: m.min_methods,
        google_client_id: m.google_client_id.clone(),
        apple_service_id: m.apple_service_id.clone(),
    }
}

/// admin 更新(区间校验 40002;email_enabled 服务端强制 true,对齐 Java)
pub struct AuthConfigUpdate {
    pub google_enabled: Option<bool>,
    pub apple_enabled: Option<bool>,
    pub otp_length: Option<i8>,
    pub otp_ttl_minutes: Option<i32>,
    pub otp_resend_seconds: Option<i32>,
    pub otp_max_attempts: Option<i32>,
    pub min_methods: Option<i8>,
    pub google_client_id: Option<String>,
    pub apple_service_id: Option<String>,
}

pub async fn update(
    state: &SharedState,
    patch: &AuthConfigUpdate,
) -> Result<AuthConfigData, SvcError> {
    // STEP-1 区间校验(40002 CONFIG_OUT_OF_RANGE,对齐 Java AuthConfigService)
    if let Some(v) = patch.otp_length {
        if ![4, 6, 8].contains(&v) {
            return Err(SvcError::code(40002));
        }
    }
    if let Some(v) = patch.otp_ttl_minutes {
        if !(1..=30).contains(&v) {
            return Err(SvcError::code(40002));
        }
    }
    if let Some(v) = patch.otp_resend_seconds {
        if !(10..=120).contains(&v) {
            return Err(SvcError::code(40002));
        }
    }
    if let Some(v) = patch.otp_max_attempts {
        if !(3..=10).contains(&v) {
            return Err(SvcError::code(40002));
        }
    }
    if let Some(v) = patch.min_methods {
        if !(1..=3).contains(&v) {
            return Err(SvcError::code(40002));
        }
    }

    let row = auth_config::Entity::find_by_id(1u64)
        .one(&state.db)
        .await?
        .ok_or(SvcError::code(50001))?;
    let mut am: auth_config::ActiveModel = row.into();
    if let Some(v) = patch.google_enabled {
        am.google_enabled = Set(v as i8);
    }
    if let Some(v) = patch.apple_enabled {
        am.apple_enabled = Set(v as i8);
    }
    if let Some(v) = patch.otp_length {
        am.otp_length = Set(v);
    }
    if let Some(v) = patch.otp_ttl_minutes {
        am.otp_ttl_minutes = Set(v);
    }
    if let Some(v) = patch.otp_resend_seconds {
        am.otp_resend_seconds = Set(v);
    }
    if let Some(v) = patch.otp_max_attempts {
        am.otp_max_attempts = Set(v);
    }
    if let Some(v) = patch.min_methods {
        am.min_methods = Set(v);
    }
    if let Some(v) = patch.google_client_id.clone() {
        am.google_client_id = Set(if v.is_empty() { None } else { Some(v) });
    }
    if let Some(v) = patch.apple_service_id.clone() {
        am.apple_service_id = Set(if v.is_empty() { None } else { Some(v) });
    }
    let updated = am.update(&state.db).await?;
    Ok(to_data(&updated))
}

/// 启动幂等种子(空库首启:P3 种子任务调用)
pub async fn seed_if_missing(db: &DatabaseTransaction) -> Result<(), sea_orm::DbErr> {
    let exists = auth_config::Entity::find()
        .filter(auth_config::Column::Id.eq(1u64))
        .one(db)
        .await?;
    if exists.is_none() {
        let am = auth_config::ActiveModel {
            id: Set(1u64),
            email_enabled: Set(1),
            google_enabled: Set(1),
            apple_enabled: Set(1),
            otp_length: Set(6),
            otp_ttl_minutes: Set(5),
            otp_resend_seconds: Set(60),
            otp_max_attempts: Set(5),
            min_methods: Set(1),
            google_client_id: Set(None),
            apple_service_id: Set(None),
            ..Default::default()
        };
        auth_config::Entity::insert(am).exec(db).await?;
    }
    Ok(())
}
