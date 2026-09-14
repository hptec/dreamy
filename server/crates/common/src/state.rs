//! 进程级共享状态:域 crate 的 handler 经 [`SharedState`] 取依赖。

use sea_orm::DatabaseConnection;

use crate::config::Config;

pub struct AppState {
    /// 主库连接(dreamy_server,身份域 11 张表)
    pub db: DatabaseConnection,
    /// 次库连接(identity 库,共享过渡表 operation_log / email_template;可能缺席)
    pub db_legacy: Option<DatabaseConnection>,
    pub redis: Option<redis::Client>,
    pub cfg: Config,
}

pub type SharedState = std::sync::Arc<AppState>;
