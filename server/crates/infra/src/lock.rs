//! 分布式锁:SET NX + 唯一 token,Lua 校验释放(对齐 Redisson RLock tryLock/unlock 语义)。
//!
//! Java 侧用法(11 个调度器 + CarrierAdminService):
//! ```java
//! RLock lock = redissonClient.getLock(LOCK_KEY);
//! if (!lock.tryLock()) return;   // 非阻塞,拿不到即放弃(多实例单飞)
//! try { work(); } finally { lock.unlock(); }
//! ```
//! Rust 对等:`try_lock` 返回 None 即放弃;Guard drop 时自动释放(token 校验防误删他人锁)。
//! Redis 缺席时:返回 LockUnavailable(调用方决定降级直跑或跳过——调度场景建议直跑,
//! 与 Java 单实例 dev 行为一致)。

use redis::aio::ConnectionManager;

const UNLOCK_LUA: &str = r#"
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
else
    return 0
end
"#;

const RENEW_LUA: &str = r#"
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('PEXPIRE', KEYS[1], ARGV[2])
else
    return 0
end
"#;

pub enum TryLockResult {
    /// 拿到锁;Guard drop 自动释放
    Acquired(LockGuard),
    /// 他人持有(对齐 tryLock() == false,调用方直接 return)
    Busy,
    /// Redis 不可用(调用方决定直跑或跳过)
    Unavailable(String),
}

pub struct LockGuard {
    conn: ConnectionManager,
    key: String,
    token: String,
}

impl LockGuard {
    /// 显式续期(长任务;PEXPIRE 校验 token)
    pub async fn renew(&self, ttl_ms: u64) -> Result<(), String> {
        let mut conn = self.conn.clone();
        let ok: i64 = redis::Script::new(RENEW_LUA)
            .key(&self.key)
            .arg(&self.token)
            .arg(ttl_ms as i64)
            .invoke_async(&mut conn)
            .await
            .map_err(|e| e.to_string())?;
        if ok != 1 {
            return Err(format!("lock {} lost (token mismatch)", self.key));
        }
        Ok(())
    }
}

impl Drop for LockGuard {
    fn drop(&mut self) {
        let conn = self.conn.clone();
        let key = self.key.clone();
        let token = self.token.clone();
        // drop 不能 async:spawn 后台释放;失败仅告警(等 TTL 自然过期,锁 key 均带 ttl 兜底)
        tokio::spawn(async move {
            let res: Result<i64, _> = redis::Script::new(UNLOCK_LUA)
                .key(&key)
                .arg(&token)
                .invoke_async(&mut conn.clone())
                .await;
            if let Err(err) = res {
                tracing::warn!("[lock] unlock {key} failed (waits ttl):{err}");
            }
        });
    }
}

/// 非阻塞 tryLock。ttl 为锁自动过期兜底(持有者崩溃后锁可自愈;Java Redisson 默认 30s watchdog 语义近似)。
pub async fn try_lock(
    conn: &ConnectionManager,
    key: &str,
    ttl_ms: u64,
) -> TryLockResult {
    let token = uuid::Uuid::new_v4().to_string();
    let mut c = conn.clone();
    let ok: Option<String> = match redis::cmd("SET")
        .arg(key)
        .arg(&token)
        .arg("NX")
        .arg("PX")
        .arg(ttl_ms as i64)
        .query_async(&mut c)
        .await
    {
        Ok(v) => v,
        Err(err) => return TryLockResult::Unavailable(err.to_string()),
    };
    match ok {
        Some(_) => TryLockResult::Acquired(LockGuard {
            conn: conn.clone(),
            key: key.to_string(),
            token,
        }),
        None => TryLockResult::Busy,
    }
}

/// 调度器惯用法:拿锁跑 `job`,拿不到(他实例在跑)静默返回;
/// Redis 不可用时**直跑**(对齐 Java 单实例 dev:redisson 缺席场景不存在,但本地 stub 环境需等价行为)。
pub async fn with_lock_or_run<F, Fut>(conn: Option<&ConnectionManager>, key: &str, ttl_ms: u64, job: F)
where
    F: FnOnce() -> Fut,
    Fut: std::future::Future<Output = ()>,
{
    match conn {
        None => job().await,
        Some(c) => match try_lock(c, key, ttl_ms).await {
            TryLockResult::Acquired(_guard) => job().await,
            TryLockResult::Busy => {
                tracing::debug!("[lock] {key} busy (another instance), skip");
            }
            TryLockResult::Unavailable(err) => {
                tracing::warn!("[lock] {key} redis unavailable, run unlocked:{err}");
                job().await;
            }
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // 无 Redis 环境仅验证降级语义;真实锁竞争用例并入 identity/tests(带环境闸门)。
    #[tokio::test]
    async fn without_redis_runs_directly() {
        let ran = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(false));
        let r = ran.clone();
        with_lock_or_run(None, "test:lock", 1_000, move || async move {
            r.store(true, std::sync::atomic::Ordering::SeqCst);
        })
        .await;
        assert!(ran.load(std::sync::atomic::Ordering::SeqCst));
    }
}
