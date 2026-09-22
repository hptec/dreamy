//! 组装与启动(唯一 bin crate,不含业务逻辑):
//! 配置装载 → 库自举与连接 → 域路由挂载 → REST/gRPC 双服务。
//!
//! 新域入驻:crates/<domain> 落地后,在本 crate router.rs 挂载其子路由、
//! main.rs 注册其 gRPC 服务(如有)。

mod router;

use common::state::{AppState, SharedState};

#[tokio::main]
async fn main() {
    // WorkerGuard 须持有到进程结束:drop 时才 flush non_blocking 缓冲,提前丢弃会丢日志
    let _log_guard = init_logging();

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
        "[boot] dreamy-server 启动中"
    );

    let db = match common::bootstrap::connect_main(&cfg).await {
        Ok(conn) => conn,
        Err(err) => {
            tracing::error!("[boot] 主库连接/自举失败:{err}");
            std::process::exit(1);
        }
    };
    // 邮件模板种子(幂等:code+locale 存在即跳过;身份 4 code + 业务 16 code)
    identity::seed::seed_mail_templates(&db).await;
    // 身份基线种子(幂等:auth_config 单例 + 权限字典 27 点 + 超管角色/账户;
    // Java DataInitializer 删码后新环境唯一自举来源)
    identity::bootstrap::seed_identity_baseline(&db).await;

    let redis = build_redis_manager(&cfg).await;

    // 业务库(identity;迁移期 catalog/交易域业务表)。缺席不阻塞启动(域功能降级)
    let biz_db = match sea_orm::Database::connect(&cfg.db_biz_url()).await {
        Ok(conn) => conn,
        Err(err) => {
            tracing::warn!("[boot] 业务库({})连接失败,业务域降级:{err}", cfg.biz_db_name);
            db.clone()
        }
    };

    let state: SharedState = std::sync::Arc::new(AppState {
        db: db.clone(),
        biz_db,
        redis,
        cfg: cfg.clone(),
    });

    let grpc_addr = std::net::SocketAddr::from(([0, 0, 0, 0], cfg.grpc_port));
    let grpc_state = state.clone();
    let grpc_task = tokio::spawn(async move {
        // 同一 18083 挂三服务:IdentityGate(会话/用户) + AuditGate/TemplateGate(审计/模板)
        let server = tonic::transport::Server::builder()
            .add_service(
                proto::dreamy::identity::v1::identity_gate_server::IdentityGateServer::new(
                    identity::grpc::IdentityGateImpl {
                        state: grpc_state.clone(),
                    },
                ),
            )
            .add_service(
                proto::dreamy::audit::v1::audit_gate_server::AuditGateServer::new(
                    identity::grpc_support::AuditGateImpl {
                        state: grpc_state.clone(),
                    },
                ),
            )
            .add_service(
                proto::dreamy::mail::v1::template_gate_server::TemplateGateServer::new(
                    identity::grpc_support::TemplateGateImpl {
                        state: grpc_state.clone(),
                    },
                ),
            );
        if let Err(err) = server
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

async fn build_redis_manager(
    cfg: &common::config::Config,
) -> Option<redis::aio::ConnectionManager> {
    match redis::Client::open(cfg.redis_url()) {
        Ok(client) => {
            // ConnectionManager 对不可达地址会无限重试——超时兜底,避免启动卡死
            match tokio::time::timeout(
                std::time::Duration::from_secs(5),
                redis::aio::ConnectionManager::new(client),
            )
            .await
            {
                Ok(Ok(manager)) => Some(manager),
                Ok(Err(err)) => {
                    tracing::warn!("[boot] Redis 不可达(缓存/频控降级 DB):{err}");
                    None
                }
                Err(_) => {
                    tracing::warn!("[boot] Redis 连接超时 5s(降级 DB,后续连接由管理器自动重试)");
                    None
                }
            }
        }
        Err(err) => {
            tracing::warn!("[boot] Redis 地址非法({}):{err}", cfg.redis_url());
            None
        }
    }
}

async fn shutdown_signal() {
    let _ = tokio::signal::ctrl_c().await;
    tracing::info!("[boot] 收到退出信号,开始优雅停机");
}

/// 日志双写:stdout(容器 docker logs)+ 按天滚动文件(SERVER_LOG_DIR,默认 ../logs)。
/// 默认值口径与 Java bootRun 对称(cwd=server/,../logs 指向项目根 logs/);
/// 容器由 compose 注入 SERVER_LOG_DIR=/app/logs。目录不可用时降级仅 stdout,不阻断启动。
/// 返回 WorkerGuard,调用方须持有到进程结束。
fn init_logging() -> Option<tracing_appender::non_blocking::WorkerGuard> {
    use tracing_subscriber::layer::SubscriberExt;
    use tracing_subscriber::util::SubscriberInitExt;

    let filter =
        tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into());
    let log_dir = std::env::var("SERVER_LOG_DIR").unwrap_or_else(|_| "../logs".into());

    // prefix + 日期 + suffix → dreamy-server.2026-09-17.log
    let (file_layer, guard) = match tracing_appender::rolling::RollingFileAppender::builder()
        .rotation(tracing_appender::rolling::Rotation::DAILY)
        .filename_prefix("dreamy-server")
        .filename_suffix("log")
        .build(&log_dir)
    {
        Ok(appender) => {
            let (writer, guard) = tracing_appender::non_blocking(appender);
            (
                Some(tracing_subscriber::fmt::layer().json().with_writer(writer)),
                Some(guard),
            )
        }
        Err(err) => {
            eprintln!("[boot] 日志目录 {log_dir} 不可用,降级仅 stdout:{err}");
            (None, None)
        }
    };

    tracing_subscriber::registry()
        .with(filter)
        .with(tracing_subscriber::fmt::layer().json())
        .with(file_layer)
        .init();

    guard
}
