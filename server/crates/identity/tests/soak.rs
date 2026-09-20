//! 内存泄漏 soak 流量驱动(遗留任务 3;IDENTITY_SOAK=1 闸门):
//! 50 并发混合流量持续打认证/查询接口 30 分钟;RSS/Redis/MySQL 水位由
//! scripts/bench-soak.sh 外部采样(容器指标),本测试只负责稳定流量。
//!
//! ```bash
//! DB_PASSWORD=<pwd> SERVER_DB_HOST=127.0.0.1 SERVER_DB_PORT=3307 IDENTITY_SOAK=1 \
//!   cargo test -p identity --test soak --release -- --nocapture --ignored
//! ```

use std::sync::Arc;
use std::time::Duration;

use common::config::Config;
use sea_orm::{ActiveModelTrait, ColumnTrait, Database, EntityTrait, QueryFilter, Set};

use identity::entity::{user, user_session};
use identity::enums::{AuthProvider, SessionStatus, UserStatus, UserTier};
use identity::security::JwtProvider;

const CONCURRENCY: usize = 50;
const SOAK_SECS: u64 = 30 * 60;

fn env(key: &str, default: &str) -> String {
    std::env::var(key).unwrap_or_else(|_| default.to_string())
}

#[tokio::test]
async fn soak_mixed_traffic() {
    if std::env::var("IDENTITY_SOAK").is_err() {
        return;
    }
    let base = env("SERVER_LOAD_ADDR", "http://127.0.0.1:18082");
    let url = format!(
        "mysql://{}:{}@{}:{}/{}",
        env("DB_USER", "root"),
        env("DB_PASSWORD", ""),
        env("SERVER_DB_HOST", "127.0.0.1"),
        env("SERVER_DB_PORT", "3307"),
        env("SERVER_DB_NAME", "dreamy_server"),
    );
    let db = Database::connect(&url).await.expect("connect");
    let cfg = Config {
        http_port: 0,
        grpc_port: 0,
        db_host: String::new(),
        db_port: 0,
        db_name: String::new(),
        db_user: String::new(),
        db_password: String::new(),
        redis_host: String::new(),
        redis_port: 0,
        store_jwt_secret: Some("soak-test-store-secret-0123456789abcdef".into()),
        admin_jwt_secret: Some("soak-test-admin-secret-0123456789abcdef".into()),
        store_cors_origin: String::new(),
        admin_cors_origin: String::new(),
        schema_path: String::new(),
    };
    let state: Arc<common::state::AppState> = Arc::new(common::state::AppState {
        db: db.clone(),
        redis: None,
        cfg,
    });
    let jwt = JwtProvider::new(&state.cfg).expect("jwt");

    // 50 个 soak 用户/会话链(幂等)
    let ts = chrono::Local::now().naive_local();
    let mut chains = Vec::with_capacity(CONCURRENCY);
    for i in 0..CONCURRENCY {
        let email = format!("soak-v{i}@load.test");
        let uid = match user::Entity::find()
            .filter(user::Column::Email.eq(email.clone()))
            .one(&db)
            .await
            .expect("q")
        {
            Some(u) => u.id,
            None => {
                let u = user::ActiveModel {
                    email: Set(email.clone()),
                    email_verified: Set(1),
                    tier: Set(UserTier::Regular.code() as i8),
                    status: Set(UserStatus::Active.code() as i8),
                    anonymized: Set(0),
                    version: Set(0),
                    joined_at: Set(Some(ts)),
                    ..Default::default()
                }
                .insert(&db)
                .await
                .expect("seed");
                u.id
            }
        };
        let access_jti = format!("soak-a{i}");
        let refresh_jti = format!("soak-r{i}");
        let exists = user_session::Entity::find()
            .filter(user_session::Column::TokenId.eq(access_jti.clone()))
            .one(&db)
            .await
            .expect("q")
            .is_some();
        if !exists {
            user_session::ActiveModel {
                user_id: Set(uid),
                token_id: Set(access_jti.clone()),
                refresh_token_id: Set(Some(refresh_jti.clone())),
                access_expires_at: Set(Some(ts + chrono::Duration::hours(2))),
                refresh_expires_at: Set(Some(ts + chrono::Duration::days(30))),
                device: Set(Some("soak".into())),
                browser: Set(Some("soak-test".into())),
                ip: Set(Some("127.0.0.1".into())),
                is_new_device: Set(0),
                method: Set(AuthProvider::Email.code() as i8),
                status: Set(SessionStatus::Active.code() as i8),
                version: Set(0),
                ..Default::default()
            }
            .insert(&db)
            .await
            .expect("seed session");
        }
        let refresh_token = jwt
            .issue_store(uid as i64, &refresh_jti, "email", true, 86_400)
            .expect("issue");
        chains.push((email, refresh_token));
    }

    // 每会话链 token 需在 Redis 有 access 主存,否则 refresh 旋转链经冷备仍可推进(refresh 走冷备校验)
    println!("[soak] 50 并发 × {SOAK_SECS}s 混合流量启动(config/refresh/health)…");
    let deadline = tokio::time::Instant::now() + Duration::from_secs(SOAK_SECS);
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(10))
        .pool_max_idle_per_host(CONCURRENCY)
        .build()
        .expect("client");
    let mut handles = Vec::with_capacity(CONCURRENCY);
    for (i, (_email, token)) in chains.into_iter().enumerate() {
        let c = client.clone();
        let base = base.clone();
        let token = token.clone();
        handles.push(tokio::spawn(async move {
            let mut refresh_token = token;
            let mut n_req: u64 = 0;
            while tokio::time::Instant::now() < deadline {
                // 混合:i%3==0 → config;否则 refresh(旋转链推进)
                if i % 3 == 0 {
                    let _ = c.get(format!("{base}/api/store/auth/config")).send().await;
                } else {
                    let resp = c
                        .post(format!("{base}/api/store/auth/refresh"))
                        .json(&serde_json::json!({ "refresh_token": refresh_token }))
                        .send()
                        .await;
                    // 旋转链推进:成功则换新 token(401/5xx 保持旧 token 重试)
                    if let Ok(resp) = resp {
                        if let Ok(j) = resp.json::<serde_json::Value>().await {
                            if let Some(t) = j["data"]["tokens"]["refresh_token"].as_str() {
                                refresh_token = t.to_string();
                            }
                        }
                    }
                }
                n_req += 1;
                // 稳态速率控制:每 worker ~10 QPS;请求失败稍作退避
                let backoff = if n_req % 7 == 0 { 200 } else { 100 };
                tokio::time::sleep(Duration::from_millis(backoff)).await;
            }
            n_req
        }));
    }
    let mut total = 0u64;
    for h in handles {
        total += h.await.unwrap_or(0);
    }
    println!("[soak] 完成:总请求 {total};RSS/Redis/MySQL 采样见 data/soak-samples.csv");
}
