//! 身份域(用户/认证/admin RBAC)——从 Java 迁移的第一个域。
//!
//! 分层约定(全部域 crate 沿用):
//! - `api`:axum 路由与 handler(REST 契约复刻,不含业务规则)
//! - `service`:领域服务(业务规则、事务边界;P1-P3 从 Java 对应 Service 移植)
//! - `entity`:SeaORM 实体(P1 按 schema 冻结件生成)
//! - `grpc`:IdentityGate 内部通道实现(Java backend 调用)
//! - 其余域内支撑件(mail/oidc/ratelimit)随 P2/P3 落地
//!
//! 对外 REST 路由以 store/admin 两个子树挂载到 server crate 的 router。

pub mod api;
pub mod grpc;
