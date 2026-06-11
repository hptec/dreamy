package com.dreamy.shipping.infra;

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
 * shipping 域报价缓存（CACHE-SHP-001/002，BE-DIM-8）。
 * - JetCache 两级（Caffeine+Redis），固定单 key 全量缓存，TTL 600s；
 *   key 恒存在（空表缓存空列表），无穿透面、无 locale/currency 维度（USD 基准配置）。
 * - 消费场景唯一：SVC-SHP-01 报价直调（FLOW-P05）；后台两个列表端点直读 DB 不经缓存。
 * - 失效触发者：本域写操作事务提交后失效（进程内，无 MQ、无 CDN）。
 * - 缓存层异常降级直查 DB（TC-SHP-040：报价不中断；BE-DIM-5）。
 */
@Component
public class ShippingCacheService {

    private static final Logger log = LoggerFactory.getLogger(ShippingCacheService.class);

    /** CACHE-SHP-001 */
    public static final String KEY_CARRIERS = "carriers";
    /** CACHE-SHP-002 */
    public static final String KEY_RATES = "rates";

    private static final Duration TTL = Duration.ofSeconds(600);

    private final CacheManager cacheManager;
    private Cache<String, Object> cache;

    public ShippingCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @PostConstruct
    void initCache() {
        QuickConfig qc = QuickConfig.newBuilder("shipping:")
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

    /** 读取 shipping:carriers（未命中回源 + 回填 TTL 600s；缓存异常降级直查 loader） */
    @SuppressWarnings("unchecked")
    public <T> List<T> getCarriers(Supplier<List<T>> loader) {
        return getOrLoad(KEY_CARRIERS, loader);
    }

    /** 读取 shipping:rates */
    @SuppressWarnings("unchecked")
    public <T> List<T> getRates(Supplier<List<T>> loader) {
        return getOrLoad(KEY_RATES, loader);
    }

    /** E-SHP-02~05 事务提交后失效 shipping:carriers */
    public void invalidateCarriers() {
        invalidate(KEY_CARRIERS);
    }

    /** E-SHP-07~09 事务提交后失效 shipping:rates */
    public void invalidateRates() {
        invalidate(KEY_RATES);
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
            // EC：缓存基础设施异常 → 降级直查 DB（运费是结算强依赖，DB 异常仍向上抛 50001）
            log.warn("[CACHE-SHP] get failed key=shipping:{} (degrade to source)", key);
            return loader.get();
        }
    }

    private void invalidate(String key) {
        try {
            cache.remove(key);
        } catch (Exception ex) {
            log.warn("[CACHE-SHP] invalidate failed key=shipping:{} (TTL 600s 兜底)", key);
        }
    }
}
