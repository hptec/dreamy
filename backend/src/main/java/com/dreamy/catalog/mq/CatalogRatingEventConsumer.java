package com.dreamy.catalog.mq;

import com.dreamy.catalog.domain.product.entity.Product;
import com.dreamy.catalog.domain.product.repository.ProductRepository;
import com.dreamy.catalog.infra.CatalogCacheService;
import com.dreamy.catalog.infra.CatalogCacheService.Family;
import com.dreamy.catalog.port.ReviewQueryPort;
import com.dreamy.infra.mq.AbstractIdempotentEventConsumer;
import com.dreamy.infra.mq.DomainEvent;
import com.dreamy.infra.mq.EventIdempotencyGuard;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * EVT-CAT-002：q.catalog.rating 消费 review.moderated → 评分冗余字段覆盖写（FLOW-P14）。
 * ① event_id 幂等 ② reviewQueryPort.approvedRatingSummary（进程内直调）
 * ③ RM-CAT-099 覆盖写（TX-CAT-022）④ 失效 catalog:product:{slug}:* + products + reco
 * ⑤ 重试/死信同 EVT-CAT-001。
 */
@Component
public class CatalogRatingEventConsumer extends AbstractIdempotentEventConsumer {

    public static final String QUEUE = "q.catalog.rating";

    private final ReviewQueryPort reviewQueryPort;
    private final ProductRepository productRepository;
    private final CatalogCacheService cache;
    private final TransactionTemplate transactionTemplate;

    public CatalogRatingEventConsumer(EventIdempotencyGuard idempotencyGuard, ReviewQueryPort reviewQueryPort,
                                      ProductRepository productRepository, CatalogCacheService cache,
                                      TransactionTemplate transactionTemplate) {
        super(idempotencyGuard);
        this.reviewQueryPort = reviewQueryPort;
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
        return List.of("review.moderated");
    }

    @Override
    protected void handle(DomainEvent event) {
        Long productId = extractProductId(event.payload());
        if (productId == null) {
            log.warn("[EVT-CAT-002] review.moderated event_id={} without product_id", event.eventId());
            return;
        }
        Product product = productRepository.findById(productId);
        if (product == null) {
            // 商品已删除（订单/评价为快照）——覆盖写无目标，幂等空操作
            log.info("[EVT-CAT-002] product {} absent, skip rating writeback", productId);
            return;
        }
        // ② 聚合 ③ TX-CAT-022 覆盖写
        ReviewQueryPort.RatingSummary summary = reviewQueryPort.approvedRatingSummary(productId);
        transactionTemplate.executeWithoutResult(tx ->
                productRepository.updateRating(productId, summary.avg(), summary.count()));
        // ④ 失效（CACHE-CAT-002/001/004）
        cache.invalidateProductSlug(product.getSlug());
        cache.invalidateFamily(Family.PRODUCTS);
        cache.invalidateFamily(Family.RECO);
        log.info("[EVT-CAT-002] rating refreshed event_id={} product={} avg={} count={}",
                event.eventId(), productId, summary.avg(), summary.count());
    }

    private Long extractProductId(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object pid = payload.get("product_id");
        return pid instanceof Number n ? n.longValue() : null;
    }
}
