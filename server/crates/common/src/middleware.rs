//! 横切中间件:CORS(分域)、R 包络 404 兜底。

use axum::http::{header, header::HeaderValue, Method};
use axum::response::IntoResponse;
use tower_http::cors::{AllowOrigin, CorsLayer};

/// CORS(对齐 Java storeCorsFilter/adminCorsFilter:GET/POST/PUT/DELETE/PATCH/OPTIONS,
/// Authorization/Content-Type/Accept-Language,credentials;origin 支持逗号分隔多值)
pub fn cors_layer(origins: &str) -> CorsLayer {
    let list: Vec<HeaderValue> = origins
        .split(',')
        .map(|s| s.trim())
        .filter(|s| !s.is_empty())
        .filter_map(|s| s.parse::<HeaderValue>().ok())
        .collect();
    CorsLayer::new()
        .allow_origin(AllowOrigin::list(list))
        .allow_methods([
            Method::GET,
            Method::POST,
            Method::PUT,
            Method::DELETE,
            Method::PATCH,
            Method::OPTIONS,
        ])
        .allow_headers([
            header::AUTHORIZATION,
            header::CONTENT_TYPE,
            header::ACCEPT_LANGUAGE,
        ])
        .allow_credentials(true)
}

crate::error_site!(pub SITE_ROUTE_FALLBACK = "common/route/fallback");

/// 未知 API 路径统一走 R 包络 404(对齐 Java 无路由 40400)
pub async fn api_fallback() -> impl IntoResponse {
    crate::error::BizError::new(SITE_ROUTE_FALLBACK, crate::error::ErrorCode::NotFound)
}
