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
import java.util.concurrent.atomic.AtomicLong;

/**
 * review 域消费端缓存（JetCache 两级 Caffeine+Redis，CACHE-REV-001/002）。
 * - key 模板与 TTL 与 data-flow 缓存矩阵逐行一致：`review:reviews:{product_id}:{sort}:{page}:{page_size}` /
 *   `review:questions:{product_id}:{page}:{page_size}`，TTL 300s；**key 不含 locale**（评价内容不做多语翻译）。
 * - 穿透保护（BE-DIM-8 / cacheNullValue 语义）：空页/商品不存在同样缓存（空 Paginated 快照为正常值缓存，
 *   不存在的 product_id 不反复打穿源库）。
 * - 失效：family 使用 Redis 共享代际号；写入提交后所有实例切换 namespace，旧代际 TTL 回收。
 * - 缓存操作失败不影响主流程（EC-REV-001：记告警，不回滚 DB）。
 */
@Component
public class ReviewCacheService {

    private static final Logger log = LoggerFactory.getLogger(ReviewCacheService.class);
    private static final String GENERATION_KEY_PREFIX = "review:cache-generation:";
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

    /** 缓存族（key 前缀=族名，CACHE-REV-001/002） */
    public enum Family {
        REVIEWS("review:reviews:", Duration.ofSeconds(300)),
        QUESTIONS("review:questions:", Duration.ofSeconds(300));

        final String prefix;
        final Duration ttl;

        Family(String prefix, Duration ttl) {
            this.prefix = prefix;
            this.ttl = ttl;
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

    public ReviewCacheService(CacheManager cacheManager, StringRedisTemplate redis) {
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

    public record Lookup(Family family, String key, long generation, Object value) {
    }

    /** 读缓存（STEP-REV-01 命中即返回） */
    public Lookup lookup(Family family, String key) {
        long generation = currentGeneration(family);
        try {
            return new Lookup(family, key, generation,
                    caches.get(family).get(versionedKey(generation, key)));
        } catch (Exception ex) {
            log.warn("[CACHE-REV] get failed family={} key={} (EC-REV-001 degrade to source)", family, key);
            return new Lookup(family, key, generation, null);
        }
    }

    /** 写缓存（族缺省 TTL 300s；空页快照同样缓存——穿透保护） */
    public void put(Lookup lookup, Object value) {
        try {
            caches.get(lookup.family()).put(versionedKey(lookup.generation(), lookup.key()), value);
        } catch (Exception ex) {
            log.warn("[CACHE-REV] put failed family={} key={} (EC-REV-001)", lookup.family(), lookup.key());
        }
    }

    /** 写后切换整个资源族代际；评价/问答族独立，保证跨实例严格失效。 */
    public void invalidateProduct(Family family, Long productId) {
        if (productId == null) {
            return;
        }
        GenerationState state = generationStates.get(family);
        try {
            reconcileGeneration(family, state, true);
        } catch (Exception ex) {
            long next = state.generation.incrementAndGet();
            state.fallbackRevision.incrementAndGet();
            log.warn("[CACHE-REV] invalidateProduct Redis generation failed family={} product={} "
                    + "(local generation={}, EC-REV-001 TTL fallback)", family, productId, next);
        }
    }

    private long currentGeneration(Family family) {
        GenerationState state = generationStates.get(family);
        try {
            return reconcileGeneration(family, state, false);
        } catch (Exception ex) {
            long local = state.generation.get();
            log.warn("[CACHE-REV] generation get failed family={} "
                    + "(local generation={}, EC-REV-001 degrade)", family, local);
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
