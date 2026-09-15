//! 分区自动维护(v2.2 设计稿 §4)。
//!
//! - 月分区追加(login_history/otp_code):确保 [当前月-1, 当前月+2] 分区存在,
//!   pmax 哨兵保持空——REORGANIZE 空区=秒级元数据操作
//! - user/user_identity 水位扩段:MAX(id) 距最高实分区边界 < 400,000(90% 水位)
//!   时 REORGANIZE pmax 追加下一段(400 万)
//! - **DROP 清理暂缓**(v2.2 决策 12):代码路径保留,由 SERVER_PARTITION_DROP=true
//!   显式启用(默认 off 只记日志);启用后 login_history 清 13 个月前、otp_code 清 3 个月前
//! - 全部动作幂等(先查 information_schema.PARTITIONS);启动同步跑一次 + 每小时巡检

use chrono::Datelike;
use sea_orm::{ConnectionTrait, DatabaseConnection, DbErr, Statement};

const SEGMENT_SIZE: u64 = 4_000_000;
const SEGMENT_WATERMARK: u64 = 400_000; // 90% 水位提前扩
const MONTH_AHEAD: i32 = 2;
const MONTH_BEHIND: i32 = 1;

fn month_str(y: i32, m: u32) -> String {
    format!("{y:04}{m:02}")
}

fn month_bound(y: i32, m: u32) -> String {
    // 下月 1 号为边界
    let (ny, nm) = if m == 12 { (y + 1, 1) } else { (y, m + 1) };
    format!("'{ny:04}-{nm:02}-01'")
}

/// 已存在的分区名集合
async fn existing_partitions(db: &DatabaseConnection, table: &str) -> Result<Vec<String>, DbErr> {
    let rows = db
        .query_all(Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!(
                "SELECT PARTITION_NAME FROM information_schema.PARTITIONS \
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '{table}' \
                 AND PARTITION_NAME IS NOT NULL"
            ),
        ))
        .await?;
    Ok(rows
        .iter()
        .filter_map(|r| r.try_get_by_index::<String>(0).ok())
        .collect())
}

/// REORGANIZE pmax:把缺失的月分区插入到 pmax 之前(pmax 保持哨兵在尾)
async fn ensure_month_partitions(db: &DatabaseConnection, table: &str) -> Result<(), DbErr> {
    let existing = existing_partitions(db, table).await?;
    if !existing.iter().any(|p| p == "pmax") {
        return Ok(()); // 无哨兵(异常形态),不动
    }
    let now = chrono::Local::now();
    let (cy, cm) = (now.year(), now.month());
    let mut adds: Vec<String> = Vec::new();
    let Some(base) = chrono::NaiveDate::from_ymd_opt(cy, cm, 1) else {
        return Ok(());
    };
    for offset in -MONTH_BEHIND..=MONTH_AHEAD {
        // 符号月份偏移:负数走 sub,正数走 add(直接 as u32 会把 -1 变天文数字)
        let month = if offset >= 0 {
            base.checked_add_months(chrono::Months::new(offset as u32))
        } else {
            base.checked_sub_months(chrono::Months::new((-offset) as u32))
        };
        let Some(month) = month else { continue };
        let name = format!("p{}", month_str(month.year(), month.month()));
        if !existing.contains(&name) {
            adds.push(format!(
                "PARTITION {name} VALUES LESS THAN ({})",
                month_bound(month.year(), month.month())
            ));
        }
    }
    if adds.is_empty() {
        return Ok(());
    }
    let sql = format!(
        "ALTER TABLE `{table}` REORGANIZE PARTITION pmax INTO ({}, PARTITION pmax VALUES LESS THAN (MAXVALUE))",
        adds.join(", ")
    );
    db.execute(Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
        .await?;
    tracing::info!("[partition] {table} 追加月分区: {:?}", adds);
    Ok(())
}

/// user/user_identity 水位扩段(RANGE id,400 万/段)
async fn ensure_id_segments(db: &DatabaseConnection, table: &str, col: &str) -> Result<(), DbErr> {
    let existing = existing_partitions(db, table).await?;
    if !existing.iter().any(|p| p == "pmax") {
        return Ok(());
    }
    let max_id: u64 = db
        .query_one(Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!(
                // CAST 必须:COALESCE(BIGINT UNSIGNED, 有符号 0) 被 MySQL 提升为 DECIMAL,
                // u64 解码失败会被 .ok() 静默吞成 max_id=0,水位判断永远不触发
                "SELECT CAST(COALESCE(MAX(`{col}`), 0) AS UNSIGNED) FROM `{table}`"
            ),
        ))
        .await?
        .and_then(|r| r.try_get_by_index::<u64>(0).ok())
        .unwrap_or(0);
    // 已有最高实边界:从分区名解析(pN = 第 N 段,边界 (N+1)*400万)
    let highest_seg = existing
        .iter()
        .filter_map(|p| p.strip_prefix('p').and_then(|n| n.parse::<u64>().ok()))
        .max()
        .unwrap_or(0);
    let current_boundary = (highest_seg + 1) * SEGMENT_SIZE;
    if current_boundary.saturating_sub(max_id) > SEGMENT_WATERMARK {
        return Ok(()); // 水位充足
    }
    let next_seg = highest_seg + 1;
    let sql = format!(
        "ALTER TABLE `{table}` REORGANIZE PARTITION pmax INTO \
         (PARTITION p{next_seg} VALUES LESS THAN ({}), \
          PARTITION pmax VALUES LESS THAN (MAXVALUE))",
        (next_seg + 1) * SEGMENT_SIZE
    );
    db.execute(Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
        .await?;
    tracing::info!(
        "[partition] {table} 扩段 p{next_seg}(MAX({col})={max_id},水位 {})",
        current_boundary.saturating_sub(max_id)
    );
    Ok(())
}

/// DROP 清理(v2.2 决策 12:暂缓,SERVER_PARTITION_DROP=true 显式启用)
async fn drop_expired(db: &DatabaseConnection, table: &str, keep_months: u32) -> Result<(), DbErr> {
    let enabled = std::env::var("SERVER_PARTITION_DROP")
        .map(|v| v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    if !enabled {
        tracing::debug!("[partition] DROP 清理暂缓启用中({table} 不动,v2.2 决策 12)");
        return Ok(());
    }
    let cutoff = chrono::NaiveDate::from_ymd_opt(
        chrono::Local::now().year(),
        chrono::Local::now().month(),
        1,
    )
    .unwrap()
    .checked_sub_months(chrono::Months::new(keep_months))
    .unwrap();
    let cutoff_name = format!("p{}", month_str(cutoff.year(), cutoff.month()));
    let existing = existing_partitions(db, table).await?;
    let stale: Vec<String> = existing
        .into_iter()
        .filter(|p| {
            // 形如 pYYYYMM 且早于 cutoff 的月分区
            p.strip_prefix('p')
                .map(|n| n.parse::<u32>().is_ok())
                .unwrap_or(false)
                && p.len() == 7
                && p.as_str() < cutoff_name.as_str()
        })
        .collect();
    if stale.is_empty() {
        return Ok(());
    }
    let list = stale
        .iter()
        .map(|p| format!("`{p}`"))
        .collect::<Vec<_>>()
        .join(",");
    let sql = format!("ALTER TABLE `{table}` DROP PARTITION {list}");
    db.execute(Statement::from_string(sea_orm::DatabaseBackend::MySql, sql))
        .await?;
    tracing::info!("[partition] {table} DROP 过期分区: {stale:?}");
    Ok(())
}

/// 巡检一次(启动同步执行 + 每小时):全部失败仅 ERROR 日志,不阻断服务
pub async fn run_once(db: &DatabaseConnection) {
    for table in ["login_history", "otp_code"] {
        if let Err(e) = ensure_month_partitions(db, table).await {
            tracing::error!(error = %e, "[partition] {table} 月分区维护失败");
        }
    }
    for (table, col) in [("user", "id"), ("user_identity", "user_id")] {
        if let Err(e) = ensure_id_segments(db, table, col).await {
            tracing::error!(error = %e, "[partition] {table} 水位扩段失败");
        }
    }
    // 暂缓启用;启用时:login_history 保 13 个月,otp_code 保 3 个月
    let _ = drop_expired(db, "login_history", 13).await;
    let _ = drop_expired(db, "otp_code", 3).await;
}

/// 每小时巡检循环(tokio spawn)
pub async fn schedule(db: DatabaseConnection) {
    loop {
        tokio::time::sleep(std::time::Duration::from_secs(3600)).await;
        run_once(&db).await;
    }
}
