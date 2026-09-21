//! 横切基础设施(全域共享):mq / sched / cache / lock。
//!
//! 语义对齐 Java backend 对应实现,迁移期间保持信封/键名/行为一致:
//! - `mq::DomainEvent` 信封字段 snake_case(event_id/type/occurred_at/payload),消费按 event_id 幂等;
//! - `mq` stub 模式同步直调进程内订阅者(dev 零外部依赖),real 模式 RabbitMQ topic exchange;
//! - `sched` 支持 6 字段 cron(秒 分 时 日 月 周,对齐 Spring @Scheduled)与 fixedDelay;
//! - `cache` 两级:本地 moka(expire_after_write 5min)+ 远端 redis,null 穿透标记;
//! - `lock`:SET NX + 唯一 token Lua 释放,非阻塞 tryLock(对齐 Redisson 用法)。
//!
//! 基础设施失败降级原则(对齐 Java 侧约定):缓存/MQ 失败记告警日志,不回滚本地事务,
//! 新鲜度退化为 TTL 级或触发补偿,主流程不损。

pub mod cache;
pub mod lock;
pub mod mq;
pub mod sched;
