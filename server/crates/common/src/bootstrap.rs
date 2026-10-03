//! 空库自举:CREATE DATABASE / TABLE IF NOT EXISTS,幂等且绝不破坏存量。
//! (一次性数据迁移是独立操作,见 scripts/migrate-identity.sh —— 与本模块无交集)

use sea_orm::{ConnectOptions, ConnectionTrait, Database, DatabaseConnection, DbErr, Statement};

/// 显式连接池:默认值(max 10/min 0/acquire 30s)在过载时会长时间挂起,这里收紧
pub fn pool_opts(url: &str) -> ConnectOptions {
    let mut opts = ConnectOptions::new(url.to_owned());
    opts.max_connections(50)
        .min_connections(5)
        .acquire_timeout(std::time::Duration::from_secs(3));
    opts
}

/// 连接主库(不存在则先建库),并应用 schema/identity.sql(全部为 CREATE ... IF NOT EXISTS)
pub async fn connect_main(cfg: &crate::config::Config) -> Result<DatabaseConnection, DbErr> {
    let base = Database::connect(pool_opts(&cfg.db_base_url())).await?;
    for db_name in [&cfg.db_name, &cfg.biz_db_name] {
        base.execute(Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            format!(
                "CREATE DATABASE IF NOT EXISTS `{}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",
                db_name
            ),
        ))
        .await?;
    }

    let main = Database::connect(pool_opts(&cfg.db_main_url())).await?;
    apply_schema(&main, &cfg.schema_path).await?;
    // 业务库(identity):业务表结构快照(源自 20260918 整库备份;Java 删除后 catalog/trading/content
    // 等业务 DDL 的唯一载体) + 域扩展 DDL。全部 CREATE TABLE IF NOT EXISTS,幂等;详见文件头注。
    // 注意:业务表必须建在 biz_db(marting 域 svc 全部经 state.biz_db 访问),不能落主库。
    let biz = Database::connect(pool_opts(&cfg.db_biz_url())).await?;
    apply_schema(&biz, "schema/business-legacy.sql").await?;
    apply_schema(&biz, "schema/marketing-base.sql").await?;
    Ok(main)
}

async fn apply_schema(conn: &DatabaseConnection, path: &str) -> Result<(), DbErr> {
    let sql = match std::fs::read_to_string(path) {
        Ok(s) => s,
        Err(err) => {
            tracing::warn!("[bootstrap] schema 文件不可读({path}:{err}),跳过自举(依赖外部建表)");
            return Ok(());
        }
    };
    let mut applied = 0usize;
    for stmt in split_statements(&sql) {
        conn.execute(Statement::from_string(
            sea_orm::DatabaseBackend::MySql,
            stmt,
        ))
        .await?;
        applied += 1;
    }
    tracing::info!("[bootstrap] schema 自举完成:执行 {applied} 条语句({path})");
    Ok(())
}

/// 简单 DDL 切分:剥注释行,按行尾分号切分(本文件仅含 CREATE TABLE,无存储过程体/字符串内分号)
fn split_statements(sql: &str) -> Vec<String> {
    let mut stmts = Vec::new();
    let mut current = String::new();
    for line in sql.lines() {
        let trimmed = line.trim_start();
        if trimmed.starts_with("--") || trimmed.starts_with('#') {
            continue;
        }
        current.push_str(line);
        current.push('\n');
        if trimmed.ends_with(';') {
            let stmt = current.trim().to_string();
            if stmt != ";" {
                stmts.push(stmt);
            }
            current.clear();
        }
    }
    let tail = current.trim();
    if !tail.is_empty() {
        stmts.push(tail.to_string());
    }
    stmts
}

#[cfg(test)]
mod tests {
    use super::split_statements;

    #[test]
    fn splits_and_strips_comments() {
        let sql = "-- header comment\nCREATE TABLE IF NOT EXISTS `a` (\n  id BIGINT\n);\n# another\nCREATE TABLE IF NOT EXISTS `b` (id BIGINT);\n";
        let stmts = split_statements(sql);
        assert_eq!(stmts.len(), 2);
        assert!(stmts[0].starts_with("CREATE TABLE IF NOT EXISTS `a`"));
        assert!(stmts[0].ends_with(';'));
        assert!(stmts[1].contains("`b`"));
    }
}
