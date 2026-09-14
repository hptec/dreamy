//! 身份域领域服务:业务规则与数据访问(P1-P3 从 Java 对应 Service 移植)。
//!
//! 性能纪律(用户指令):每个接口 Redis-first + 主动失效,Redis 故障一律降级 DB 不失败。

pub mod auth;
pub mod authconfig;
pub mod demo_user;
pub mod merge;
pub mod otp;
pub mod partition_maintain;
pub mod permissions;
pub mod ratelimit;
pub mod session;
pub mod user_query;

pub use user_query::{Col, Cond, CondVal, ListQuery, Lookup, UserView};

use thiserror::Error;

/// 领域服务错误(REST 层映射:NotFound→40400,InvalidArg→40000,Code→对应业务码,Infra→50001;
/// gRPC 层映射:NotFound→NOT_FOUND,InvalidArg→INVALID_ARGUMENT,其余→UNAVAILABLE)
/// DbErr 装箱:控制错误体积(Rust 惯例),`?` 自动转换不受影响
#[derive(Debug, Error)]
pub enum SvcError {
    #[error("未命中")]
    NotFound,
    #[error("非法参数: {0}")]
    InvalidArg(String),
    /// 业务错误码(wire 契约值,如 40101/40902/42901)+ REST details(gRPC 查询路径不产生此变体)
    #[error("业务错误 {code}")]
    Code {
        code: i32,
        details: Option<serde_json::Value>,
    },
    #[error("基础设施错误: {0}")]
    Infra(#[from] Box<sea_orm::DbErr>),
}

impl SvcError {
    /// 业务码便捷构造(details 缺省 None)
    pub fn code(code: i32) -> Self {
        SvcError::Code {
            code,
            details: None,
        }
    }

    /// 业务码 + details(如 40101 remaining_attempts)
    pub fn code_with(code: i32, details: serde_json::Value) -> Self {
        SvcError::Code {
            code,
            details: Some(details),
        }
    }
}

impl From<sea_orm::DbErr> for SvcError {
    fn from(err: sea_orm::DbErr) -> Self {
        SvcError::Infra(Box::new(err))
    }
}
