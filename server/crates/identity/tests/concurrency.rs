//! 并发稳定性证据测试(遗留任务 4;V3 并发 refresh 乐观锁的行为证明):
//!
//! ```bash
//! # 需真实 MySQL(dreamy_server 库;Redis 可选,V3 断言为纯 DB 层)
//! DB_PASSWORD=<pwd> SERVER_DB_HOST=127.0.0.1 SERVER_DB_PORT=3307 \
//!   IDENTITY_CONCURRENCY=1 cargo test -p identity --test concurrency -- --nocapture
//! ```
//!
//! 未设置 IDENTITY_CONCURRENCY 时自动跳过(cargo test 全量跑不依赖外部服务)。
//! 场景:
//! 1. 同 refresh_token 并发 10 次刷新 → 仅一成功,其余 40102;冷备行 version 恰好 +1
//! 2. 同 email 并发 10 次 OTP 消费 → 仅一成功,其余 41001;码终态 consumed

use std::sync::Arc;

use common::config::Config;
use common::state::{AppState, SharedState};
use sea_orm::{ActiveModelTrait, ColumnTrait, Database, DatabaseConnection, EntityTrait, QueryFilter, QueryTrait, Set};

use identity::entity::{otp_code, user, user_session};
use identity::enums::{AuthProvider, OtpStatus, SessionStatus, UserStatus, UserTier};
use identity::security::JwtProvider;
use identity::service::{auth, otp};

fn env(key: &str, default: &str) -> String {
    std::env::var(key).unwrap_or_else(|_| default.to_string())
}

async fn shared_state() -> Option<(SharedState, DatabaseConnection)> {
    if std::env::var("IDENTITY_CONCURRENCY").is_err() {
        return None;
    }
    let url = format!(
        "mysql://{}:{}@{}:{}/{}",
        env("DB_USER", "root"),
        env("DB_PASSWORD", ""),
        env("SERVER_DB_HOST", "127.0.0.1"),
        env("SERVER_DB_PORT", "3307"),
        env("SERVER_DB_NAME", "dreamy_server"),
    );
    let db = Database::connect(&url).await.expect("connect dreamy_server");
    // Redis 置 None:V3 乐观锁断言为纯 DB 层语义;Redis 路径(rotation 标记/撤销)由 E2E 覆盖
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
        store_jwt_secret: Some("conc-test-store-secret-0123456789abcdef".into()),
        admin_jwt_secret: Some("conc-test-admin-secret-0123456789abcdef".into()),
        store_cors_origin: String::new(),
        admin_cors_origin: String::new(),
        schema_path: String::new(),
    };
    let state: SharedState = Arc::new(AppState {
        db: db.clone(),
        redis: None,
        cfg,
    });
    Some((state, db))
}

fn now() -> chrono::NaiveDateTime {
    chrono::Local::now().naive_local()
}

/// 场景 1:同 refresh_token 并发 10 次刷新,仅一成功(40102 收敛 + version 恰 +1)
#[tokio::test]
async fn concurrent_refresh_only_one_wins() {
    let Some((state, _db)) = shared_state().await else {
        return;
    };
    let jwt = JwtProvider::new(&state.cfg).expect("jwt provider");

    // 造用户(独立 email,幂等隔离)
    let email = format!("conc-refresh-{}@dreamy.test", std::process::id());
    let u = user::ActiveModel {
        email: Set(email),
        email_verified: Set(1),
        tier: Set(UserTier::Regular.code() as i8),
        status: Set(UserStatus::Active.code() as i8),
        anonymized: Set(0),
        version: Set(0),
        joined_at: Set(Some(now())),
        ..Default::default()
    }
    .insert(&state.db)
    .await
    .expect("seed user");

    // 造会话行(version=0 起点)
    let suffix = uuid::Uuid::new_v4().to_string();
    let row = user_session::ActiveModel {
        user_id: Set(u.id),
        token_id: Set(format!("conc-a-{suffix}")),
        refresh_token_id: Set(Some(format!("conc-r-{suffix}"))),
        access_expires_at: Set(Some(now() + chrono::Duration::hours(1))),
        refresh_expires_at: Set(Some(now() + chrono::Duration::days(30))),
        device: Set(None),
        browser: Set(None),
        ip: Set(Some("127.0.0.1".into())),
        is_new_device: Set(0),
        method: Set(AuthProvider::Email.code() as i8),
        status: Set(SessionStatus::Active.code() as i8),
        version: Set(0),
        ..Default::default()
    }
    .insert(&state.db)
    .await
    .expect("seed session");
    let refresh_jti = format!("conc-r-{suffix}");
    let refresh_token = jwt
        .issue_store(u.id as i64, &refresh_jti, "email", true, 86_400)
        .expect("issue refresh token");

    // 并发 10 刷新
    let mut handles = Vec::new();
    for _ in 0..10 {
        let st = state.clone();
        let jw = jwt.clone();
        let tk = refresh_token.clone();
        handles.push(tokio::spawn(async move { auth::refresh(&st, &jw, &tk).await }));
    }
    let mut ok = 0usize;
    let mut rejected = 0usize;
    for h in handles {
        match h.await.expect("task join") {
            Ok(_) => ok += 1,
            Err(e) => {
                assert_eq!(
                    e.biz_code(),
                    Some(40102),
                    "并发输家必须收敛 40102,实际 {e:?}"
                );
                rejected += 1;
            }
        }
    }
    println!("[concurrency] refresh 并发 10:成功 {ok},40102 拒绝 {rejected}");
    assert_eq!(ok, 1, "同 refresh_token 并发刷新必须仅一成功");
    assert_eq!(ok + rejected, 10);

    // 终态:version 恰好 +1(无重复旋转)
    let after = user_session::Entity::find_by_id(row.id)
        .one(&state.db)
        .await
        .expect("query")
        .expect("row exists");
    assert_eq!(after.version, 1, "version 应恰好自增一次");
    assert_ne!(
        after.refresh_token_id.as_deref(),
        Some(refresh_jti.as_str()),
        "旧 refresh jti 必须已被旋转掉"
    );

    // 清理
    user_session::Entity::delete_by_id(row.id)
        .exec(&state.db)
        .await
        .ok();
    user::Entity::delete_by_id(u.id).exec(&state.db).await.ok();
}

/// 场景 2:同 email 并发 10 次 OTP 消费,仅一成功(其余 41001)
#[tokio::test]
async fn concurrent_otp_consume_only_one_wins() {
    let Some((state, _db)) = shared_state().await else {
        return;
    };

    let email = format!("conc-otp-{}@dreamy.test", std::process::id());
    let code_hash = bcrypt::hash("424242", 4).expect("hash");
    let ts = now();
    common::partition::insert_self_heal(
        &state,
        "otp_code",
        &[ts],
        otp_code::Entity::insert(otp_code::ActiveModel {
            email: Set(email.clone()),
            code_hash: Set(code_hash),
            length: Set(6),
            expires_at: Set(ts + chrono::Duration::minutes(10)),
            attempts: Set(0),
            max_attempts: Set(5),
            status: Set(OtpStatus::Pending.code() as i8),
            last_sent_at: Set(Some(ts)),
            version: Set(0),
            created_at: Set(ts),
            updated_at: Set(Some(ts)),
            ..Default::default()
        })
        .build(sea_orm::DatabaseBackend::MySql),
    )
    .await
    .expect("seed otp_code");

    // 并发 10 消费(同 email 进程内锁串行;首个成功置 consumed,后续查无 pending → 41001)
    let mut handles = Vec::new();
    for _ in 0..10 {
        let st = state.clone();
        let em = email.clone();
        handles.push(tokio::spawn(async move {
            otp::consume_for_login(&st, &em, "424242").await
        }));
    }
    let mut ok = 0usize;
    let mut rejected = 0usize;
    for h in handles {
        match h.await.expect("task join") {
            Ok(()) => ok += 1,
            Err(e) => {
                assert_eq!(e.biz_code(), Some(41001), "输家必须收敛 41001,实际 {e:?}");
                rejected += 1;
            }
        }
    }
    println!("[concurrency] OTP 并发消费 10:成功 {ok},拒绝 {rejected}");
    assert_eq!(ok, 1, "同码并发消费必须仅一成功");
    assert_eq!(ok + rejected, 10);

    // 终态:码 consumed
    let left = otp_code::Entity::find()
        .filter(otp_code::Column::Email.eq(email.clone()))
        .all(&state.db)
        .await
        .expect("query");
    assert!(left
        .iter()
        .all(|r| r.status == OtpStatus::Consumed.code() as i8));

    // 清理
    otp_code::Entity::delete_many()
        .filter(otp_code::Column::Email.eq(email))
        .exec(&state.db)
        .await
        .ok();
}
