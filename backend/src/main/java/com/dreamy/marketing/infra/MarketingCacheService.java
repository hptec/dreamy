package com.dreamy.marketing.infra;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.template.QuickConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * marketing 域消费端缓存（JetCache 两级 Caffeine+Redis，CACHE-MKT-001~009）。
 * - key 模板与 TTL 与 data-flow 缓存矩阵逐行一致；key 含 locale 不含 currency（决策 13 / §8）。
 * - FLASH 族 TTL 60s（倒计时新鲜度兜底，CACHE-MKT-009）。
 * - 穿透保护（BE-DIM-8）：BLOG/WEDDING/LOOKBOOK 详情族 null 标记值短 TTL 60s。
 * - 模式失效：写路径全部经本组件 put，按 family 维护 key 注册表，invalidateFamily 两级同删
 *   （多实例残留由 TTL 60~300s + CDN s-maxage 自然过期收敛，EC-MKT-002 同口径）。
 * - 缓存操作失败不影响主流程（EC-MKT-002：记告警，不回滚 DB）。
 */
@Component
public class MarketingCacheService {

    private static final Logger log = LoggerFactory.getLogger(MarketingCacheService.class);

    /** null 穿透保护标记值（CACHE-MKT-003/005/007） */
    public static final String NULL_MARKER = "__marketing_null__";

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
    private final Map<Family, Cache<String, Object>> caches = new ConcurrentHashMap<>();
    /** family → 本实例写入过的 key 集合（family 级失效用） */
    private final Map<Family, Set<String>> keyRegistry = new ConcurrentHashMap<>();

    public MarketingCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
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
            keyRegistry.put(family, ConcurrentHashMap.newKeySet());
        }
    }

    /** 读缓存；NULL_MARKER 命中由调用方判定（isNullMarker） */
    public Object get(Family family, String key) {
        try {
            return caches.get(family).get(key);
        } catch (Exception ex) {
            log.warn("[CACHE-MKT] get failed family={} key={} (EC-MKT-002 degrade to source)", family, key);
            return null;
        }
    }

    /** 写缓存（族缺省 TTL） */
    public void put(Family family, String key, Object value) {
        try {
            caches.get(family).put(key, value);
            keyRegistry.get(family).add(key);
        } catch (Exception ex) {
            log.warn("[CACHE-MKT] put failed family={} key={} (EC-MKT-002)", family, key);
        }
    }

    /** 写 null 标记（穿透保护，60s 短 TTL——BE-DIM-8） */
    public void putNullMarker(Family family, String key) {
        try {
            caches.get(family).put(key, NULL_MARKER, 60, TimeUnit.SECONDS);
            keyRegistry.get(family).add(key);
        } catch (Exception ex) {
            log.warn("[CACHE-MKT] putNull failed family={} key={} (EC-MKT-002)", family, key);
        }
    }

    public boolean isNullMarker(Object cached) {
        return NULL_MARKER.equals(cached);
    }

    /** 精确 key 失效（两级同删） */
    public void invalidateKeys(Family family, Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        try {
            caches.get(family).removeAll(keys);
            keyRegistry.get(family).removeAll(keys);
        } catch (Exception ex) {
            log.warn("[CACHE-MKT] invalidateKeys failed family={} (EC-MKT-002, TTL 兜底)", family);
        }
    }

    /** family 级失效（`marketing:{family}:*` 语义） */
    public void invalidateFamily(Family family) {
        try {
            Set<String> keys = new HashSet<>(keyRegistry.get(family));
            if (!keys.isEmpty()) {
                caches.get(family).removeAll(keys);
                keyRegistry.get(family).removeAll(keys);
            }
        } catch (Exception ex) {
            log.warn("[CACHE-MKT] invalidateFamily failed family={} (EC-MKT-002, TTL 兜底)", family);
        }
    }

    /** `marketing:blog:{slug}:*` 失效（全 locale + null 标记，CACHE-MKT-003；新旧 slug 由调用方各调一次） */
    public void invalidateBlogSlug(String slug) {
        if (slug == null) {
            return;
        }
        invalidateKeys(Family.BLOG, Set.of(slug + ":en", slug + ":es", slug + ":fr"));
    }
}
