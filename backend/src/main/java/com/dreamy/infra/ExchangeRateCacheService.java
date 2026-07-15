package com.dreamy.infra;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.template.QuickConfig;
import com.dreamy.dto.TradingDtos.StoreExchangeRateDto;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * 汇率消费端缓存（CACHE-TRD-001：JetCache 两级 Caffeine+Redis，key `trading:exchange-rates`，
 * TTL 600s；updateAdminExchangeRate 写入后由持久化缓存任务清理，执行结果可在后台追踪。
 * 穿透保护：固定 5 行种子数据无穿透面（trading-data-detail §8）。
 * 缓存操作失败不影响主流程（TX-TRD-011 EC：失效失败不回滚 DB，TTL 600s 兜底收敛）。
 */
@Component
public class ExchangeRateCacheService {

    public static final String KEY = "trading:exchange-rates";

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateCacheService.class);
    private static final Duration TTL = Duration.ofSeconds(600);

    private final CacheManager cacheManager;
    private Cache<String, List<StoreExchangeRateDto>> cache;

    public ExchangeRateCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @PostConstruct
    void init() {
        QuickConfig qc = QuickConfig.newBuilder(KEY)
                .cacheType(CacheType.BOTH)
                .expire(TTL)
                .localExpire(TTL)
                .localLimit(16)
                .cacheNullValue(false)
                .syncLocal(false)
                .build();
        cache = cacheManager.getOrCreateCache(qc);
    }

    /** 读穿（未命中 → loader 回源回填） */
    public List<StoreExchangeRateDto> getOrLoad(Supplier<List<StoreExchangeRateDto>> loader) {
        try {
            List<StoreExchangeRateDto> cached = cache.get(KEY);
            if (cached != null) {
                return cached;
            }
        } catch (Exception ex) {
            log.warn("[CACHE-TRD-001] read failed, fallback to DB", ex);
        }
        List<StoreExchangeRateDto> loaded = loader.get();
        try {
            cache.put(KEY, loaded);
        } catch (Exception ex) {
            log.warn("[CACHE-TRD-001] put failed (TTL 兜底)", ex);
        }
        return loaded;
    }

    public String invalidateStrict() {
        cache.remove(KEY);
        return "removed " + KEY;
    }
}
