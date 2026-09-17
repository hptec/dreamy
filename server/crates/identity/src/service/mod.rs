//! 身份域领域服务:业务规则与数据访问(P1-P3 从 Java 对应 Service 移植)。
//!
//! 性能纪律(用户指令):每个接口 Redis-first + 主动失效,Redis 故障一律降级 DB 不失败。

pub mod account;
pub mod admin_auth;
pub mod admin_ops;
pub mod auth;
pub mod authconfig;
pub mod demo_user;
pub mod merge;
pub mod otp;
pub mod permissions;
pub mod ratelimit;
pub mod session;
pub mod user_query;

pub use common::error::SvcError;
pub use user_query::{Col, Cond, CondVal, ListQuery, Lookup, UserView};

use common::state::SharedState;

/// user 注册成功后的水位预扩(identity 域策略:双表 user/user_identity;机制在 common::partition)。
/// 写入后检查——id 只有 INSERT 成功才拿到,且应用层零查询成本;探测 id+10万 所在段,
/// 提前建段避免后续注册撞 1526。
pub async fn ensure_user_segments(state: &SharedState, user_id: u64) {
    const SEGMENT_WATERMARK: u64 = 100_000;
    let probe = user_id.saturating_add(SEGMENT_WATERMARK);
    for table in ["user", "user_identity"] {
        if let Err(e) = common::partition::ensure_id_segments(state, table, &[probe]).await {
            tracing::error!(error = %e, "[partition] {table} 水位段保障失败(id={user_id})");
        }
    }
}
