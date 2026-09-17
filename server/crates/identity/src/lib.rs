//! 身份域(用户/认证/admin RBAC)——从 Java 迁移的第一个域。
//!
//! 分层约定(全部域 crate 沿用):
//! - `api`:axum 路由与 handler(REST 契约复刻,不含业务规则)
//! - `service`:领域服务(业务规则、事务边界;P1-P3 从 Java 对应 Service 移植)
//! - `entity`:SeaORM 实体(主库 dreamy_server,11 张;由 sea-orm-cli 从真实 schema 生成)
//! - `grpc`:IdentityGate 内部通道实现(Java backend 调用)
//! - `grpc_support`:AuditGate/TemplateGate 内部通道实现(Java 业务侧审计/邮件模板)
//! - `seed`:邮件模板启动种子(email_template 自 identity 库收编主库)
//! - `bootstrap`:身份基线种子(auth_config/权限字典/超管角色/超管账户;Java DataInitializer 删码后的唯一自举来源)
//! - 其他域内支撑件(mail/oidc/ratelimit)随 P2/P3 落地
//!
//! 对外 REST 路由以 store/admin 两个子树挂载到 server crate 的 router。

pub mod api;
pub mod bootstrap;
pub mod entity;
pub mod enums;
pub mod grpc;
pub mod grpc_support;
pub mod mail;
pub mod oidc;
pub mod security;
pub mod seed;
pub mod service;
