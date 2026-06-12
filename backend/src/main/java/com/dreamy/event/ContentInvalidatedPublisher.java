package com.dreamy.event;

import com.dreamy.infra.mq.DomainEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * content.invalidated 事件发布（catalog-data-detail §8.1，TASK-056 本域触发侧）。
 * payload：{type: product_created|product_updated|product_status_changed|product_flags_changed|
 * category_changed|tag_changed, slug?, old_slug?, locales:[en,es,fr], occurred_at}；
 * event_id 由 DomainEventPublisher 生成（DomainEvent 信封）。
 * 发布失败不回滚（EC-CAT-002 / TX-CAT-001 备注：MQ 失败 TTL 兜底）；消费者 q.invalidate 归基建侧。
 */
@Component
public class ContentInvalidatedPublisher {

    public static final String ROUTING_KEY = "content.invalidated";
    public static final List<String> ALL_LOCALES = List.of("en", "es", "fr");

    public static final String TYPE_PRODUCT_CREATED = "product_created";
    public static final String TYPE_PRODUCT_UPDATED = "product_updated";
    public static final String TYPE_PRODUCT_STATUS_CHANGED = "product_status_changed";
    public static final String TYPE_PRODUCT_FLAGS_CHANGED = "product_flags_changed";
    public static final String TYPE_CATEGORY_CHANGED = "category_changed";
    public static final String TYPE_TAG_CHANGED = "tag_changed";

    private final DomainEventPublisher eventPublisher;

    public ContentInvalidatedPublisher(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /** 发布失效事件（事务提交后由 CatalogAfterCommitRunner 调用，CP-031） */
    public void publish(String type, String slug, String oldSlug) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        if (slug != null) {
            payload.put("slug", slug);
        }
        if (oldSlug != null && !oldSlug.equals(slug)) {
            payload.put("old_slug", oldSlug);
        }
        payload.put("locales", ALL_LOCALES);
        payload.put("occurred_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        eventPublisher.publish(ROUTING_KEY, payload);
    }

    public void publish(String type) {
        publish(type, null, null);
    }
}
