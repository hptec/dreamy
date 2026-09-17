//! 分区动态保障(v3:写入路径拦截;无计划任务、无 DROP、无 pmax 哨兵)。
//!
//! 通用机制,与域无关(common 所有域 crate 的分区表写入路径共用):
//! - Redis 缓存分区名集合(TTL 90 天):命中=分区必在,直接写,零 DB 元数据查询
//! - miss → Redis 分布式锁(SET NX EX 30s)+ DB 双检查(information_schema 为准)
//!   → 尾部 ADD PARTITION(空区纯元数据,毫秒级)/ 历史或空洞 REORGANIZE 邻接分区
//!   → 缓存回填
//! - INSERT 报 1526(Table has no partition for value)→ 自愈 ensure → 重试一次
//! - Redis 故障:缓存按 miss 处理、锁按未持有处理——DB 双检查 + DDL 报错幂等
//!   (Duplicate partition 捕获当成功)兜底,只降速不降正确性
//!
//! 调用约定(域 crate 按自己的分区表选用):
//! - 时间分区表:INSERT 前 ensure_months([now])(容忍失败,1526 自愈兜底);
//!   非事务路径可改用 insert_self_heal 一步到位(DDL 隐式提交,事务内勿用)
//! - 段分区表(分区键 id/user_id 已知):事务前 ensure_id_segments([id]),id 已知精确
//! - 水位预扩等策略(哪些表、何时探测)由域 crate 自行定义
//!   (identity 域见 service::ensure_user_segments)
//!
//! ⚠ 本模块是全仓唯一保留原生 SQL 的位置:分区 DDL(ALTER TABLE … ADD/REORGANIZE
//! PARTITION)与 information_schema 元数据查询,SeaORM 查询 DSL 无法表达。
//! 业务表查询/写入一律走实体 DSL(禁止 Statement::from_sql_and_values)。

use chrono::{Datelike, NaiveDateTime};
use crate::state::SharedState;
use redis::AsyncCommands;
use sea_orm::{ConnectionTrait, DbErr, ExecResult, Statement};

const SEGMENT_SIZE: u64 = 4_000_000;
const CACHE_TTL_SECS: i64 = 90 * 24 * 3600;
const LOCK_TTL_SECS: i64 = 30;

fn cache_key(table: &str) -> String {
    format!("partition:{table}")
}

fn lock_key(table: &str) -> String {
    format!("partition_lock:{table}")
}

fn month_name(y: i32, m: u32) -> String {
    format!("p{y:04}{m:02}")
}

/// 月分区边界 = 下月 1 号(SQL 字面量,含引号)
fn month_bound_sql(y: i32, m: u32) -> String {
    let (ny, nm) = if m == 12 { (y + 1, 1) } else { (y, m + 1) };
    format!("'{ny:04}-{nm:02}-01'")
}

/// MySQL 1526(Table has no partition for value)
pub fn is_no_partition(err: &DbErr) -> bool {
    let s = err.to_string().to_ascii_lowercase();
    s.contains("no partition") || s.contains("1526")
}

/// MySQL 1517(Duplicate partition name):并发建同分区的幂等信号
fn is_duplicate_partition(err: &DbErr) -> bool {
    err.to_string().to_ascii_lowercase().contains("duplicate partition")
}

// ══════════════════ 公共入口 ══════════════════

/// 月分区保障(时间表)。单条插入即批量为 1 的特例。
/// 返回 Err 仅代表「尽力保障失败」,调用方可容忍(1526 自愈兜底)或传播。
pub async fn ensure_months(
    state: &SharedState,
    table: &str,
    times: &[NaiveDateTime],
) -> Result<(), DbErr> {
    ensure_months_impl(state, table, times, false).await
}

/// 自愈专用:跳过缓存快路径强制 DB 双检查。
/// 缓存假命中(Redis 说有而 DB 实无,如分区被人工 DROP)时,普通 ensure 会再次
/// 假命中导致重试依然 1526——只有绕过缓存才能发现真相并修正(cache_fill 全量重建)。
async fn ensure_months_force(
    state: &SharedState,
    table: &str,
    times: &[NaiveDateTime],
) -> Result<(), DbErr> {
    ensure_months_impl(state, table, times, true).await
}

async fn ensure_months_impl(
    state: &SharedState,
    table: &str,
    times: &[NaiveDateTime],
    force: bool,
) -> Result<(), DbErr> {
    let mut months: Vec<(i32, u32)> = times.iter().map(|t| (t.year(), t.month())).collect();
    months.sort_unstable();
    months.dedup();
    if months.is_empty() {
        return Ok(());
    }
    let all: Vec<(String, String)> = months
        .iter()
        .map(|(y, m)| (month_name(*y, *m), month_bound_sql(*y, *m)))
        .collect();
    let names: Vec<String> = all.iter().map(|(n, _)| n.clone()).collect();
    let missing = if force {
        names // 强制全 miss:直接锁 + DB 双检查
    } else {
        match cache_missing(state, table, &names).await {
            Some(m) if m.is_empty() => return Ok(()),
            Some(m) => m,
            None => names, // Redis 缺席 → 全部走 DB 双检查
        }
    };
    let todo: Vec<(String, String)> = all
        .into_iter()
        .filter(|(n, _)| missing.contains(n))
        .collect();
    if todo.is_empty() {
        return Ok(());
    }
    ensure_with_lock(state, table, |existing| {
        let mut todo = todo.clone();
        todo.retain(|(n, _)| !existing.iter().any(|(en, _)| en == n));
        if todo.is_empty() {
            return Ok(vec![]);
        }
        Ok(plan_month_ddls(table, existing, &todo))
    })
    .await
}

/// 段保障(分区键值已知,精确校验)
pub async fn ensure_id_segments(
    state: &SharedState,
    table: &str,
    ids: &[u64],
) -> Result<(), DbErr> {
    let mut segs: Vec<u64> = ids.iter().map(|i| i / SEGMENT_SIZE).collect();
    segs.sort_unstable();
    segs.dedup();
    if segs.is_empty() {
        return Ok(());
    }
    let names: Vec<String> = segs.iter().map(|s| format!("p{s}")).collect();
    let missing = match cache_missing(state, table, &names).await {
        Some(m) if m.is_empty() => return Ok(()),
        Some(m) => m,
        None => names,
    };
    if missing.is_empty() {
        return Ok(());
    }
    ensure_with_lock(state, table, |existing| {
        let existing_segs: Vec<u64> = parse_segments(existing);
        let needed: Vec<u64> = missing
            .iter()
            .filter_map(|n| n.strip_prefix('p').and_then(|x| x.parse::<u64>().ok()))
            .filter(|s| !existing_segs.contains(s))
            .collect();
        if needed.is_empty() {
            return Ok(vec![]);
        }
        Ok(plan_segment_ddls(table, existing, &needed))
    })
    .await
}

/// 月分区表 INSERT + 1526 自愈重试(仅限非事务路径;DDL 隐式提交,事务内勿用)
pub async fn insert_self_heal(
    state: &SharedState,
    table: &str,
    months: &[NaiveDateTime],
    stmt: Statement,
) -> Result<ExecResult, DbErr> {
    match state.db.execute(stmt.clone()).await {
        Ok(r) => Ok(r),
        Err(e) if is_no_partition(&e) => {
            tracing::warn!("[partition] {table} INSERT 无匹配分区,自愈后重试");
            ensure_months_force(state, table, months).await?;
            state.db.execute(stmt).await
        }
        Err(e) => Err(e),
    }
}

// ══════════════════ 锁 + 双检查 + 执行 + 回填 ══════════════════

/// 锁内流程:双检查(DB 现有分区)→ 规划 DDL → 执行(幂等)→ 缓存回填。
/// plan 闭包接收 DB 实况,返回待执行 DDL 列表(空=无需动作,仅回填)。
async fn ensure_with_lock<F>(state: &SharedState, table: &str, plan: F) -> Result<(), DbErr>
where
    F: FnOnce(&[(String, String)]) -> Result<Vec<String>, DbErr>,
{
    let guard = try_lock(state, table).await;
    let result = run_ensure(state, table, plan).await;
    if let Some(g) = guard {
        g.release().await;
    }
    result
}

async fn run_ensure<F>(state: &SharedState, table: &str, plan: F) -> Result<(), DbErr>
where
    F: FnOnce(&[(String, String)]) -> Result<Vec<String>, DbErr>,
{
    // 双检查:information_schema 为准(缓存假 miss / 假命中在此修正)
    let existing = query_partitions(state, table).await?;
    let ddls = plan(&existing)?;
    for ddl in &ddls {
        if let Err(e) = state
            .db
            .execute(Statement::from_string(
                sea_orm::DatabaseBackend::MySql,
                ddl.clone(),
            ))
            .await
        {
            // 并发建同分区:另一个执行者已建成,幂等成功
            if !is_duplicate_partition(&e) {
                return Err(e);
            }
            tracing::warn!("[partition] {table} 并发建分区撞车(幂等忽略):{ddl}");
        }
    }
    if !ddls.is_empty() {
        tracing::info!("[partition] {table} 动态建分区:{} 条 DDL", ddls.len());
    }
    cache_fill(state, table).await;
    Ok(())
}

/// 现有分区实况(名称 + 边界描述):日期形如 '2026-10-01',段为数字串,pmax 为 MAXVALUE
async fn query_partitions(state: &SharedState, table: &str) -> Result<Vec<(String, String)>, DbErr> {
    let rows = state
        .db
        .query_all(Statement::from_sql_and_values(
            sea_orm::DatabaseBackend::MySql,
            r#"SELECT PARTITION_NAME, PARTITION_DESCRIPTION FROM information_schema.PARTITIONS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND PARTITION_NAME IS NOT NULL
               ORDER BY PARTITION_ORDINAL_POSITION"#,
            [table.into()],
        ))
        .await?;
    Ok(rows
        .iter()
        .filter_map(|r| {
            Some((
                r.try_get_by_index::<String>(0).ok()?,
                r.try_get_by_index::<String>(1).ok()?,
            ))
        })
        .collect())
}

// ══════════════════ DDL 规划(纯函数,可单测) ══════════════════

/// 日期边界去引号后字典序即时间序(YYYY-MM-DD 固定宽度)
fn clean_bound(d: &str) -> &str {
    d.trim_matches('\'')
}

/// 月分区规划:尾部(边界 > 现最高)ADD 一次;历史/空洞按邻接分区分组 REORGANIZE
fn plan_month_ddls(
    table: &str,
    existing: &[(String, String)],
    todo: &[(String, String)],
) -> Vec<String> {
    let has_pmax = existing.iter().any(|(_, d)| d == "MAXVALUE");
    let max_bound: Option<&str> = existing
        .iter()
        .filter(|(_, d)| d != "MAXVALUE")
        .map(|(_, d)| clean_bound(d))
        .max();

    let mut tail: Vec<&(String, String)> = Vec::new();
    let mut gap: Vec<&(String, String)> = Vec::new();
    for m in todo {
        let b = clean_bound(&m.1);
        if max_bound.is_none_or(|mb| b > mb) {
            tail.push(m);
        } else {
            gap.push(m);
        }
    }

    let mut ddls = Vec::new();
    if !tail.is_empty() {
        tail.sort_by(|a, b| clean_bound(&a.1).cmp(clean_bound(&b.1)));
        let parts: Vec<String> = tail
            .iter()
            .map(|(n, b)| format!("PARTITION {n} VALUES LESS THAN ({b})"))
            .collect();
        // 存量表若仍带 pmax 哨兵,ADD 被 MySQL 拒绝(ERROR 1493),走 REORGANIZE 兼容
        if has_pmax {
            ddls.push(format!(
                "ALTER TABLE `{table}` REORGANIZE PARTITION pmax INTO ({}, \
                 PARTITION pmax VALUES LESS THAN (MAXVALUE))",
                parts.join(", ")
            ));
        } else {
            ddls.push(format!(
                "ALTER TABLE `{table}` ADD PARTITION ({})",
                parts.join(", ")
            ));
        }
    }

    // 历史/空洞:每个待建月找「首个边界 ≥ 它」的邻接分区 P,按 P 分组一次 REORGANIZE
    // (新集合 = 组内月分区… + P 原名原边界重建,覆盖 P 原范围;组间分区互斥互不影响)
    if !gap.is_empty() {
        gap.sort_by(|a, b| clean_bound(&a.1).cmp(clean_bound(&b.1)));
        let mut groups: Vec<(String, String, Vec<String>)> = Vec::new(); // (P名, P边界, 新分区列表)
        for (n, b) in &gap {
            let bnd = clean_bound(b);
            let target = existing.iter().filter(|(_, d)| d != "MAXVALUE").find(|(_, d)| clean_bound(d) >= bnd);
            let Some((pn, pd)) = target else {
                continue; // 无实邻接(理论上 gap 月必有,防御跳过)
            };
            let part = format!("PARTITION {n} VALUES LESS THAN ({b})");
            match groups.iter_mut().find(|(gn, _, _)| gn == pn) {
                Some((_, _, parts)) => parts.push(part),
                None => groups.push((pn.clone(), pd.clone(), vec![part])),
            }
        }
        for (pn, pd, parts) in groups {
            ddls.push(format!(
                "ALTER TABLE `{table}` REORGANIZE PARTITION `{pn}` INTO ({}, \
                 PARTITION `{pn}` VALUES LESS THAN ({pd}))",
                parts.join(", ")
            ));
        }
    }
    ddls
}

fn parse_segments(existing: &[(String, String)]) -> Vec<u64> {
    existing
        .iter()
        .filter_map(|(n, _)| n.strip_prefix('p').and_then(|x| x.parse::<u64>().ok()))
        .collect()
}

/// 段分区规划:尾部连续 ADD 到覆盖最大需求段(空段纯元数据,一次多段)
fn plan_segment_ddls(
    table: &str,
    existing: &[(String, String)],
    needed: &[u64],
) -> Vec<String> {
    let existing_segs = parse_segments(existing);
    let db_max = existing_segs.iter().max().copied().unwrap_or(0);
    let Some(&add_to) = needed.iter().max() else {
        return vec![];
    };
    if add_to <= db_max {
        // 段空洞(低于最高段的缺失段)无法尾部 ADD——按建段规则不会发生,交由 1526 兜底
        tracing::error!(
            "[partition] {table} 段空洞(needed={needed:?} ≤ 最高段 p{db_max}),尾部 ADD 不可达"
        );
        return vec![];
    }
    let parts: Vec<String> = (db_max + 1..=add_to)
        .map(|s| format!("PARTITION p{s} VALUES LESS THAN ({})", (s + 1) * SEGMENT_SIZE))
        .collect();
    let has_pmax = existing.iter().any(|(_, d)| d == "MAXVALUE");
    if has_pmax {
        vec![format!(
            "ALTER TABLE `{table}` REORGANIZE PARTITION pmax INTO ({}, \
             PARTITION pmax VALUES LESS THAN (MAXVALUE))",
            parts.join(", ")
        )]
    } else {
        vec![format!(
            "ALTER TABLE `{table}` ADD PARTITION ({})",
            parts.join(", ")
        )]
    }
}

// ══════════════════ Redis 缓存 / 锁 ══════════════════

/// 缓存过滤:Some(missing)=Redis 正常;None=Redis 缺席(调用方按全 miss 处理)
async fn cache_missing(
    state: &SharedState,
    table: &str,
    names: &[String],
) -> Option<Vec<String>> {
    let mut conn = state.redis.clone()?;
    let flags: Vec<bool> = conn.smismember(cache_key(table), names).await.ok()?;
    Some(
        names
            .iter()
            .zip(flags)
            .filter(|(_, hit)| !hit)
            .map(|(n, _)| n.clone())
            .collect(),
    )
}

/// 回填缓存:以 DB 实况为准全量重建(修正假 miss/假命中),失败忽略(下次 miss 再回源)
async fn cache_fill(state: &SharedState, table: &str) {
    let Some(mut conn) = state.redis.clone() else {
        return;
    };
    let Ok(existing) = query_partitions(state, table).await else {
        return;
    };
    let names: Vec<String> = existing.into_iter().map(|(n, _)| n).collect();
    if names.is_empty() {
        return;
    }
    let key = cache_key(table);
    let _: Result<(), _> = redis::pipe()
        .del(&key)
        .sadd(&key, names)
        .expire(&key, CACHE_TTL_SECS)
        .query_async(&mut conn)
        .await;
}

struct LockGuard {
    state: SharedState,
    table: String,
    token: String,
}

impl LockGuard {
    /// 比较 token 再 DEL(防误删他人锁);失败忽略(TTL 30s 兜底)
    async fn release(self) {
        let Some(mut conn) = self.state.redis.clone() else {
            return;
        };
        let script = redis::Script::new(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        );
        let _ = script
            .key(lock_key(&self.table))
            .arg(&self.token)
            .invoke_async::<()>(&mut conn)
            .await;
    }
}

/// 非阻塞抢锁:拿不到也放行(DB 双检查 + DDL 报错幂等才是正确性根基,锁仅为减噪声)
async fn try_lock(state: &SharedState, table: &str) -> Option<LockGuard> {
    let mut conn = state.redis.clone()?;
    let token = format!(
        "{:x}-{:x}",
        std::process::id(),
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .ok()?
            .as_nanos()
    );
    let ok: bool = redis::cmd("SET")
        .arg(lock_key(table))
        .arg(&token)
        .arg("NX")
        .arg("EX")
        .arg(LOCK_TTL_SECS)
        .query_async(&mut conn)
        .await
        .ok()?;
    ok.then(|| LockGuard {
        state: state.clone(),
        table: table.to_string(),
        token,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn part(name: &str, bound: &str) -> (String, String) {
        (name.to_string(), bound.to_string())
    }

    #[test]
    fn month_name_and_bound() {
        assert_eq!(month_name(2026, 9), "p202609");
        assert_eq!(month_bound_sql(2026, 9), "'2026-10-01'");
        assert_eq!(month_bound_sql(2026, 12), "'2027-01-01'");
    }

    #[test]
    fn plan_tail_adds_once_for_multiple_future_months() {
        let existing = vec![part("p202609", "'2026-10-01'"), part("p202610", "'2026-11-01'")];
        let todo = vec![part("p202611", "'2026-12-01'"), part("p202612", "'2027-01-01'")];
        let ddls = plan_month_ddls("otp_code", &existing, &todo);
        assert_eq!(ddls.len(), 1);
        assert!(ddls[0].contains("ADD PARTITION"));
        assert!(ddls[0].contains("p202611"));
        assert!(ddls[0].contains("p202612"));
    }

    #[test]
    fn plan_history_reorganizes_neighbor() {
        let existing = vec![part("p202608", "'2026-09-01'"), part("p202609", "'2026-10-01'")];
        let todo = vec![part("p202506", "'2025-07-01'")];
        let ddls = plan_month_ddls("login_history", &existing, &todo);
        assert_eq!(ddls.len(), 1);
        assert!(ddls[0].contains("REORGANIZE PARTITION `p202608`"));
        // 邻接分区原名同边界重建在尾部
        assert!(ddls[0].contains("PARTITION `p202608` VALUES LESS THAN ('2026-09-01')"));
        assert!(ddls[0].contains("PARTITION p202506 VALUES LESS THAN ('2025-07-01')"));
    }

    #[test]
    fn plan_gap_months_same_neighbor_merge_into_one_reorganize() {
        // 初始低界 + 当前月之间的大空洞,两个历史月邻接同一分区
        let existing = vec![part("p202501", "'2025-02-01'"), part("p202609", "'2026-10-01'")];
        let todo = vec![part("p202506", "'2025-07-01'"), part("p202508", "'2025-09-01'")];
        let ddls = plan_month_ddls("otp_code", &existing, &todo);
        assert_eq!(ddls.len(), 1);
        assert!(ddls[0].contains("REORGANIZE PARTITION `p202609`"));
        assert!(ddls[0].contains("p202506"));
        assert!(ddls[0].contains("p202508"));
    }

    #[test]
    fn plan_tail_with_pmax_fallback_reorganizes() {
        // 存量表仍带 pmax 哨兵时,尾部追加走 REORGANIZE 兼容路径
        let existing = vec![part("p202609", "'2026-10-01'"), part("pmax", "MAXVALUE")];
        let todo = vec![part("p202612", "'2027-01-01'")];
        let ddls = plan_month_ddls("otp_code", &existing, &todo);
        assert_eq!(ddls.len(), 1);
        assert!(ddls[0].contains("REORGANIZE PARTITION pmax"));
        assert!(ddls[0].contains("MAXVALUE"));
    }

    #[test]
    fn plan_segments_adds_contiguous_range() {
        let existing = vec![part("p0", "4000000"), part("p1", "8000000"), part("p2", "12000000")];
        let ddls = plan_segment_ddls("user", &existing, &[6]);
        assert_eq!(ddls.len(), 1);
        assert!(ddls[0].contains("ADD PARTITION"));
        for s in 3..=6 {
            assert!(ddls[0].contains(format!("p{s}").as_str()));
        }
        assert!(ddls[0].contains("VALUES LESS THAN (28000000)"));
    }

    #[test]
    fn plan_segments_below_max_is_hole_and_skipped() {
        let existing = vec![part("p0", "4000000"), part("p2", "12000000")];
        assert!(plan_segment_ddls("user", &existing, &[1]).is_empty());
    }

    #[test]
    fn no_partition_error_detection() {
        let err = DbErr::Custom("Table has no partition for value '2026-12-15'".into());
        assert!(is_no_partition(&err));
        let dup = DbErr::Custom("Duplicate partition name p202612".into());
        assert!(!is_no_partition(&dup));
        assert!(is_duplicate_partition(&dup));
    }
}
