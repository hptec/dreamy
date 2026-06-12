package com.dreamy.mq;

import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.infra.CatalogCacheService;
import com.dreamy.infra.CatalogCacheService.Family;
import com.dreamy.port.TradingQueryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * EVT-CAT-003：每日 04:00 滚动窗口刷新（30 天窗口自然衰减补偿——无新订单时窗口不滑动的兜底）。
 * 候选集 = trading 近 31 天有售商品 ∪ sales_30d>0 商品；逐一重算回写（RM-CAT-098 覆盖写可重入）。
 */
@Component
public class SalesWindowRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(SalesWindowRefreshJob.class);

    private final TradingQueryPort tradingQueryPort;
    private final ProductRepository productRepository;
    private final CatalogCacheService cache;
    private final TransactionTemplate transactionTemplate;

    public SalesWindowRefreshJob(TradingQueryPort tradingQueryPort, ProductRepository productRepository,
                                 CatalogCacheService cache, TransactionTemplate transactionTemplate) {
        this.tradingQueryPort = tradingQueryPort;
        this.productRepository = productRepository;
        this.cache = cache;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void refreshWindow() {
        LocalDateTime now = LocalDateTime.now();
        Set<Long> candidates = new LinkedHashSet<>();
        candidates.addAll(tradingQueryPort.listPaidProductIds(now.minusDays(31)));
        candidates.addAll(productRepository.listIdsWithSalesPositive());
        if (candidates.isEmpty()) {
            log.info("[EVT-CAT-003] no candidates, window refresh skipped");
            return;
        }
        LocalDateTime windowStart = now.minusDays(30);
        transactionTemplate.executeWithoutResult(tx -> {
            for (Long productId : candidates) {
                int sales = tradingQueryPort.sumPaidQty(productId, windowStart);
                productRepository.updateSales30d(productId, sales, now);
            }
        });
        cache.invalidateFamily(Family.RECO);
        log.info("[EVT-CAT-003] sales window refreshed, products={}", candidates.size());
    }
}
