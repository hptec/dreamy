package com.dreamy.identity.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 会话有效性缓存（store:session:valid:{tokenId}，仅 Redis 单级 TTL30s）。
 * 约束: shared-contracts cache.remote_only；QP-003（优先读 Redis）；EC-002（失效失败不回滚 DB，兜底 TTL 自然过期）；
 * EDGE-023（强制下线 DEL 后全集群即时生效）；DG-003（Redis 不可用降级查 DB，记 ERROR 告警）。
 */
@Component
public class SessionValidityCache {

    private static final Logger log = LoggerFactory.getLogger(SessionValidityCache.class);
    private static final String PREFIX = "store:session:valid:";
    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;

    public SessionValidityCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** STEP-09：事务提交后写有效性键 */
    public void markValid(String tokenId) {
        try {
            redis.opsForValue().set(PREFIX + tokenId, "1", TTL);
        } catch (Exception ex) {
            // EC-002：写失败不回滚 DB，仅告警
            log.warn("[CACHE] markValid failed tokenId={} (EC-002 fallback)", tokenId);
        }
    }

    /** 会话校验：Redis 命中即有效；不可用/未命中返回 false 由调用方降级查 DB（DG-003） */
    public boolean isValid(String tokenId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(PREFIX + tokenId));
        } catch (Exception ex) {
            log.error("[CACHE] isValid degraded to DB tokenId={} (DG-003)", tokenId);
            return false;
        }
    }

    /** FLOW-07/12 撤销：DEL 单级键（即时生效，无残留窗口） */
    public void invalidate(String tokenId) {
        try {
            redis.delete(PREFIX + tokenId);
        } catch (Exception ex) {
            log.warn("[CACHE] invalidate failed tokenId={} (EC-002)", tokenId);
        }
    }
}
