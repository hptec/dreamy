//! 组装与启动(唯一 bin crate,不含业务逻辑):
//! 配置装载 → 库自举与连接 → 域路由挂载 → REST/gRPC 双服务。
//!
//! 新域入驻:crates/<domain> 落地后,在本 crate router.rs 挂载其子路由、
//! main.rs 注册其 gRPC 服务(如有)。

mod router;

use common::state::{AppState, SharedState};

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .json()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into()),
        )
        .init();

    let cfg = match common::config::Config::from_env() {
        Ok(c) => c,
        Err(err) => {
            tracing::error!("[boot] 配置校验失败:{err}");
            std::process::exit(1);
        }
    };

    tracing::info!(
        http_port = cfg.http_port,
        grpc_port = cfg.grpc_port,
        db = %cfg.db_name,
        db_legacy = %cfg.db_legacy_name,
        "[boot] dreamy-server 启动中"
    );

    let db = match common::bootstrap::connect_main(&cfg).await {
        Ok(conn) => conn,
        Err(err) => {
            tracing::error!("[boot] 主库连接/自举失败:{err}");
            std::process::exit(1);
        }
    };
    let db_legacy = common::bootstrap::connect_legacy(&cfg).await;
    let redis = redis::Client::open(cfg.redis_url()).ok();

    let state: SharedState = std::sync::Arc::new(AppState {
        db: db.clone(),
        db_legacy,
        redis,
        cfg: cfg.clone(),
    });

    let grpc_addr = std::net::SocketAddr::from(([0, 0, 0, 0], cfg.grpc_port));
    let grpc_db = db.clone();
    let grpc_task = tokio::spawn(async move {
        let svc = proto::dreamy::identity::v1::identity_gate_server::IdentityGateServer::new(
            identity::grpc::IdentityGateImpl { db: grpc_db },
        );
        if let Err(err) = tonic::transport::Server::builder()
            .add_service(svc)
            .serve_with_shutdown(grpc_addr, shutdown_signal())
            .await
        {
            tracing::error!("[grpc] 服务异常退出:{err}");
            std::process::exit(1);
        }
    });

    let http_addr = std::net::SocketAddr::from(([0, 0, 0, 0], cfg.http_port));
    let listener = match tokio::net::TcpListener::bind(http_addr).await {
        Ok(l) => l,
        Err(err) => {
            tracing::error!("[http] 端口 {} 绑定失败:{err}", http_addr);
            std::process::exit(1);
        }
    };
    tracing::info!(%http_addr, %grpc_addr, "[boot] 就绪");

    let app = router::build(state);
    if let Err(err) = axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await
    {
        tracing::error!("[http] 服务异常退出:{err}");
        std::process::exit(1);
    }
    let _ = grpc_task.await;
    tracing::info!("[boot] 已优雅退出");
}

async fn shutdown_signal() {
    let _ = tokio::signal::ctrl_c().await;
    tracing::info!("[boot] 收到退出信号,开始优雅停机");
}
