//! 共享地基:全部域 crate 复用的横切能力。
//!
//! - [`error`]:R 响应包络 + 错误码全表(与 Java 字节级对齐)+ 唯一错误位置标识(error_site!)
//!   + 领域服务错误 SvcError 及其 → BizError 的 REST 映射(svc_to_biz)
//! - [`int_enum`]:Java IntEnum/Describe 同构枚举机制(int_enum!)
//! - [`config`]:env 配置装载与校验
//! - [`state`]:进程级共享状态(DB 主/次连接 + Redis)
//! - [`partition`]:分区动态保障(Redis 拦截 + DDL 幂等 + 1526 自愈,所有域 crate 分区表共用)
//! - [`bootstrap`]:dreamy_server 库幂等自举
//! - [`health`]:/healthz /readyz 基础设施端点
//! - [`middleware`]:CORS / R 包络 404 兜底

pub mod bootstrap;
pub mod config;
pub mod error;
pub mod health;
pub mod i18n;
pub mod int_enum;
pub mod middleware;
pub mod partition;
pub mod state;
pub mod time;
