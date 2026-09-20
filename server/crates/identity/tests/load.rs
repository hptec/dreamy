//! login 专项压测(遗留任务 2;IDENTITY_LOAD=1 闸门,未设置自动跳过):
//!
//! ```bash
//! DB_PASSWORD=<pwd> SERVER_DB_HOST=127.0.0.1 SERVER_DB_PORT=3307 \
//!   IDENTITY_LOAD=1 cargo test -p identity --test load --release -- --nocapture
//! ```
//!
//! 打真实 HTTP(默认 http://127.0.0.1:18082)。令牌全部由 server 签发
//! (setup 经直插码+HTTP verify 登录;压测进程密钥与 server 不同,自签必 40102)。
//! 场景:
//! - S1 config 读:无频控,读路径容量主力
//! - S2 refresh 写:无频控,登录态最重写链(冷备双 SELECT+乐观锁 UPDATE+Redis 双写)
//! - S3 send-otp:预置 resend 冷却键 → 稳态即频控拒绝路径(42901,Redis 热路径)
//! - S5 admin-login:不存在账号恒定 bcrypt 路径;IP 熔断后转 42903 拒绝路径
//! - S6 admin sessions:user_detail 冷备查询
//! - S7 ListUsers 分页:首页/深分页交替(千万行下 COUNT 缓存 + 分页排序)
//! - S4 verify 全链:小样本(42904 IP 频控 300/min 上限内)
//! 阶梯 50/100/300/500 × 20s。

use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

use common::config::Config;
use sea_orm::{ActiveModelTrait, ColumnTrait, Database, EntityTrait, QueryFilter};
use tokio::sync::Mutex;

use identity::entity::{user, user_session};
use identity::enums::{UserStatus, UserTier};

fn env(key: &str, default: &str) -> String {
    std::env::var(key).unwrap_or_else(|_| default.to_string())
}

const STAGES: &[usize] = &[50, 100, 300, 500];
const STAGE_SECS: u64 = 20;
const WARMUP_SECS: u64 = 3;

#[derive(Default)]
struct Samples {
    micros: Mutex<Vec<u128>>,
    status: Mutex<std::collections::HashMap<u16, usize>>,
    count: AtomicUsize,
}

impl Samples {
    fn new() -> Arc<Self> {
        Arc::new(Samples::default())
    }

    async fn record(&self, micros: u128, status: u16) {
        self.micros.lock().await.push(micros);
        *self.status.lock().await.entry(status).or_default() += 1;
        self.count.fetch_add(1, Ordering::Relaxed);
    }

    async fn report(&self, label: &str, stage: usize, wall: Duration) -> String {
        let mut m = self.micros.lock().await;
        m.sort_unstable();
        let n = m.len();
        if n == 0 {
            return format!("{label} stage={stage}: 无样本");
        }
        let pct = |p: f64| m[((n as f64) * p).min((n - 1) as f64) as usize];
        let st = self.status.lock().await;
        let mut codes: Vec<_> = st.iter().map(|(k, v)| format!("{k}×{v}")).collect();
        codes.sort();
        let qps = n as f64 / wall.as_secs_f64();
        format!(
            "{label:<14} 并发{stage:>4} | n={n:<7} qps={qps:>8.1} | p50={:>7.1}ms p95={:>7.1}ms p99={:>7.1}ms max={:>7.1}ms | {}",
            pct(0.50) as f64 / 1000.0,
            pct(0.95) as f64 / 1000.0,
            pct(0.99) as f64 / 1000.0,
            m[n - 1] as f64 / 1000.0,
            codes.join(" "),
        )
    }
}

async fn shared_state() -> Option<(common::state::SharedState, sea_orm::DatabaseConnection)> {
    if std::env::var("IDENTITY_LOAD").is_err() {
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
        store_jwt_secret: Some("load-test-store-secret-0123456789abcdef".into()),
        admin_jwt_secret: Some("load-test-admin-secret-0123456789abcdef".into()),
        store_cors_origin: String::new(),
        admin_cors_origin: String::new(),
        schema_path: String::new(),
    };
    Some((
        Arc::new(common::state::AppState {
            db: db.clone(),
            redis: None,
            cfg,
        }),
        db,
    ))
}

fn now() -> chrono::NaiveDateTime {
    chrono::Local::now().naive_local()
}

#[derive(Clone)]
struct VUser {
    email: String,
    refresh_token: String,
    access_token: String,
    user_id: u64,
}

fn http_client() -> reqwest::Client {
    reqwest::Client::builder()
        .timeout(Duration::from_secs(10))
        .pool_max_idle_per_host(64)
        .tcp_nodelay(true)
        .build()
        .expect("client")
}

/// setup:放宽 verify 频控 + 500 压测用户经真实 HTTP verify 登录(server 签发令牌);
/// 42904 IP 频控 300/min → 每 250 次休眠一个窗口
async fn setup(base: &str, state: &common::state::SharedState) -> (Vec<VUser>, Option<String>) {
    let db = &state.db;
    sea_orm::ConnectionTrait::execute_unprepared(
        db,
        "UPDATE auth_config SET verify_ip_rate_per_minute = 300 WHERE id = 1;",
    )
    .await
    .expect("放宽 verify 频控");
    sea_orm::ConnectionTrait::execute_unprepared(
        db,
        "DELETE FROM user_session WHERE token_id LIKE 'bench-a%';",
    )
    .await
    .ok();
    sea_orm::ConnectionTrait::execute_unprepared(
        db,
        "DELETE FROM otp_code WHERE email LIKE 'bench-v%@load.test';",
    )
    .await
    .ok();

    let client = http_client();
    let code_hash = bcrypt::hash("424242", 4).expect("hash");
    let mut vusers = Vec::with_capacity(500);
    for i in 0..500usize {
        let email = format!("bench-v{i}@load.test");
        let uid = match user::Entity::find()
            .filter(user::Column::Email.eq(email.clone()))
            .one(db)
            .await
            .expect("q user")
        {
            Some(u) => u.id,
            None => {
                let u = user::ActiveModel {
                    email: sea_orm::Set(email.clone()),
                    email_verified: sea_orm::Set(1),
                    tier: sea_orm::Set(UserTier::Regular.code() as i8),
                    status: sea_orm::Set(UserStatus::Active.code() as i8),
                    anonymized: sea_orm::Set(0),
                    version: sea_orm::Set(0),
                    joined_at: sea_orm::Set(Some(now())),
                    ..Default::default()
                }
                .insert(db)
                .await
                .expect("seed user");
                u.id
            }
        };
        // 直插已知码 + 真实 HTTP verify(全链:频控→归并→建号/幂等→会话→历史)
        // 时间一律 MySQL NOW():宿主 chrono 与 mysqld(UTC) 差 8h,混用会让
        // server 侧过期判定错位(expires/created 双源时间戳 = 压测 41001 根因)
        sea_orm::ConnectionTrait::execute_unprepared(
            db,
            &format!(
                "INSERT INTO otp_code (email,code_hash,length,expires_at,attempts,max_attempts,status,last_sent_at,version,created_at,updated_at) VALUES ('{email}', '{code_hash}', 6, DATE_ADD(DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR), INTERVAL 10 MINUTE), 0, 5, 1, DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR), 0, DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR));"
            ),
        )
        .await
        .expect("插码");
        let resp = client
            .post(format!("{base}/api/store/auth/otp/verify"))
            .json(&serde_json::json!({"email": email, "code": "424242"}))
            .send()
            .await
            .expect("verify 请求失败");
        let body: serde_json::Value = resp.json().await.expect("verify 响应解析");
        if body["code"] != 0 {
            panic!("setup verify 失败 i={i} email={email}: {body}");
        }
        let refresh_token = body["data"]["tokens"]["refresh_token"]
            .as_str()
            .expect("缺 refresh_token")
            .to_string();
        let access_token = body["data"]["tokens"]["access_token"]
            .as_str()
            .expect("缺 access_token")
            .to_string();
        vusers.push(VUser {
            email,
            refresh_token,
            access_token,
            user_id: uid,
        });
        if (i + 1) % 250 == 0 {
            println!("[load] setup 窗口休眠 61s(42904 频控 {}/250)…", i + 1);
            tokio::time::sleep(Duration::from_secs(61)).await;
        }
    }

    // S3 预置 send 冷却键(频控拒绝路径,零邮件副作用)
    if let Ok(redis_client) = redis::Client::open(format!(
        "redis://{}:{}/",
        env("REDIS_HOST", "127.0.0.1"),
        env("REDIS_PORT", "6379")
    )) {
        if let Ok(mut conn) = redis::aio::ConnectionManager::new(redis_client).await {
            use redis::AsyncCommands as _;
            for i in 0..500usize {
                let _: Result<(), _> = conn
                    .set_ex::<_, _, ()>(format!("otp:resend:bench-s{i}@load.test"), "1", 86400)
                    .await;
            }
        }
    }

    // admin token(HTTP 登录一次)
    let admin_token = match client
        .post(format!("{base}/api/admin/auth/login"))
        .json(&serde_json::json!({
            "email": env("BENCH_ADMIN_EMAIL", "admin@dreamy.com"),
            "password": env("BENCH_ADMIN_PASSWORD", "Admin@123456"),
        }))
        .send()
        .await
    {
        Ok(r) => match r.json::<serde_json::Value>().await {
            Ok(v) => v["data"]["token"].as_str().map(String::from),
            Err(_) => None,
        },
        Err(_) => None,
    };
    (vusers, admin_token)
}

/// 阶梯驱动:并发 N worker 跑 STAGE_SECS 秒;首 3 请求连接失败(0)自动重试一次
/// (Docker Desktop port-forward 首波建连丢失,非 server 行为,不计样本)
async fn run_stage<F, Fut>(stage: usize, f: F) -> Arc<Samples>
where
    F: Fn(usize) -> Fut + Clone + Send + 'static,
    Fut: std::future::Future<Output = (u128, u16)> + Send,
{
    let samples = Samples::new();
    let deadline = Instant::now() + Duration::from_secs(STAGE_SECS + WARMUP_SECS);
    let mut handles = Vec::with_capacity(stage);
    for i in 0..stage {
        let f = f.clone();
        let s = samples.clone();
        handles.push(tokio::spawn(async move {
            tokio::time::sleep(Duration::from_millis((i as u64 * 7) % 500)).await;
            let mut attempts = 0usize;
            while Instant::now() < deadline {
                let (micros, status) = f(i).await;
                if status == 0 && attempts < 3 {
                    attempts += 1;
                    tokio::time::sleep(Duration::from_millis(50)).await;
                    continue; // 建连失败不计样本,重试
                }
                attempts = 0;
                if deadline - Instant::now() <= Duration::from_secs(STAGE_SECS) {
                    s.record(micros, status).await;
                }
            }
        }));
    }
    for h in handles {
        h.await.ok();
    }
    samples
}

async fn load_test() {
    let base = env("SERVER_LOAD_ADDR", "http://127.0.0.1:18082");
    let Some((state, _db)) = shared_state().await else {
        return;
    };
    println!("[load] 目标 {base};阶梯 {STAGES:?} × {STAGE_SECS}s");
    let (vusers, admin_token) = setup(&base, &state).await;
    println!("[load] setup 完成:500 vusers(server 签发令牌)+ send 冷却键 + verify 频控 300/min");
    let client = Arc::new(http_client());
    // 旋转链表(refresh, access):S2 旋转推进后 access 同步更新,S8/S9 取最新——
    // 旧 access 在旋转后立即失效是服务端安全设计,压测必须跟随最新凭证
    let chains: Arc<Vec<Mutex<(String, String)>>> = Arc::new(
        vusers
            .iter()
            .map(|v| Mutex::new((v.refresh_token.clone(), v.access_token.clone())))
            .collect(),
    );
    let mut report = Vec::new();

    for &stage in STAGES {
        {
            let c = client.clone();
            let url = format!("{base}/api/store/auth/config");
            let s = run_stage(stage, move |_| {
                let c = c.clone();
                let url = url.clone();
                async move {
                    let t = Instant::now();
                    let r = c.get(&url).send().await;
                    let st = r.as_ref().map(|x| x.status().as_u16()).unwrap_or(0);
                    (t.elapsed().as_micros(), st)
                }
            })
            .await;
            report.push(s.report("S1 config", stage, Duration::from_secs(STAGE_SECS)).await);
        }
        {
            // S2:worker 独占一条旋转链,每次刷新推进到新 token(旋转后旧 token 必 40102)
            let c = client.clone();
            let url = format!("{base}/api/store/auth/refresh");
            let vu = vusers.clone();
            let chains = chains.clone();
            let s = Samples::new();
            let deadline = Instant::now() + Duration::from_secs(STAGE_SECS + WARMUP_SECS);
            let mut handles = Vec::with_capacity(stage);
            for i in 0..stage {
                let c = c.clone();
                let url = url.clone();
                let chains = chains.clone();
                let s = s.clone();
                handles.push(tokio::spawn(async move {
                    tokio::time::sleep(Duration::from_millis((i as u64 * 7) % 500)).await;
                    let mut first = true;
                    while Instant::now() < deadline {
                        let (token, _) = chains[i % chains.len()].lock().await.clone();
                        let t = Instant::now();
                        let r = c
                            .post(&url)
                            .json(&serde_json::json!({"refresh_token": token}))
                            .send()
                            .await;
                        let st = r.as_ref().map(|x| x.status().as_u16()).unwrap_or(0);
                        // 旋转推进:成功则更新链上 token(并发同链 worker 间由 40102 自然收敛)
                        if st == 200 {
                            if let Ok(v) = r.unwrap().json::<serde_json::Value>().await {
                                let nu = (
                                    v["data"]["tokens"]["refresh_token"]
                                        .as_str()
                                        .unwrap_or_default()
                                        .to_string(),
                                    v["data"]["tokens"]["access_token"]
                                        .as_str()
                                        .unwrap_or_default()
                                        .to_string(),
                                );
                                if !nu.0.is_empty() {
                                    *chains[i % chains.len()].lock().await = nu;
                                }
                            }
                        }
                        if first {
                            first = false;
                            continue; // 首请求(建连)不采样
                        }
                        if deadline - Instant::now() <= Duration::from_secs(STAGE_SECS) {
                            s.record(t.elapsed().as_micros(), st).await;
                        }
                    }
                }));
            }
            for h in handles {
                h.await.ok();
            }
            report.push(s.report("S2 refresh", stage, Duration::from_secs(STAGE_SECS)).await);
        }
        {
            let c = client.clone();
            let url = format!("{base}/api/store/auth/otp/send");
            let vu = vusers.clone();
            let s = run_stage(stage, move |i| {
                let c = c.clone();
                let url = url.clone();
                let email = format!("bench-s{}@load.test", i % vu.len());
                async move {
                    let t = Instant::now();
                    let r = c
                        .post(&url)
                        .json(&serde_json::json!({"email": email}))
                        .send()
                        .await;
                    let st = r.as_ref().map(|x| x.status().as_u16()).unwrap_or(0);
                    (t.elapsed().as_micros(), st)
                }
            })
            .await;
            report.push(s.report("S3 send(429)", stage, Duration::from_secs(STAGE_SECS)).await);
        }
        {
            let c = client.clone();
            let url = format!("{base}/api/admin/auth/login");
            let s = run_stage(stage, move |i| {
                let c = c.clone();
                let url = url.clone();
                let email = format!("bench-admin-{i}@load.test");
                async move {
                    let t = Instant::now();
                    let r = c
                        .post(&url)
                        .json(&serde_json::json!({"email": email, "password": "WrongPass!x9"}))
                        .send()
                        .await;
                    let st = r.as_ref().map(|x| x.status().as_u16()).unwrap_or(0);
                    (t.elapsed().as_micros(), st)
                }
            })
            .await;
            report.push(s.report("S5 admin-login", stage, Duration::from_secs(STAGE_SECS)).await);
        }
        if let Some(tk) = admin_token.clone() {
            let c = client.clone();
            let url = format!("{base}/api/admin/users");
            let vu = vusers.clone();
            let tk = tk.clone();
            let s = run_stage(stage, move |i| {
                let c = c.clone();
                let url = url.clone();
                let tk = tk.clone();
                let uid = vu[i % vu.len()].user_id;
                async move {
                    let t = Instant::now();
                    let r = c
                        .get(format!("{url}/{uid}"))
                        .header("Authorization", format!("Bearer {tk}"))
                        .send()
                        .await;
                    let st = r.as_ref().map(|x| x.status().as_u16()).unwrap_or(0);
                    (t.elapsed().as_micros(), st)
                }
            })
            .await;
            report.push(s.report("S6 sessions", stage, Duration::from_secs(STAGE_SECS)).await);
        }
        if let Some(tk) = admin_token.clone() {
            let c = client.clone();
            let url = format!("{base}/api/admin/users");
            let tk = tk.clone();
            let s = run_stage(stage, move |i| {
                let c = c.clone();
                let url = url.clone();
                let tk = tk.clone();
                let page = if i % 4 == 0 { 1 } else { 10 + (i % 20) as i64 };
                async move {
                    let t = Instant::now();
                    let r = c
                        .get(format!("{url}?page={page}&pageSize=20"))
                        .header("Authorization", format!("Bearer {tk}"))
                        .send()
                        .await;
                    let st = r.as_ref().map(|x| x.status().as_u16()).unwrap_or(0);
                    (t.elapsed().as_micros(), st)
                }
            })
            .await;
            report.push(s.report("S7 users分页", stage, Duration::from_secs(STAGE_SECS)).await);
        }
        // S8 profile:消费端认证读热路径(JWT extractor → validate_store Redis 主存 + 用户缓存)
        if let Some(_tk) = vusers.first().map(|v| v.access_token.clone()) {
            let c = client.clone();
            let url = format!("{base}/api/store/account/profile");
            let chains_ref = chains.clone();
            let s = run_stage(stage, move |i| {
                let c = c.clone();
                let url = url.clone();
                let cr = chains_ref.clone();
                async move {
                    let tk = cr[i % cr.len()].lock().await.1.clone();
                    let t = Instant::now();
                    let r = c
                        .get(&url)
                        .header("Authorization", format!("Bearer {tk}"))
                        .send()
                        .await;
                    let st = r.as_ref().map(|x| x.status().as_u16()).unwrap_or(0);
                    (t.elapsed().as_micros(), st)
                }
            })
            .await;
            report.push(s.report("S8 profile", stage, Duration::from_secs(STAGE_SECS)).await);
        }
        // S9 identities:认证读 + 路由表(千万行 KEY 分区)反查
        {
            let c = client.clone();
            let url = format!("{base}/api/store/account/identities");
            let chains_ref = chains.clone();
            let s = run_stage(stage, move |i| {
                let c = c.clone();
                let url = url.clone();
                let cr = chains_ref.clone();
                async move {
                    let tk = cr[i % cr.len()].lock().await.1.clone();
                    let t = Instant::now();
                    let r = c
                        .get(&url)
                        .header("Authorization", format!("Bearer {tk}"))
                        .send()
                        .await;
                    let st = r.as_ref().map(|x| x.status().as_u16()).unwrap_or(0);
                    (t.elapsed().as_micros(), st)
                }
            })
            .await;
            report.push(s.report("S9 identities", stage, Duration::from_secs(STAGE_SECS)).await);
        }
        println!("[load] 阶梯 {stage} 完成");
    }

    // S4 verify 全链小样本(300/min 频控内)
    {
        println!("[load] S4 verify 全链采样(约 40 样本)…");
        let s = Samples::new();
        let c = client.clone();
        for i in 0..40usize {
            let email = format!("bench-v{i}@load.test");
            let hash = bcrypt::hash("424242", 4).expect("hash");
            sea_orm::ConnectionTrait::execute_unprepared(
                &state.db,
                &format!(
                    "INSERT INTO otp_code (email,code_hash,length,expires_at,attempts,max_attempts,status,last_sent_at,version,created_at,updated_at) VALUES ('{email}', '{hash}', 6, DATE_ADD(DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR), INTERVAL 10 MINUTE), 0, 5, 1, DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR), 0, DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR));"
                ),
            )
            .await
            .expect("插码");
            let t = Instant::now();
            let r = c
                .post(format!("{base}/api/store/auth/otp/verify"))
                .json(&serde_json::json!({"email": email, "code": "424242"}))
                .send()
                .await;
            let st = r.as_ref().map(|x| x.status().as_u16()).unwrap_or(0);
            s.record(t.elapsed().as_micros(), st).await;
            tokio::time::sleep(Duration::from_millis(2100)).await;
        }
        report.push(s.report("S4 verify 全链", 1, Duration::from_secs(100)).await);
    }

    println!("\n===== login 专项压测报告(2026-09-19,千万级数据) =====");
    for line in report {
        println!("{line}");
    }
    println!("\n说明:S2 为登录态最重写链(容量主力);S3/S5 稳态即频控拒绝路径(安全设计);");
    println!("S4 受 42904 IP 频控约束;S7 深分页 total 计数已走 30s 缓存(本变更性能优化)。");
}

#[tokio::test]
async fn login_benchmark() {
    if std::env::var("IDENTITY_LOAD").is_err() {
        return;
    }
    load_test().await;
}
