//! 身份域(用户/认证/admin RBAC)——从 Java 迁移的第一个域。
//!
//! 分层约定(全部域 crate 沿用):
//! - `api`:axum 路由与 handler(REST 契约复刻,不含业务规则)
//! - `service`:领域服务(业务规则、事务边界;P1-P3 从 Java 对应 Service 移植)
//! - `entity`:SeaORM 实体(主库 dreamy_server,11 张;由 sea-orm-cli 从真实 schema 生成)
//! - `entity_legacy`:次库 identity 共享过渡表实体(operation_log/email_template,
//!   查询走次库连接;Java 全退役时两表归并主库)
//! - `grpc`:IdentityGate 内部通道实现(Java backend 调用)
//! - 其他域内支撑件(mail/oidc/ratelimit)随 P2/P3 落地
//!
//! 对外 REST 路由以 store/admin 两个子树挂载到 server crate 的 router。

pub mod api;
pub mod entity;
pub mod entity_legacy;
pub mod enums;
pub mod grpc;
pub mod security;
pub mod service;
