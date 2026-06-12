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

/**
 * review 域消费端缓存（JetCache 两级 Caffeine+Redis，CACHE-REV-001/002）。
 * - key 模板与 TTL 与 data-flow 缓存矩阵逐行一致：`review:reviews:{product_id}:{sort}:{page}:{page_size}` /
 *   `review:questions:{product_id}:{page}:{page_size}`，TTL 300s；**key 不含 locale**（评价内容不做多语翻译）。
 * - 穿透保护（BE-DIM-8 / cacheNullValue 语义）：空页/商品不存在同样缓存（空 Paginated 快照为正常值缓存，
 *   不存在的 product_id 不反复打穿源库）。
 * - 前缀失效：写路径全部经本组件 put，按 family 维护 key 注册表，invalidateProduct 按
 *   `{product_id}:` 前缀两级同删（等价 L2「remote SCAN+DEL 封装」；多实例残留由 TTL 300s +
 *   CDN s-maxage 60s 自然过期收敛，EC-REV-001 同口径）。
 * - 缓存操作失败不影响主流程（EC-REV-001：记告警，不回滚 DB）。
 */
@Component
public class ReviewCacheService {

    private static final Logger log = LoggerFactory.getLogger(ReviewCacheService.class);

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
    private final Map<Family, Cache<String, Object>> caches = new ConcurrentHashMap<>();
    /** family → 本实例写入过的 key 集合（product 前缀失效用） */
    private final Map<Family, Set<String>> keyRegistry = new ConcurrentHashMap<>();

    public ReviewCacheService(CacheManager cacheManager) {
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

    /** 读缓存（STEP-REV-01 命中即返回） */
    public Object get(Family family, String key) {
        try {
            return caches.get(family).get(key);
        } catch (Exception ex) {
            log.warn("[CACHE-REV] get failed family={} key={} (EC-REV-001 degrade to source)", family, key);
            return null;
        }
    }

    /** 写缓存（族缺省 TTL 300s；空页快照同样缓存——穿透保护） */
    public void put(Family family, String key, Object value) {
        try {
            caches.get(family).put(key, value);
            keyRegistry.get(family).add(key);
        } catch (Exception ex) {
            log.warn("[CACHE-REV] put failed family={} key={} (EC-REV-001)", family, key);
        }
    }

    /** `review:{res}:{product_id}:*` 前缀失效（CACHE-REV 失效触发者列；两级同删） */
    public void invalidateProduct(Family family, Long productId) {
        if (productId == null) {
            return;
        }
        String prefix = productId + ":";
        try {
            Set<String> keys = new HashSet<>();
            for (String key : keyRegistry.get(family)) {
                if (key.startsWith(prefix)) {
                    keys.add(key);
                }
            }
            if (!keys.isEmpty()) {
                caches.get(family).removeAll(keys);
                keyRegistry.get(family).removeAll(keys);
            }
        } catch (Exception ex) {
            log.warn("[CACHE-REV] invalidateProduct failed family={} product={} (EC-REV-001, TTL 兜底)",
                    family, productId);
        }
    }
}
