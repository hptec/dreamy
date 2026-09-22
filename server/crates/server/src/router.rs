//! 总路由装配:基础设施端点 + 各域子路由 + 全局中间件(安全头/请求 ID)。
//! 新域入驻在此挂载(见 crate 顶部注释)。

use axum::http::{header, header::HeaderValue};
use axum::routing::get;
use axum::Router;
use tower_http::request_id::{MakeRequestUuid, PropagateRequestIdLayer, SetRequestIdLayer};
use tower_http::set_header::SetResponseHeaderLayer;

use common::state::SharedState;

pub fn build(state: SharedState) -> Router {
    // 域 router 自行消费 SharedState(内部 with_state)返回已物化的 Router<()>;
    // 装配层:基础设施路由消费状态 → 嵌入各域 → 全局中间件,零状态耦合。
    // JwtProvider:P2 起签发必需;缺失时 store 域以无签发模式挂载(账户端点全 401)
    let jwt = identity::security::JwtProvider::new(&state.cfg).ok();
    let store_api = match jwt.clone() {
        Some(jwt) => identity::api::store_router(state.clone(), jwt),
        None => identity::api::store_router_no_jwt(state.clone()),
    };
    let admin_api = match jwt.clone() {
        Some(jwt) => identity::api::admin_router(state.clone(), jwt),
        None => axum::Router::new()
            .fallback(common::middleware::api_fallback)
            .layer(tower_http::cors::CorsLayer::permissive()),
    };
    // trading 底座(store JWT 鉴权域路由;与 identity::api 同款双模挂载)
    let trading_api = match jwt.clone() {
        Some(jwt) => marketing::api_trading::address_router(state.clone(), jwt),
        None => axum::Router::new(),
    };
    // catalog 域:admin CRUD(JWT + RBAC)+ store 公开树
    let category_admin_api = match jwt.clone() {
        Some(jwt) => marketing::api_category::admin_router(state.clone(), jwt),
        None => axum::Router::new(),
    };
    // collection 域:admin 分组/集合/商品挂载 + store 导航(E-CAT-07 真实现覆盖占位)
    let tax_admin_api = match jwt.clone() {
        Some(jwt) => marketing::api_tax::admin_router(state.clone(), jwt),
        None => axum::Router::new(),
    };
    let payment_api = match jwt.clone() {
        Some(jwt) => marketing::api_payment::store_router(state.clone(), jwt),
        None => axum::Router::new(),
    };
    let shipment_admin_api = match jwt.clone() {
        Some(jwt) => marketing::api_shipment::admin_router(state.clone(), jwt),
        None => axum::Router::new(),
    };
    let refund_admin_api = match jwt.clone() {
        Some(jwt) => marketing::api_payment::admin_router(state.clone(), jwt),
        None => axum::Router::new(),
    };
    let order_api = match jwt.clone() {
        Some(jwt) => marketing::api_order::router(state.clone(), jwt),
        None => axum::Router::new(),
    };
    let checkout_api = match jwt.clone() {
        Some(jwt) => marketing::api_checkout::router(state.clone(), jwt),
        None => axum::Router::new(),
    };
    let cart_api = match jwt.clone() {
        Some(jwt) => marketing::api_cart::router(state.clone(), jwt),
        None => axum::Router::new(),
    };
    let content_store_api = marketing::api_content::store_router().with_state(state.clone());
    let content_admin_api = match jwt.clone() {
        Some(jwt) => marketing::api_content::admin_router(state.clone(), jwt),
        None => axum::Router::new(),
    };
    let banner_admin_api = match jwt.clone() {
        Some(jwt) => marketing::api_banner::admin_router(state.clone(), jwt),
        None => axum::Router::new(),
    };
    let attribute_admin_api = match jwt.clone() {
        Some(jwt) => marketing::api_attribute::admin_router(state.clone(), jwt),
        None => axum::Router::new(),
    };
    let collection_admin_api = match jwt {
        Some(jwt) => marketing::api_collection::admin_router(state.clone(), jwt),
        None => axum::Router::new(),
    };

    let infra = Router::new()
        .route("/healthz", get(common::health::healthz))
        .route("/readyz", get(common::health::readyz))
        .with_state(state.clone());
    infra
        .merge(marketing::api::router(state.clone()))
        .merge(trading_api)
        .merge(category_admin_api)
        .merge(collection_admin_api)
        .merge(attribute_admin_api)
        .merge(banner_admin_api)
        .merge(cart_api)
        .merge(checkout_api)
        .merge(order_api)
        .merge(payment_api)
        .merge(refund_admin_api)
        .merge(shipment_admin_api)
        .merge(marketing::api_shipment::track_router().with_state(state.clone()))
        .merge(content_store_api)
        .merge(content_admin_api)
        .merge(tax_admin_api)
        .merge(marketing::api_category::store_router().with_state(state.clone()))
        .merge(marketing::api_product::store_router().with_state(state.clone()))
        .merge(marketing::api_collection::store_router().with_state(state))
        .nest("/api/store", store_api)
        .nest("/api/admin", admin_api)
        // 安全响应头三件套(对齐 Java SecurityHeadersFilter;HSTS 由 TLS 网关层负责)
        .layer(SetResponseHeaderLayer::if_not_present(
            header::X_CONTENT_TYPE_OPTIONS,
            HeaderValue::from_static("nosniff"),
        ))
        .layer(SetResponseHeaderLayer::if_not_present(
            header::X_FRAME_OPTIONS,
            HeaderValue::from_static("DENY"),
        ))
        .layer(SetResponseHeaderLayer::if_not_present(
            header::REFERER,
            HeaderValue::from_static("strict-origin-when-cross-origin"),
        ))
        // 请求 ID:上游(网关/Java)已带则透传,否则生成
        .layer(PropagateRequestIdLayer::x_request_id())
        .layer(SetRequestIdLayer::x_request_id(MakeRequestUuid))
}
