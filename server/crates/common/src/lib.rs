//! 共享地基:全部域 crate 复用的横切能力。
//!
//! - [`error`]:R 响应包络 + 错误码全表(与 Java 字节级对齐)+ 唯一错误位置标识
//! - [`config`]:env 配置装载与校验
//! - [`state`]:进程级共享状态(DB 主/次连接 + Redis)
//! - [`bootstrap`]:dreamy_server 库幂等自举
//! - [`health`]:/healthz /readyz 基础设施端点
//! - [`middleware`]:CORS / R 包络 404 兜底

pub mod bootstrap;
pub mod config;
pub mod error;
pub mod health;
pub mod middleware;
pub mod state;
