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
import java.util.List;
import java.util.function.Supplier;

/**
 * 税费配置缓存（order-flow-complete F）：JetCache 两级，固定单 key 全量缓存（启用税率规则 / 目的国政策），
 * TTL 600s；后台写操作提交后经缓存任务失效；缓存层异常降级直查 DB（报价不中断）。
 */
@Component
public class TaxCacheService {

    private static final Logger log = LoggerFactory.getLogger(TaxCacheService.class);

    public static final String KEY_RULES = "rules";
    public static final String KEY_POLICIES = "policies";

    private static final Duration TTL = Duration.ofSeconds(600);

    private final CacheManager cacheManager;
    private Cache<String, Object> cache;

    public TaxCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @PostConstruct
    void initCache() {
        QuickConfig qc = QuickConfig.newBuilder("tax:")
                .cacheType(CacheType.BOTH)
                .expire(TTL)
                .localExpire(TTL)
                .localLimit(16)
                .cacheNullValue(false)
                .syncLocal(false)
                .penetrationProtect(true)
                .build();
        this.cache = cacheManager.getOrCreateCache(qc);
    }

    public <T> List<T> getRules(Supplier<List<T>> loader) {
        return getOrLoad(KEY_RULES, loader);
    }

    public <T> List<T> getPolicies(Supplier<List<T>> loader) {
        return getOrLoad(KEY_POLICIES, loader);
    }

    public String invalidateStrict() {
        cache.remove(KEY_RULES);
        cache.remove(KEY_POLICIES);
        return "removed tax:rules,tax:policies";
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getOrLoad(String key, Supplier<List<T>> loader) {
        try {
            Object cached = cache.get(key);
            if (cached instanceof List<?> list) {
                return (List<T>) list;
            }
            List<T> loaded = loader.get();
            cache.put(key, loaded);
            return loaded;
        } catch (Exception ex) {
            log.warn("[CACHE-TAX] get failed key=tax:{} (degrade to source)", key);
            return loader.get();
        }
    }
}
