//! 身份域 REST 路由(挂载于 /api/store 与 /api/admin 子树)。
//!
//! P2:store 侧全端点;P3:admin 侧增量挂载。
//! 约定:域 router 自行消费 SharedState(内部 with_state),对外返回已物化的 Router<()>,
//! 装配层(server crate router.rs)零状态耦合地 nest。

pub mod store_account;
pub mod store_auth;

use crate::security::JwtProvider;
use common::middleware::{api_fallback, cors_layer};
use common::state::SharedState;

/// store 域路由状态(SharedState + JwtProvider 合体;axum FromRef 手动桥到分量)
#[derive(Clone)]
pub struct StoreState {
    pub shared: SharedState,
    pub jwt: JwtProvider,
}

impl axum::extract::FromRef<StoreState> for SharedState {
    fn from_ref(state: &StoreState) -> Self {
        state.shared.clone()
    }
}

impl axum::extract::FromRef<StoreState> for JwtProvider {
    fn from_ref(state: &StoreState) -> Self {
        state.jwt.clone()
    }
}

/// /api/store/auth/* 与 /api/store/account/*(P2 全量)
pub fn store_router(state: SharedState, jwt: JwtProvider) -> axum::Router {
    let jwt_for_auth = jwt.clone();
    let jwt_for_account = jwt;
    axum::Router::new()
        .nest("/auth", store_auth::router(state.clone(), jwt_for_auth))
        .nest(
            "/account",
            store_account::router(state.clone(), jwt_for_account),
        )
        .fallback(api_fallback)
        .layer(cors_layer(&state.cfg.store_cors_origin))
        .with_state(())
}

/// /api/admin/*(P3 实现;当前 404 兜底占位)
pub fn admin_router(state: SharedState) -> axum::Router {
    axum::Router::new()
        .fallback(api_fallback)
        .layer(cors_layer(&state.cfg.admin_cors_origin))
        .with_state(())
}

/// 无 JWT 密钥时的降级挂载(骨架/无密钥环境):认证端点全 40100,config 仍可用
pub fn store_router_no_jwt(state: SharedState) -> axum::Router {
    axum::Router::new()
        .fallback(api_fallback)
        .layer(cors_layer(&state.cfg.store_cors_origin))
        .with_state(())
}
