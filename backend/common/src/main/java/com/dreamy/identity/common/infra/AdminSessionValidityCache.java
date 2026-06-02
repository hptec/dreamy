package com.dreamy.identity.common.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Admin 会话有效性缓存（admin:session:valid:{tokenId}，仅 Redis 单级 TTL30s）。
 * 约束: BLOCKER-1 admin 会话撤销/登出/禁用即时生效（api-detail §0「会话有效性：admin 校验 admin_session.status=active」）；
 * 复用 SessionValidityCache 模式；DG-003（Redis 不可用/未命中降级查 DB，记 ERROR/WARN 告警）；
 * EDGE-014 禁用管理员级联撤销后 token 在 8h 自然过期前即失效。
 */
@Component
public class AdminSessionValidityCache {

    private static final Logger log = LoggerFactory.getLogger(AdminSessionValidityCache.class);
    private static final String PREFIX = "admin:session:valid:";
    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;

    public AdminSessionValidityCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** FLOW-09 登录提交后写有效性键 */
    public void markValid(String tokenId) {
        try {
            redis.opsForValue().set(PREFIX + tokenId, "1", TTL);
        } catch (Exception ex) {
            // 写失败不回滚 DB，仅告警（兜底 TTL 自然过期 + DB 校验）
            log.warn("[CACHE] admin markValid failed tokenId={}", tokenId);
        }
    }

    /** 会话校验：Redis 命中即有效；不可用/未命中返回 false 由调用方降级查 DB（DG-003） */
    public boolean isValid(String tokenId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(PREFIX + tokenId));
        } catch (Exception ex) {
            log.error("[CACHE] admin isValid degraded to DB tokenId={} (DG-003)", tokenId);
            return false;
        }
    }

    /** adminLogout / 禁用级联撤销：DEL 单级键（即时生效，无残留窗口） */
    public void invalidate(String tokenId) {
        try {
            redis.delete(PREFIX + tokenId);
        } catch (Exception ex) {
            log.warn("[CACHE] admin invalidate failed tokenId={}", tokenId);
        }
    }
}
