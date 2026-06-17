package com.dreamy.infra;

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
 * catalog 域消费端缓存（JetCache 两级 Caffeine+Redis，CACHE-CAT-001~006）。
 * - key 模板与 TTL 与 data-flow 缓存矩阵逐行一致；key 含 locale 不含 currency（决策 13/14）。
 * - 穿透保护（BE-DIM-8）：PRODUCT 族 null 标记值短 TTL 60s（CACHE-CAT-002）。
 * - 模式失效：写路径全部经本组件 put，故按 family 维护 key 注册表，
 *   invalidateFamily 经 Cache.removeAll 两级同删（等价 L2「remote SCAN+DEL 封装 / 本地 invalidateAll by prefix」；
 *   多实例残留由 TTL 60~600s + CDN s-maxage 自然过期收敛，EC-CAT-002 同口径）。
 * - 缓存操作失败不影响主流程（EC-CAT-002：记告警，不回滚 DB）。
 */
@Component
public class CatalogCacheService {

    private static final Logger log = LoggerFactory.getLogger(CatalogCacheService.class);

    /** null 穿透保护标记值（CACHE-CAT-002） */
    public static final String NULL_MARKER = "__catalog_null__";

    /** 缓存族（key 前缀=族名，CACHE-CAT-001~006） */
    public enum Family {
        PRODUCTS("catalog:products:", Duration.ofSeconds(300)),
        PRODUCT("catalog:product:", Duration.ofSeconds(300)),
        SEARCH("catalog:search:", Duration.ofSeconds(60)),
        RECO("catalog:reco:", Duration.ofSeconds(300)),
        CATEGORIES("catalog:categories:", Duration.ofSeconds(600)),
        COLLECTIONS("catalog:collections:", Duration.ofSeconds(600));

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

    public CatalogCacheService(CacheManager cacheManager) {
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

    /** 读缓存；NULL_MARKER 命中由调用方判定（nullHit） */
    public Object get(Family family, String key) {
        try {
            return caches.get(family).get(key);
        } catch (Exception ex) {
            log.warn("[CACHE-CAT] get failed family={} key={} (EC-CAT-002 degrade to source)", family, key);
            return null;
        }
    }

    /** 写缓存（族缺省 TTL） */
    public void put(Family family, String key, Object value) {
        try {
            caches.get(family).put(key, value);
            keyRegistry.get(family).add(key);
        } catch (Exception ex) {
            log.warn("[CACHE-CAT] put failed family={} key={} (EC-CAT-002)", family, key);
        }
    }

    /** 写 null 标记（穿透保护，60s 短 TTL——CACHE-CAT-002 / BE-DIM-8） */
    public void putNullMarker(Family family, String key) {
        try {
            caches.get(family).put(key, NULL_MARKER, 60, TimeUnit.SECONDS);
            keyRegistry.get(family).add(key);
        } catch (Exception ex) {
            log.warn("[CACHE-CAT] putNull failed family={} key={} (EC-CAT-002)", family, key);
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
            log.warn("[CACHE-CAT] invalidateKeys failed family={} (EC-CAT-002, TTL 兜底)", family);
        }
    }

    /** family 级失效（`catalog:{family}:*` 语义） */
    public void invalidateFamily(Family family) {
        try {
            Set<String> keys = new HashSet<>(keyRegistry.get(family));
            if (!keys.isEmpty()) {
                caches.get(family).removeAll(keys);
                keyRegistry.get(family).removeAll(keys);
            }
        } catch (Exception ex) {
            log.warn("[CACHE-CAT] invalidateFamily failed family={} (EC-CAT-002, TTL 兜底)", family);
        }
    }

    /** `catalog:product:{slug}:*` 失效（全 locale + null 标记，CACHE-CAT-002；新旧 slug 由调用方各调一次） */
    public void invalidateProductSlug(String slug) {
        if (slug == null) {
            return;
        }
        invalidateKeys(Family.PRODUCT, Set.of(slug + ":en", slug + ":es", slug + ":fr"));
    }
}
