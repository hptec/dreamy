//! 基础设施健康端点:/healthz(liveness)与 /readyz(readiness)。

use axum::extract::State;
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::Json;
use sea_orm::{ConnectionTrait, DatabaseConnection, Statement};

use crate::state::SharedState;

/// liveness:进程存活即 200(不查依赖,避免依赖抖动引发容器重启风暴)
pub async fn healthz() -> impl IntoResponse {
    Json(serde_json::json!({ "status": "ok" }))
}

async fn db_ping(conn: &DatabaseConnection) -> bool {
    conn.query_one(Statement::from_string(
        sea_orm::DatabaseBackend::MySql,
        "SELECT 1",
    ))
    .await
    .is_ok()
}

async fn redis_ping(client: &redis::Client) -> bool {
    match client.get_multiplexed_tokio_connection().await {
        Ok(mut conn) => redis::cmd("PING")
            .query_async::<String>(&mut conn)
            .await
            .map(|v| v.eq_ignore_ascii_case("PONG"))
            .unwrap_or(false),
        Err(_) => false,
    }
}

/// readiness:DB 主/次连接 + Redis 连通性(compose healthcheck 与 deploy 探活挂钩)
pub async fn readyz(State(state): State<SharedState>) -> impl IntoResponse {
    let mut checks = serde_json::Map::new();

    let db_main = db_ping(&state.db).await;
    checks.insert("db_main".into(), serde_json::json!(db_main));

    let db_legacy = match &state.db_legacy {
        Some(conn) => db_ping(conn).await,
        None => false,
    };
    checks.insert("db_legacy".into(), serde_json::json!(db_legacy));

    let redis_ok = match &state.redis {
        Some(client) => redis_ping(client).await,
        None => false,
    };
    checks.insert("redis".into(), serde_json::json!(redis_ok));

    // 主库为硬依赖;次库/Redis 允许暂缺(共享表端点在 P2/P3 自行降级)
    let ready = db_main;
    let status = if ready { "ready" } else { "degraded" };
    let body = serde_json::json!({ "status": status, "checks": checks });
    if ready {
        (StatusCode::OK, Json(body)).into_response()
    } else {
        (StatusCode::SERVICE_UNAVAILABLE, Json(body)).into_response()
    }
}
