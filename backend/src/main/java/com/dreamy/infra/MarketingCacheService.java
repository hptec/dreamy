package com.dreamy.infra;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.template.QuickConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * marketing 域消费端缓存（JetCache 两级 Caffeine+Redis，CACHE-MKT-001~009）。
 * - key 模板与 TTL 与 data-flow 缓存矩阵逐行一致；key 含 locale 不含 currency（决策 13 / §8）。
 * - FLASH 族 TTL 60s（倒计时新鲜度兜底，CACHE-MKT-009）。
 * - 穿透保护（BE-DIM-8）：BLOG/WEDDING/LOOKBOOK 详情族 null 标记值短 TTL 60s。
 * - 模式失效：每个 family 使用 Redis 共享代际号。写路径提交后原子递增代际，所有实例立即切换
 *   到新 namespace；旧代际数据由 60~300s TTL 自然回收。该机制不依赖单进程 key 注册表，
 *   因而服务重启及多实例场景也不会继续命中旧内容。
 * - 缓存操作失败不影响主流程（EC-MKT-002：记告警，不回滚 DB）。
 */
@Component
public class MarketingCacheService {

    private static final Logger log = LoggerFactory.getLogger(MarketingCacheService.class);

    /** null 穿透保护标记值（CACHE-MKT-003/005/007） */
    public static final String NULL_MARKER = "__marketing_null__";

    private static final String GENERATION_KEY_PREFIX = "marketing:cache-generation:";
    private static final DefaultRedisScript<Long> GENERATION_SCRIPT = new DefaultRedisScript<>("""
            local generation_key = KEYS[1]
            local high_water_key = KEYS[2]
            local local_generation = tonumber(ARGV[1]) or 0
            local must_advance = ARGV[2] == '1'
            local remote_generation = tonumber(redis.call('GET', generation_key))
            local high_water = tonumber(redis.call('GET', high_water_key)) or 0
            local consistent = remote_generation ~= nil
                and remote_generation >= local_generation
                and remote_generation >= high_water

            if consistent and not must_advance then
                if remote_generation > high_water then
                    redis.call('SET', high_water_key, string.format('%.0f', remote_generation))
                end
                return remote_generation
            end

            local base = math.max(local_generation, high_water)
            if remote_generation ~= nil then
                base = math.max(base, remote_generation)
            end
            local next_generation = base + 1
            if remote_generation == nil then
                local now = redis.call('TIME')
                local time_generation = tonumber(now[1]) * 1000000 + tonumber(now[2])
                next_generation = math.max(next_generation, time_generation)
            end

            local encoded = string.format('%.0f', next_generation)
            redis.call('SET', generation_key, encoded)
            redis.call('SET', high_water_key, encoded)
            return next_generation
            """, Long.class);

    /** 缓存族（key 前缀=族名，CACHE-MKT-001~009） */
    public enum Family {
        BANNERS("marketing:banners:", Duration.ofSeconds(300)),
        BLOGS("marketing:blogs:", Duration.ofSeconds(300)),
        BLOG("marketing:blog:", Duration.ofSeconds(300)),
        WEDDINGS("marketing:weddings:", Duration.ofSeconds(300)),
        WEDDING("marketing:wedding:", Duration.ofSeconds(300)),
        LOOKBOOKS("marketing:lookbooks:", Duration.ofSeconds(300)),
        LOOKBOOK("marketing:lookbook:", Duration.ofSeconds(300)),
        GUIDES("marketing:guides:", Duration.ofSeconds(300)),
        FLASH("marketing:flash:", Duration.ofSeconds(60));

        final String prefix;
        final Duration ttl;

        Family(String prefix, Duration ttl) {
            this.prefix = prefix;
            this.ttl = ttl;
        }

        public Duration ttl() {
            return ttl;
        }
    }

    private final CacheManager cacheManager;
    private final StringRedisTemplate redis;
    private final Map<Family, Cache<String, Object>> caches = new ConcurrentHashMap<>();
    private final Map<Family, GenerationState> generationStates = new ConcurrentHashMap<>();

    private static final class GenerationState {
        private final AtomicLong generation = new AtomicLong();
        private final AtomicLong fallbackRevision = new AtomicLong();
        private final AtomicLong reconciledRevision = new AtomicLong();
    }

    public MarketingCacheService(CacheManager cacheManager, StringRedisTemplate redis) {
        this.cacheManager = cacheManager;
        this.redis = redis;
    }

    @PostConstruct
    void initCaches() {
        for (Family family : Family.values()) {
            QuickConfig qc = QuickConfig.newBuilder(family.prefix)
                    .cacheType(CacheType.BOTH)
                    .expire(family.ttl)
                    .localExpire(family.ttl)
                    .localLimit(2000)
                    .cacheNullValue(false)
                    .syncLocal(false)
                    .build();
            caches.put(family, cacheManager.getOrCreateCache(qc));
            generationStates.put(family, new GenerationState());
        }
    }

    /**
     * 一次缓存读取的不可变上下文。generation 在源数据加载前捕获，后续 put 仍写入该代际；
     * 若期间发生失效，新请求已切到下一代，不会被并发回填的旧值污染。
     */
    public record Lookup(Family family, String key, long generation, Object value) {
    }

    /** 读缓存；NULL_MARKER 命中由调用方判定（isNullMarker） */
    public Lookup lookup(Family family, String key) {
        long generation = currentGeneration(family);
        try {
            Object value = caches.get(family).get(versionedKey(generation, key));
            return new Lookup(family, key, generation, value);
        } catch (Exception ex) {
            log.warn("[CACHE-MKT] get failed family={} key={} (EC-MKT-002 degrade to source)", family, key);
            return new Lookup(family, key, generation, null);
        }
    }

    /** 写缓存（族缺省 TTL） */
    public void put(Lookup lookup, Object value) {
        try {
            caches.get(lookup.family()).put(versionedKey(lookup.generation(), lookup.key()), value);
        } catch (Exception ex) {
            log.warn("[CACHE-MKT] put failed family={} key={} (EC-MKT-002)", lookup.family(), lookup.key());
        }
    }

    /** 写 null 标记（穿透保护，60s 短 TTL——BE-DIM-8） */
    public void putNullMarker(Lookup lookup) {
        try {
            caches.get(lookup.family()).put(
                    versionedKey(lookup.generation(), lookup.key()), NULL_MARKER, 60, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("[CACHE-MKT] putNull failed family={} key={} (EC-MKT-002)",
                    lookup.family(), lookup.key());
        }
    }

    public boolean isNullMarker(Object cached) {
        return NULL_MARKER.equals(cached);
    }

    /** family 级失效（共享代际 + TTL 回收，实现 `marketing:{family}:*` 语义） */
    void invalidateFamily(Family family) {
        GenerationState state = generationStates.get(family);
        try {
            invalidateFamilyStrict(family);
        } catch (Exception ex) {
            long next = state.generation.incrementAndGet();
            state.fallbackRevision.incrementAndGet();
            log.warn("[CACHE-MKT] invalidateFamily Redis generation failed family={} "
                    + "(local generation={}, EC-MKT-002 TTL fallback)", family, next);
        }
    }

    /** Durable task execution path: return the shared generation and surface Redis failures. */
    public long invalidateFamilyStrict(Family family) {
        return reconcileGeneration(family, generationStates.get(family), true);
    }

    private long currentGeneration(Family family) {
        GenerationState state = generationStates.get(family);
        try {
            return reconcileGeneration(family, state, false);
        } catch (Exception ex) {
            long local = state.generation.get();
            log.warn("[CACHE-MKT] generation get failed family={} "
                    + "(local generation={}, EC-MKT-002 degrade)", family, local);
            return local;
        }
    }

    private long reconcileGeneration(Family family, GenerationState state, boolean invalidate) {
        long local = state.generation.get();
        long fallbackRevision = state.fallbackRevision.get();
        boolean mustAdvance = invalidate || fallbackRevision > state.reconciledRevision.get();
        Long shared = redis.execute(
                GENERATION_SCRIPT,
                List.of(generationKey(family), generationHighWaterKey(family)),
                Long.toString(local),
                mustAdvance ? "1" : "0");
        if (shared == null || shared < local) {
            throw new IllegalStateException("Redis generation reconciliation returned an invalid value");
        }
        long generation = state.generation.accumulateAndGet(shared, Math::max);
        state.reconciledRevision.accumulateAndGet(fallbackRevision, Math::max);
        return generation;
    }

    private String generationKey(Family family) {
        return GENERATION_KEY_PREFIX + "{" + family.name().toLowerCase(java.util.Locale.ROOT) + "}";
    }

    private String generationHighWaterKey(Family family) {
        return generationKey(family) + ":high-water";
    }

    private String versionedKey(long generation, String key) {
        return "v" + generation + ":" + key;
    }
}
