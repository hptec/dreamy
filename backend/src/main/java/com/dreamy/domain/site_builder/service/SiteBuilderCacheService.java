package com.dreamy.domain.site_builder.service;

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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Shared caches for fully assembled storefront site-builder responses. */
@Component
public class SiteBuilderCacheService {

    private static final Logger log = LoggerFactory.getLogger(SiteBuilderCacheService.class);
    private static final String GENERATION_KEY_PREFIX = "site-builder:cache-generation:";
    private static final DefaultRedisScript<Long> GENERATION_SCRIPT = new DefaultRedisScript<>("""
            local generation_key = KEYS[1]
            local high_water_key = KEYS[2]
            local local_generation = tonumber(ARGV[1]) or 0
            local remote_generation = tonumber(redis.call('GET', generation_key))
            local high_water = tonumber(redis.call('GET', high_water_key)) or 0
            local base = math.max(local_generation, high_water)
            if remote_generation ~= nil then
                base = math.max(base, remote_generation)
            end
            local next_generation = base + 1
            if ARGV[2] == '0' and remote_generation ~= nil and remote_generation >= local_generation
                    and remote_generation >= high_water then
                next_generation = remote_generation
            elseif remote_generation == nil then
                local now = redis.call('TIME')
                next_generation = math.max(next_generation, tonumber(now[1]) * 1000000 + tonumber(now[2]))
            end
            local encoded = string.format('%.0f', next_generation)
            redis.call('SET', generation_key, encoded)
            redis.call('SET', high_water_key, encoded)
            return next_generation
            """, Long.class);

    public enum Family {
        HOME("site-builder:home:", Duration.ofSeconds(300)),
        NAVIGATION("site-builder:navigation:", Duration.ofSeconds(600)),
        FOOTER("site-builder:footer:", Duration.ofSeconds(600)),
        ANNOUNCEMENTS("site-builder:announcements:", Duration.ofSeconds(300));

        private final String prefix;
        private final Duration ttl;

        Family(String prefix, Duration ttl) {
            this.prefix = prefix;
            this.ttl = ttl;
        }
    }

    public record Lookup(Family family, String key, long generation, Object value) {}

    private final CacheManager cacheManager;
    private final StringRedisTemplate redis;
    private final Map<Family, Cache<String, Object>> caches = new EnumMap<>(Family.class);
    private final Map<Family, AtomicLong> generations = new EnumMap<>(Family.class);

    public SiteBuilderCacheService(CacheManager cacheManager, StringRedisTemplate redis) {
        this.cacheManager = cacheManager;
        this.redis = redis;
    }

    @PostConstruct
    void initCaches() {
        for (Family family : Family.values()) {
            QuickConfig config = QuickConfig.newBuilder(family.prefix)
                    .cacheType(CacheType.BOTH)
                    .expire(family.ttl)
                    .localExpire(family.ttl)
                    .localLimit(500)
                    .cacheNullValue(false)
                    .syncLocal(false)
                    .build();
            caches.put(family, cacheManager.getOrCreateCache(config));
            generations.put(family, new AtomicLong());
        }
    }

    public Lookup lookup(Family family, String key) {
        long generation = currentGeneration(family);
        try {
            return new Lookup(family, key, generation, caches.get(family).get(versionedKey(generation, key)));
        } catch (Exception ex) {
            log.warn("[CACHE-SITE] get failed family={} key={} (degrade to source)", family, key);
            return new Lookup(family, key, generation, null);
        }
    }

    public void put(Lookup lookup, Object value) {
        try {
            caches.get(lookup.family()).put(versionedKey(lookup.generation(), lookup.key()), value);
        } catch (Exception ex) {
            log.warn("[CACHE-SITE] put failed family={} key={}", lookup.family(), lookup.key());
        }
    }

    /** Durable task path. Redis failure is propagated so task retry and failure records stay truthful. */
    public long invalidateFamilyStrict(Family family) {
        return reconcileGeneration(family, true);
    }

    private long currentGeneration(Family family) {
        try {
            return reconcileGeneration(family, false);
        } catch (Exception ex) {
            long local = generations.get(family).get();
            log.warn("[CACHE-SITE] generation read failed family={} local={} (degrade)", family, local);
            return local;
        }
    }

    private long reconcileGeneration(Family family, boolean invalidate) {
        AtomicLong localState = generations.get(family);
        long local = localState.get();
        Long shared = redis.execute(GENERATION_SCRIPT,
                List.of(generationKey(family), generationKey(family) + ":high-water"),
                Long.toString(local), invalidate ? "1" : "0");
        if (shared == null || shared < local) {
            throw new IllegalStateException("Redis site-builder generation returned an invalid value");
        }
        return localState.accumulateAndGet(shared, Math::max);
    }

    private String generationKey(Family family) {
        return GENERATION_KEY_PREFIX + "{" + family.name().toLowerCase(java.util.Locale.ROOT) + "}";
    }

    private String versionedKey(long generation, String key) {
        return "v" + generation + ":" + key;
    }
}
