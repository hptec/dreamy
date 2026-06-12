package com.dreamy.mq;

import com.dreamy.infra.mq.DomainEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * review 域事件发布器（review-data-detail §8.1，本域为纯生产者不自建队列）。
 * - EVT-REV-001 `review.moderated`：审核态变更（E-REV-07 / E-REV-09 action∈{approve,reject} 按
 *   product_id 去重）。payload 最小依赖面 = event_id（信封）+ product_id（catalog EVT-CAT-002
 *   消费者仅取 product_id 经 ReviewQueryPort 回查权威聚合）；review_id/status 为观测冗余字段。
 * - EVT-REV-002 `content.invalidated`：全部前台可见写（E-REV-07/08/09/10/11/12/14/15），
 *   type=review_changed|question_changed；slug 经 ReviewCatalogSnapshotPort 取得，商品已删除则跳过发布
 *   （无 PDP 可失效）。
 * 发布失败不回滚（EC-REV-002：rating 回写靠 EVT-CAT-003 每日补偿收敛，PDP 新鲜度退化为 TTL 级）；
 * 调用方须在事务提交后发布（ReviewAfterCommitRunner，CP-031）。
 */
@Component
public class ReviewEventPublisher {

    public static final String RK_MODERATED = "review.moderated";
    public static final String RK_CONTENT_INVALIDATED = "content.invalidated";

    public static final String TYPE_REVIEW_CHANGED = "review_changed";
    public static final String TYPE_QUESTION_CHANGED = "question_changed";

    public static final List<String> ALL_LOCALES = List.of("en", "es", "fr");

    private final DomainEventPublisher eventPublisher;

    public ReviewEventPublisher(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /** EVT-REV-001 review.moderated（仅审核态变更：approve/reject；精选/回复/图片/可见性不发） */
    public void publishModerated(Long productId, Long reviewId, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product_id", productId);
        if (reviewId != null) {
            payload.put("review_id", reviewId);
        }
        if (status != null) {
            payload.put("status", status);
        }
        payload.put("occurred_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        eventPublisher.publish(RK_MODERATED, payload);
    }

    /** EVT-REV-002 content.invalidated（slug 为 null＝商品已删除 → 跳过发布） */
    public void publishContentInvalidated(String type, String slug, Long productId) {
        if (slug == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("slug", slug);
        payload.put("product_id", productId);
        payload.put("locales", ALL_LOCALES);
        payload.put("occurred_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        eventPublisher.publish(RK_CONTENT_INVALIDATED, payload);
    }
}
