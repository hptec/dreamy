package com.dreamy.mq;

import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.infra.CatalogCacheService;
import com.dreamy.infra.CatalogCacheService.Family;
import com.dreamy.port.TradingQueryPort;
import com.dreamy.infra.mq.AbstractIdempotentEventConsumer;
import com.dreamy.infra.mq.DomainEvent;
import com.dreamy.infra.mq.EventIdempotencyGuard;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EVT-CAT-001：q.catalog.sales 消费 order.paid → 30 天滚动销量重算回写（决策 29 best_sellers 数据源）。
 * ① event_id 幂等（AbstractIdempotentEventConsumer 幂等闸——error-strategy L2 要求 3 本域落点）
 * ② payload.lines[].product_id 去重 ③ tradingQueryPort.sumPaidQty 重算（进程内直调，决策 3）
 * ④ RM-CAT-098 覆盖写（TX-CAT-021 单事务，可重入）⑤ 失效 catalog:reco:*
 * ⑥ 失败抛出 → real 模式重试 ×3（1s/4s/16s）→ dreamy.dlq（队列参数见 application.yml dreamy.mq.queues）。
 */
@Component
public class CatalogSalesEventConsumer extends AbstractIdempotentEventConsumer {

    public static final String QUEUE = "q.catalog.sales";

    private final TradingQueryPort tradingQueryPort;
    private final ProductRepository productRepository;
    private final CatalogCacheService cache;
    private final TransactionTemplate transactionTemplate;

    public CatalogSalesEventConsumer(EventIdempotencyGuard idempotencyGuard, TradingQueryPort tradingQueryPort,
                                     ProductRepository productRepository, CatalogCacheService cache,
                                     TransactionTemplate transactionTemplate) {
        super(idempotencyGuard);
        this.tradingQueryPort = tradingQueryPort;
        this.productRepository = productRepository;
        this.cache = cache;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public String queue() {
        return QUEUE;
    }

    @Override
    public List<String> bindingKeys() {
        return List.of("order.paid");
    }

    @Override
    protected void handle(DomainEvent event) {
        // ② lines[].product_id 去重
        Set<Long> productIds = extractProductIds(event.payload());
        if (productIds.isEmpty()) {
            log.warn("[EVT-CAT-001] order.paid event_id={} without product lines", event.eventId());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusDays(30);
        // ③④ TX-CAT-021：消费者内单事务逐一覆盖写（天然可重入）
        transactionTemplate.executeWithoutResult(tx -> {
            for (Long productId : productIds) {
                int sales = tradingQueryPort.sumPaidQty(productId, windowStart);
                productRepository.updateSales30d(productId, sales, now);
            }
        });
        // ⑤ 失效 catalog:reco:*（CACHE-CAT-004 失效触发者：order.paid 销量回写）
        cache.invalidateFamily(Family.RECO);
        log.info("[EVT-CAT-001] sales_30d refreshed event_id={} products={}", event.eventId(), productIds);
    }

    @SuppressWarnings("unchecked")
    private Set<Long> extractProductIds(Map<String, Object> payload) {
        Set<Long> ids = new LinkedHashSet<>();
        if (payload == null) {
            return ids;
        }
        Object lines = payload.get("lines");
        if (lines instanceof List<?> list) {
            for (Object line : list) {
                if (line instanceof Map<?, ?> map) {
                    Object pid = ((Map<String, Object>) map).get("product_id");
                    if (pid instanceof Number n) {
                        ids.add(n.longValue());
                    }
                }
            }
        }
        return ids;
    }
}
