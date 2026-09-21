//! 两级缓存:moka 本地(expire_after_write)+ redis 远端,null 穿透标记。
//!
//! 对齐 Java JetCache 用法(local caffeine 5min + remote redis-lettuce,kryo5 编码):
//! - 值编码用 JSON(kryo 是 Java 生态产物,Rust 侧 JSON 语义等价);
//! - null 穿透保护:`__<area>_null__` 标记值(对齐 CatalogCacheService.NULL_MARKER);
//! - 缓存操作失败不影响主流程(记 WARN,不回滚 DB,EC-CAT-002 口径);
//! - 代际失效(generation)由域服务按需使用,infra 提供键规约,不实现 Lua 脚本
//!   (Java 侧代际脚本与 CatalogCacheService 强耦合,域迁移时随域实现)。

use std::time::Duration;

use moka::sync::Cache as MokaCache;
use redis::aio::ConnectionManager;
use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};

/// null 穿透标记生成(area 隔离,避免与真实值碰撞)
pub fn null_marker(area: &str) -> String {
    format!("__{area}_null__")
}

fn is_null_marker(area: &str, raw: &str) -> bool {
    raw == null_marker(area)
}

/// 两级缓存句柄。clone 廉价(moka Cache 内部 Arc,ConnectionManager 可 clone)。
#[derive(Clone)]
pub struct TwoTierCache {
    local: MokaCache<String, String>,
    redis: Option<ConnectionManager>,
    /// 远端键前缀(如 "cc:catalog:"),本地键即裸 key
    prefix: String,
    /// 本地 TTL(millis);远端 TTL 通常更长
    /// 保留供运行时诊断(与 remote ttl 分开展示)
    #[allow(dead_code)]
    local_ttl_ms: u64,
    remote_ttl_secs: u64,
}

#[derive(Serialize, Deserialize)]
struct Envelope {
    /// JSON 编码的缓存值;None 表示 null 标记
    v: Option<String>,
}

impl TwoTierCache {
    pub fn new(
        redis: Option<ConnectionManager>,
        prefix: &str,
        local_max_capacity: u64,
        // local_ttl_ms 保留供运行时诊断(与 remote ttl 分开展示)
        #[allow(unused_variables)]
        local_ttl_ms: u64,
        remote_ttl_secs: u64,
    ) -> Self {
        TwoTierCache {
            local: MokaCache::builder()
                .max_capacity(local_max_capacity)
                .time_to_live(Duration::from_millis(local_ttl_ms))
                .build(),
            redis,
            prefix: format!("{prefix}:"),
            local_ttl_ms,
            remote_ttl_secs,
        }
    }

    /// Java JetCache 对齐缺省:本地 5min,远端 30min
    pub fn jetcache_default(redis: Option<ConnectionManager>, area: &str) -> Self {
        Self::new(redis, &format!("cc:{area}"), 10_000, 300_000, 1_800)
    }

    fn remote_key(&self, key: &str) -> String {
        format!("{}{}", self.prefix, key)
    }

    /// 读:本地命中 → 远端回填 → miss
    /// 返回 None 表示未命中;Some(None) 语义由调用方用 `get_or_load` 简化,此处裸值即 JSON 字符串。
    pub async fn get(&self, key: &str) -> Option<String> {
        if let Some(hit) = self.local.get(key) {
            return Some(hit);
        }
        let mut redis = self.redis.clone()?;
        let raw: Option<String> = redis::cmd("GET")
            .arg(self.remote_key(key))
            .query_async(&mut redis)
            .await
            .ok()?;
        let raw = raw?;
        self.local.insert(key.to_string(), raw.clone());
        Some(raw)
    }

    /// 读并判空穿透:Ok(Some(v)) 命中;Ok(None) 未命中;Err 缓存故障(调用方降级 DB)
    pub async fn get_typed<T: DeserializeOwned>(
        &self,
        area: &str,
        key: &str,
    ) -> Result<Option<Option<T>>, String> {
        match self.get(key).await {
            None => Ok(None),
            Some(raw) if is_null_marker(area, &raw) => Ok(Some(None)),
            Some(raw) => {
                let env: Envelope =
                    serde_json::from_str(&raw).map_err(|e| format!("cache decode: {e}"))?;
                let v = env
                    .v
                    .map(|inner| serde_json::from_str(&inner).map_err(|e| format!("cache value: {e}")))
                    .transpose()?;
                Ok(Some(v))
            }
        }
    }

    /// 写:本地 + 远端;value=None 时写 null 标记(防穿透)
    pub async fn set_typed<T: Serialize>(
        &self,
        area: &str,
        key: &str,
        value: &Option<T>,
    ) -> Result<(), String> {
        let raw = match value {
            None => null_marker(area),
            Some(v) => {
                let inner = serde_json::to_string(v).map_err(|e| e.to_string())?;
                serde_json::to_string(&Envelope {
                    v: Some(inner),
                })
                .map_err(|e| e.to_string())?
            }
        };
        self.local.insert(key.to_string(), raw.clone());
        if let Some(mut redis) = self.redis.clone() {
            redis::cmd("SETEX")
                .arg(self.remote_key(key))
                .arg(self.remote_ttl_secs)
                .arg(raw)
                .query_async::<()>(&mut redis)
                .await
                .map_err(|e| format!("cache setex: {e}"))?;
        }
        Ok(())
    }

    /// 写即失效(对齐 Java @CacheInvalidate):本地逐出 + 远端 DEL
    pub async fn invalidate(&self, key: &str) -> Result<(), String> {
        self.local.invalidate(key);
        if let Some(mut redis) = self.redis.clone() {
            redis::cmd("DEL")
                .arg(self.remote_key(key))
                .query_async::<()>(&mut redis)
                .await
                .map_err(|e| format!("cache del: {e}"))?;
        }
        Ok(())
    }

    /// 前缀批量失效(SCAN + DEL;@CacheInvalidate(multi=true) 对齐)
    pub async fn invalidate_prefix(&self, prefix: &str) -> Result<u64, String> {
        self.local.invalidate_all();
        let Some(mut redis) = self.redis.clone() else {
            return Ok(0);
        };
        let pattern = format!("{}{}*", self.prefix, prefix);
        let mut cursor: u64 = 0;
        let mut deleted = 0u64;
        loop {
            let (next, keys): (u64, Vec<String>) = redis::cmd("SCAN")
                .arg(cursor)
                .arg("MATCH")
                .arg(&pattern)
                .arg("COUNT")
                .arg(500)
                .query_async(&mut redis)
                .await
                .map_err(|e| format!("scan: {e}"))?;
            if !keys.is_empty() {
                deleted += redis::cmd("DEL")
                    .arg(&keys)
                    .query_async::<u64>(&mut redis)
                    .await
                    .unwrap_or(0);
            }
            cursor = next;
            if cursor == 0 {
                break;
            }
        }
        Ok(deleted)
    }

    /// 缓存旁路主入口:命中即返回;未命中 load(含 None 结果 → null 标记);
    /// 缓存故障一律降级直接 load(不影响主流程)。
    pub async fn get_or_load<T, F, Fut>(&self, area: &str, key: &str, load: F) -> Result<Option<T>, String>
    where
        T: Serialize + DeserializeOwned + Clone,
        F: FnOnce() -> Fut,
        Fut: std::future::Future<Output = Result<Option<T>, String>>,
    {
        match self.get_typed::<T>(area, key).await {
            Ok(Some(v)) => return Ok(v),
            Ok(None) => {}
            Err(err) => tracing::warn!("[cache] read fail area={area} key={key} (fallback load):{err}"),
        }
        let loaded = load().await?;
        if let Err(err) = self.set_typed(area, key, &loaded).await {
            tracing::warn!("[cache] write fail area={area} key={key} (ttl degrade):{err}");
        }
        Ok(loaded)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn cache_with_marker_area() -> TwoTierCache {
        TwoTierCache::new(None, "cc:test", 100, 60_000, 60)
    }

    #[tokio::test]
    async fn local_only_roundtrip() {
        let c = cache_with_marker_area();
        let v: Option<serde_json::Value> = Some(json!({"id": 1}));
        c.set_typed("test", "k1", &v).await.unwrap();
        let got: Option<Option<serde_json::Value>> = c.get_typed("test", "k1").await.unwrap();
        assert_eq!(got, Some(Some(json!({"id": 1}))));
    }

    #[tokio::test]
    async fn null_penetration_marker() {
        let c = cache_with_marker_area();
        let none: Option<serde_json::Value> = None;
        c.set_typed("test", "k-null", &none).await.unwrap();
        // 命中且语义为「已确认不存在」
        let got: Option<Option<serde_json::Value>> = c.get_typed("test", "k-null").await.unwrap();
        assert_eq!(got, Some(None));
        assert!(c.get("k-null").await.unwrap().contains("__test_null__"));
    }

    #[tokio::test]
    async fn get_or_load_caches_and_degrades() {
        let c = cache_with_marker_area();
        let calls = std::sync::Arc::new(std::sync::atomic::AtomicUsize::new(0));
        let c2 = calls.clone();
        let load = || {
            let n = c2.clone();
            async move {
                n.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
                Ok::<_, String>(Some(json!({"n": 42})))
            }
        };
        let a = c.get_or_load::<serde_json::Value, _, _>("t", "x", load).await.unwrap();
        let b = c.get_or_load::<serde_json::Value, _, _>("t", "x", load).await.unwrap();
        assert_eq!(a, b);
        assert_eq!(calls.load(std::sync::atomic::Ordering::SeqCst), 1, "第二次应命中缓存");
    }

    #[tokio::test]
    async fn invalidate_clears_local() {
        let c = cache_with_marker_area();
        let v: Option<String> = Some("v".into());
        c.set_typed("t", "k", &v).await.unwrap();
        c.invalidate("k").await.unwrap();
        let got: Option<Option<String>> = c.get_typed("t", "k").await.unwrap();
        assert_eq!(got, None, "失效后应 miss(无 redis,远端无从恢复)");
    }
}
