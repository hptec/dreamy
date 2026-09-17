//! 大数据量证据测试(用户指令:大数量设计与实现必须测试并留证据)。
//!
//! ```bash
//! # 需本地 MySQL 可达;灌库+测量约 1 分钟
//! DB_PASSWORD=<pwd> SERVER_DB_HOST=127.0.0.1 SERVER_DB_PORT=3307 \
//!   IDENTITY_LARGE_DATA=1 cargo test -p identity --test large_data -- --nocapture
//! ```
//!
//! 设计:
//! - 独立临时库 dreamy_bench(结构 LIKE identity.user),不污染 dreamy_server
//! - 灌数用翻倍法(INSERT...SELECT 显式 id 平移),17 轮 ≈ 140 万行
//! - 直接驱动 service::user_query(构造指向 bench 库的 SharedState),
//!   Redis 置 None → 测的是纯 DB 路径(缓存路径热延迟由冒烟覆盖)
//! - 证据:各场景 p50/p95 延迟表;断言阈值见各测量点
//! - 深分页(created_at 无索引时的默认排序)为已知风险点——证据说话,
//!   若超标则结论:需补 idx_user_created_at(增量 DDL,随迁移脚本走)

use std::time::Instant;

use common::state::{AppState, SharedState};
use identity::service::user_query::{self, Col, Cond, CondVal, ListQuery};
use sea_orm::{ConnectionTrait, Database, Statement};

fn env(key: &str, default: &str) -> String {
    std::env::var(key).unwrap_or_else(|_| default.to_string())
}

/// 场景测量:N 次取 p50/p95(毫秒)。
/// 注意:async 测量必须原生 await——tokio current_thread 测试运行时内
/// 嵌套 block_on 会在第一条 IO 查询上死锁(实测踩坑)。
async fn measure_p<F, Fut>(runs: usize, mut f: F) -> (f64, f64)
where
    F: FnMut() -> Fut,
    Fut: std::future::Future<Output = ()>,
{
    // 预热 2 轮不计样(排除冷缓冲/首查编译的离群值——测量方法学,非预算放水)
    for _ in 0..2 {
        f().await;
    }
    let mut samples: Vec<f64> = Vec::with_capacity(runs);
    for _ in 0..runs {
        let t = Instant::now();
        f().await;
        samples.push(t.elapsed().as_secs_f64() * 1000.0);
    }
    samples.sort_by(|a, b| a.partial_cmp(b).unwrap());
    let p50 = samples[samples.len() / 2];
    let p95 = samples[(samples.len() * 95) / 100];
    (p50, p95)
}

/// 证据收集:全部场景测完统一断言(单场景超标不中断后续测量,一次跑完拿全证据)
struct Evidence {
    label: &'static str,
    p50: f64,
    p95: f64,
    budget: f64,
}

fn record(out: &mut Vec<Evidence>, label: &'static str, p50: f64, p95: f64, budget: f64) {
    let verdict = if p95 < budget { "OK " } else { "超标" };
    println!("  [{verdict}] {label:<36} p50={p50:8.2}ms  p95={p95:8.2}ms  (预算 {budget}ms)");
    out.push(Evidence {
        label,
        p50,
        p95,
        budget,
    });
}

#[tokio::test]
async fn large_data_evidence() {
    if std::env::var("IDENTITY_LARGE_DATA").is_err() {
        eprintln!("[large-data] 跳过:未设置 IDENTITY_LARGE_DATA=1");
        return;
    }
    let base_url = format!(
        "mysql://root:{}@{}:{}",
        urlencoding::encode(&env("DB_PASSWORD", "")),
        env("SERVER_DB_HOST", "127.0.0.1"),
        env("SERVER_DB_PORT", "3307")
    );
    let admin = Database::connect(&base_url).await.expect("连接 MySQL 失败");

    // ── 独立 bench 库(前后皆清)──
    admin
        .execute(Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            "DROP DATABASE IF EXISTS dreamy_bench",
        ))
        .await
        .unwrap();
    admin
        .execute(Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            "CREATE DATABASE dreamy_bench CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",
        ))
        .await
        .unwrap();
    admin
        .execute(Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            "CREATE TABLE dreamy_bench.user LIKE dreamy_server.user",
        ))
        .await
        .unwrap();
    admin
        .execute(Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            "INSERT INTO dreamy_bench.user (id, email, email_verified, tier, status, anonymized, version, joined_at, created_at, updated_at) VALUES (1, 'seed@bench.test', 1, 1, 1, 0, 0, NOW(), NOW(), NOW())",
        ))
        .await
        .unwrap();

    // v2.2:email 查找走 identity_email 路由表(bench 同构复制 + 灌数)
    admin
        .execute(Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            "CREATE TABLE dreamy_bench.identity_email LIKE dreamy_server.identity_email",
        ))
        .await
        .unwrap();

    // ── 翻倍灌数:20 轮 1 → 2^20(104 万)行 ──
    println!("[large-data] 灌数中(翻倍法,目标 >100 万行)...");
    let t0 = Instant::now();
    for _ in 0..20 {
        admin
            .execute(Statement::from_string(
                sea_orm::DatabaseBackend::MySql,
                "INSERT INTO dreamy_bench.user (id, email, email_verified, tier, status, anonymized, version, joined_at, created_at, updated_at) \
                 SELECT id + (SELECT MAX(id) FROM dreamy_bench.user), CONCAT('bulk-', id + (SELECT MAX(id) FROM dreamy_bench.user), '@bench.test'), 1, 1, 1, 0, 0, NOW(), NOW(), NOW() \
                 FROM dreamy_bench.user",
            ))
            .await
            .expect("灌数失败");
    }
    let db = Database::connect(format!("{base_url}/dreamy_bench"))
        .await
        .expect("连接 bench 库失败");
    let total: i64 = db
        .query_one(Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            "SELECT COUNT(*) AS c FROM user",
        ))
        .await
        .unwrap()
        .unwrap()
        .try_get_by_index::<i64>(0)
        .unwrap_or(0);
    println!(
        "[large-data] 灌数完成:{total} 行,耗时 {:.1}s",
        t0.elapsed().as_secs_f64()
    );
    assert!(total >= 1_000_000, "行数不足百万:{total}");
    // 路由表灌数(email → id,百万行)
    admin
        .execute(Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            "INSERT INTO dreamy_bench.identity_email (email, user_id, created_at) SELECT email, id, NOW() FROM dreamy_bench.user",
        ))
        .await
        .expect("路由表灌数失败");

    let state: SharedState = std::sync::Arc::new(AppState {
        db,
        redis: None, // 纯 DB 路径;缓存热路径另证
        cfg: common::config::Config {
            http_port: 0,
            grpc_port: 0,
            db_host: String::new(),
            db_port: 0,
            db_name: "dreamy_bench".into(),
            db_user: String::new(),
            db_password: String::new(),
            redis_host: String::new(),
            redis_port: 0,
            store_jwt_secret: None,
            admin_jwt_secret: None,
            store_cors_origin: String::new(),
            admin_cors_origin: String::new(),
            schema_path: String::new(),
        },
    });

    println!("[large-data] 延迟测量(p50/p95;慢场景降采样,全部测完统一断言):");
    let mut evidence: Vec<Evidence> = Vec::new();

    // ① 首页:默认排序(created_at desc)+ id 兜底,page 1
    {
        let q = ListQuery {
            conds: vec![],
            order: (Col::CreatedAt, true),
            page: 1,
            page_size: 20,
        };
        let (p50, p95) = measure_p(12, || async {
            user_query::list_users(&state, &q).await.unwrap();
        })
        .await;
        record(
            &mut evidence,
            "① 首页(created_at desc, 20 行)",
            p50,
            p95,
            300.0,
        );
    }

    // ② id 排序首页(主键有序,最优情形对照)
    {
        let q = ListQuery {
            conds: vec![],
            order: (Col::Id, true),
            page: 1,
            page_size: 20,
        };
        let (p50, p95) = measure_p(50, || async {
            user_query::list_users(&state, &q).await.unwrap();
        })
        .await;
        // 预算构成:百万行 COUNT(*) ~50ms 为物理下限(Paginated 契约要求 total;
        // InnoDB 无行数元数据,走最窄索引全扫)+ 主键取页 <2ms(⑥ 佐证主键路径)
        record(&mut evidence, "② 首页(id desc, 20 行)", p50, p95, 100.0);
    }

    // ③ 深分页(中段页码)——已知风险点:created_at 无索引
    {
        let mid_page = (total as u64 / 20 / 2).max(1);
        let q = ListQuery {
            conds: vec![],
            order: (Col::CreatedAt, true),
            page: mid_page,
            page_size: 20,
        };
        let (p50, p95) = measure_p(10, || async {
            user_query::list_users(&state, &q).await.unwrap();
        })
        .await;
        record(
            &mut evidence,
            "③ 深分页(created_at desc 中段)",
            p50,
            p95,
            1500.0,
        );
    }

    // ④ 前缀过滤(email 唯一索引可走)
    {
        let q = ListQuery {
            conds: vec![Cond::LikePrefix(Col::Email, "bulk-1234".into())],
            order: (Col::Id, false),
            page: 1,
            page_size: 20,
        };
        let (p50, p95) = measure_p(50, || async {
            user_query::list_users(&state, &q).await.unwrap();
        })
        .await;
        record(&mut evidence, "④ Email 前缀过滤(uk 索引)", p50, p95, 100.0);
    }

    // ⑤ 状态过滤 + 计数(全表命中)
    {
        let q = ListQuery {
            conds: vec![Cond::Eq(Col::Status, CondVal::Num(1))],
            order: (Col::Id, true),
            page: 1,
            page_size: 20,
        };
        let (p50, p95) = measure_p(10, || async {
            user_query::list_users(&state, &q).await.unwrap();
        })
        .await;
        record(
            &mut evidence,
            "⑤ 状态过滤 + 全量计数(百万行)",
            p50,
            p95,
            800.0,
        );
    }

    // ⑥ GetUser 主键直查(无缓存,纯 DB)
    {
        let lookup = user_query::Lookup::Id(700_000);
        let (p50, p95) = measure_p(50, || async {
            user_query::get_user(&state, &lookup).await.unwrap();
        })
        .await;
        record(&mut evidence, "⑥ GetUser by id(主键)", p50, p95, 20.0);
    }

    // ⑦ GetUser by email(唯一索引)
    {
        let lookup = user_query::Lookup::Email("bulk-700000@bench.test".into());
        let (p50, p95) = measure_p(50, || async {
            user_query::get_user(&state, &lookup).await.unwrap();
        })
        .await;
        record(
            &mut evidence,
            "⑦ GetUser by email(路由两跳)",
            p50,
            p95,
            20.0,
        );
    }

    // 统一断言:任一超标即失败(证据触发优化:补索引/改查询),完整证据表已先行输出
    let over: Vec<&Evidence> = evidence.iter().filter(|e| e.p95 >= e.budget).collect();
    assert!(
        over.is_empty(),
        "大数据量预算超标(需优化后复测):{}",
        over.iter()
            .map(|e| {
                format!(
                    "{} p50={:.0}ms p95={:.0}ms>={:.0}ms",
                    e.label, e.p50, e.p95, e.budget
                )
            })
            .collect::<Vec<_>>()
            .join("; ")
    );

    // ── 清理 ──
    admin
        .execute(Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            "DROP DATABASE IF EXISTS dreamy_bench",
        ))
        .await
        .unwrap();
    println!("[large-data] 证据完成,bench 库已清理");
}
