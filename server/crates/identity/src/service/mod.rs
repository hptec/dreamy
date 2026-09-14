//! 身份域领域服务:业务规则与数据访问(P1-P3 从 Java 对应 Service 移植)。
//!
//! 性能纪律(用户指令):每个接口 Redis-first + 主动失效,Redis 故障一律降级 DB 不失败。

pub mod demo_user;
pub mod partition_maintain;
pub mod permissions;
pub mod session;
pub mod user_query;

pub use user_query::{Col, Cond, CondVal, ListQuery, Lookup, UserView};

use thiserror::Error;

/// 领域服务错误(gRPC 层统一映射:NotFound→NOT_FOUND,InvalidArg→INVALID_ARGUMENT,Infra→UNAVAILABLE)
/// DbErr 装箱:控制错误体积(Rust 惯例),`?` 自动转换不受影响
#[derive(Debug, Error)]
pub enum SvcError {
    #[error("未命中")]
    NotFound,
    #[error("非法参数: {0}")]
    InvalidArg(String),
    #[error("基础设施错误: {0}")]
    Infra(#[from] Box<sea_orm::DbErr>),
}

impl From<sea_orm::DbErr> for SvcError {
    fn from(err: sea_orm::DbErr) -> Self {
        SvcError::Infra(Box::new(err))
    }
}
