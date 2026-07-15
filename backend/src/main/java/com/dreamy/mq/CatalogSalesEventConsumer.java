package com.dreamy.mq;

import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.domain.cache.service.CacheInvalidationPlans;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.port.TradingQueryPort;
import com.dreamy.infra.mq.AbstractIdempotentEventConsumer;
import com.dreamy.infra.mq.DomainEvent;
import com.dreamy.infra.mq.EventIdempotencyGuard;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.Clock;
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
    private final TransactionTemplate transactionTemplate;
    private final CacheInvalidationTaskService cacheTasks;
    private final Clock clock;

    public CatalogSalesEventConsumer(EventIdempotencyGuard idempotencyGuard, TradingQueryPort tradingQueryPort,
                                     ProductRepository productRepository, TransactionTemplate transactionTemplate,
                                     CacheInvalidationTaskService cacheTasks, Clock clock) {
        super(idempotencyGuard);
        this.tradingQueryPort = tradingQueryPort;
        this.productRepository = productRepository;
        this.transactionTemplate = transactionTemplate;
        this.cacheTasks = cacheTasks;
        this.clock = clock;
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
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime windowStart = now.minusDays(30);
        // ③④ TX-CAT-021：消费者内单事务逐一覆盖写（天然可重入）
        transactionTemplate.executeWithoutResult(tx -> {
            for (Long productId : productIds) {
                int sales = tradingQueryPort.sumPaidQty(productId, windowStart);
                productRepository.updateSales30d(productId, sales, now);
            }
            cacheTasks.enqueue(CacheInvalidationTaskService.MODE_SYSTEM_EVENT, "catalog.sales.refresh",
                    "product_batch", event.eventId(), "销量回写", CacheInvalidationPlans.PRODUCT_SALES,
                    null, Map.of("product_ids", productIds), "event:order.paid");
        });
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
