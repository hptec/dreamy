package com.dreamy.infra;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheGetResult;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.CacheResult;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.template.QuickConfig;
import com.dreamy.dto.AnalyticsDtos.AnalyticsTrafficResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * analytics 域缓存（CACHE-ANA-001~005，BE-DIM-8）。
 * - 聚合层 `analytics:`（dashboard / overview:{range}）：JetCache 两级 TTL 60s，失效=TTL 自然过期（决策 10）。
 * - 流量层 `analytics:traffic:`（{range}）：两级 TTL 300s（决策 19）；降级体短 TTL 60s（CACHE-ANA-005）。
 * - stale 快照 `analytics:traffic:stale:`（{range}）：remote-only（Redis 单级）TTL 24h——跨实例共享
 *   同一「最近成功」副本（CACHE-ANA-004），每次 GA4 成功拉取覆盖写。
 * - key 维度 range ∈ {7d,30d,90d}（V-ANA-002 先行校验，key 空间有界无穿透面）；无 locale 维度；不经 CDN。
 * - 聚合层读写失败静默降级回源；流量 stale/降级体读写失败显式抛 CacheAccessException
 *   （DEC-ANA-5 ⑤ 判定点：兜底链自身失效 → 502001/504001）。
 */
@Component
public class AnalyticsCacheService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsCacheService.class);

    /** CACHE-ANA-001 */
    public static final String KEY_DASHBOARD = "dashboard";

    private static final Duration AGG_TTL = Duration.ofSeconds(60);
    private static final Duration TRAFFIC_TTL = Duration.ofSeconds(300);
    private static final Duration STALE_TTL = Duration.ofHours(24);
    /** CACHE-ANA-005 降级体短 TTL（故障期防反复打 GA4） */
    private static final long DEGRADED_TTL_SECONDS = 60;

    /** 缓存基础设施访问失败（DEC-ANA-5 ⑤ 触发源） */
    public static class CacheAccessException extends RuntimeException {
        public CacheAccessException(String message) {
            super(message);
        }
    }

    private final CacheManager cacheManager;
    private Cache<String, Object> aggCache;
    private Cache<String, Object> trafficCache;
    private Cache<String, Object> staleCache;

    public AnalyticsCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @PostConstruct
    void initCaches() {
        aggCache = cacheManager.getOrCreateCache(QuickConfig.newBuilder("analytics:")
                .cacheType(CacheType.BOTH)
                .expire(AGG_TTL).localExpire(AGG_TTL)
                .localLimit(16)
                .cacheNullValue(false)
                .syncLocal(false)
                .penetrationProtect(true)
                .build());
        trafficCache = cacheManager.getOrCreateCache(QuickConfig.newBuilder("analytics:traffic:")
                .cacheType(CacheType.BOTH)
                .expire(TRAFFIC_TTL).localExpire(TRAFFIC_TTL)
                .localLimit(8)
                .cacheNullValue(false)
                .syncLocal(false)
                .penetrationProtect(true)
                .build());
        staleCache = cacheManager.getOrCreateCache(QuickConfig.newBuilder("analytics:traffic:stale:")
                .cacheType(CacheType.REMOTE)
                .expire(STALE_TTL)
                .cacheNullValue(false)
                .penetrationProtect(false)
                .build());
    }

    // ===== 聚合层（CACHE-ANA-001/002，失败静默降级回源）=====

    /** STEP-ANA-01 读 analytics:dashboard / analytics:overview:{range}；失败按未命中处理 */
    @SuppressWarnings("unchecked")
    public <T> T getAggregate(String key) {
        try {
            return (T) aggCache.get(key);
        } catch (Exception ex) {
            log.warn("[CACHE-ANA] get failed key=analytics:{} (degrade to source)", key);
            return null;
        }
    }

    /** 聚合层回填（TTL 60s）；失败仅告警（下一请求回源） */
    public void putAggregate(String key, Object value) {
        try {
            aggCache.put(key, value);
        } catch (Exception ex) {
            log.warn("[CACHE-ANA] put failed key=analytics:{}", key);
        }
    }

    /** CACHE-ANA-002 key：analytics:overview:{range} */
    public static String overviewKey(String range) {
        return "overview:" + range;
    }

    // ===== 流量层（CACHE-ANA-003/004/005）=====

    /** STEP-ANA-01 读 analytics:traffic:{range}（fresh 或降级体）；失败按未命中处理（仍可走 GA4/stale 链） */
    public AnalyticsTrafficResponse getTraffic(String range) {
        try {
            return (AnalyticsTrafficResponse) trafficCache.get(range);
        } catch (Exception ex) {
            log.warn("[CACHE-ANA] traffic get failed range={} (treat as miss)", range);
            return null;
        }
    }

    /** STEP-ANA-03 成功写 fresh（300s）；失败仅告警（不影响本次 200 响应） */
    public void putTrafficFresh(String range, AnalyticsTrafficResponse response) {
        try {
            trafficCache.put(range, response);
        } catch (Exception ex) {
            log.warn("[CACHE-ANA] traffic fresh put failed range={}", range);
        }
    }

    /** STEP-ANA-03 成功覆盖写 stale 快照（24h remote-only）；失败仅告警 */
    public void putStale(String range, AnalyticsTrafficResponse response) {
        try {
            CacheResult result = staleCache.PUT(range, response);
            if (!result.isSuccess()) {
                log.warn("[CACHE-ANA] stale put not success range={} code={}", range, result.getResultCode());
            }
        } catch (Exception ex) {
            log.warn("[CACHE-ANA] stale put failed range={}", range);
        }
    }

    /**
     * STEP-ANA-04 读 stale 快照（DEC-ANA-5 ③）。
     * 基础设施失败（Redis 异常）→ CacheAccessException（⑤ 判定）；不存在 → null。
     */
    public AnalyticsTrafficResponse getStale(String range) {
        CacheGetResult<Object> result;
        try {
            result = staleCache.GET(range);
        } catch (Exception ex) {
            throw new CacheAccessException("stale GET failed: " + ex.getClass().getSimpleName());
        }
        if (result.isSuccess()) {
            return (AnalyticsTrafficResponse) result.getValue();
        }
        if (result.getResultCode() == com.alicp.jetcache.CacheResultCode.FAIL) {
            throw new CacheAccessException("stale GET failed: " + result.getMessage());
        }
        return null;
    }

    /**
     * STEP-ANA-05 降级体写 analytics:traffic:{range} 短 TTL 60s（CACHE-ANA-005）。
     * 基础设施失败 → CacheAccessException（⑤ 判定）。
     */
    public void putTrafficDegraded(String range, AnalyticsTrafficResponse degraded) {
        CacheResult result;
        try {
            result = trafficCache.PUT(range, degraded, DEGRADED_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new CacheAccessException("degraded PUT failed: " + ex.getClass().getSimpleName());
        }
        if (result.getResultCode() == com.alicp.jetcache.CacheResultCode.FAIL) {
            throw new CacheAccessException("degraded PUT failed: " + result.getMessage());
        }
    }
}
