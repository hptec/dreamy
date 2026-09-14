//! 身份域 REST 路由(挂载于 /api/store 与 /api/admin 子树)。
//! P0 骨架:仅分域 CORS + R 包络 404 兜底;业务端点 P2(store)/P3(admin)增量挂载。
//!
//! 约定:域 router 自行消费 SharedState(内部 with_state),对外返回已物化的 Router<()>,
//! 装配层(server crate router.rs)零状态耦合地 nest。

use axum::Router;
use common::middleware::{api_fallback, cors_layer};
use common::state::SharedState;

/// /api/store/auth/* 与 /api/store/account/*(P2 实现)
pub fn store_router(state: SharedState) -> Router {
    Router::new()
        .fallback(api_fallback)
        .layer(cors_layer(&state.cfg.store_cors_origin))
        .with_state(state)
}

/// /api/admin/{auth,auth-config,admins,roles,permissions,users,operation-logs}/*(P3 实现)
pub fn admin_router(state: SharedState) -> Router {
    Router::new()
        .fallback(api_fallback)
        .layer(cors_layer(&state.cfg.admin_cors_origin))
        .with_state(state)
}
