package com.dreamy.mq;

import com.dreamy.infra.mq.DomainEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * content.invalidated 事件发布（marketing-data-detail §9.1，TASK-056 本域触发侧）。
 * payload：{type: banner_changed|blog_changed|wedding_changed|lookbook_changed|guide_changed|flash_sale_changed,
 * slug?, old_slug?, id?, locales:[en,es,fr], occurred_at}；event_id 由 DomainEventPublisher 生成（DomainEvent 信封）。
 * coupon 全部写操作与 SCHED 翻转不发（无消费端缓存面，E-MKT-14 STEP-MKT-04 归因）；
 * newsletter/contact 不发（FLOW-P19 显式约定）。发布失败不回滚（EC-MKT-002，TTL 兜底）。
 */
@Component
public class MarketingContentInvalidatedPublisher {

    public static final String ROUTING_KEY = "content.invalidated";
    public static final List<String> ALL_LOCALES = List.of("en", "es", "fr");

    public static final String TYPE_BANNER_CHANGED = "banner_changed";
    public static final String TYPE_BLOG_CHANGED = "blog_changed";
    public static final String TYPE_WEDDING_CHANGED = "wedding_changed";
    public static final String TYPE_LOOKBOOK_CHANGED = "lookbook_changed";
    public static final String TYPE_GUIDE_CHANGED = "guide_changed";
    public static final String TYPE_FLASH_SALE_CHANGED = "flash_sale_changed";

    private final DomainEventPublisher eventPublisher;

    public MarketingContentInvalidatedPublisher(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /** 发布失效事件（事务提交后由 MarketingAfterCommitRunner 调用，CP-031） */
    public void publish(String type, String slug, String oldSlug, Long id) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        if (slug != null) {
            payload.put("slug", slug);
        }
        if (oldSlug != null && !oldSlug.equals(slug)) {
            payload.put("old_slug", oldSlug);
        }
        if (id != null) {
            payload.put("id", id);
        }
        payload.put("locales", ALL_LOCALES);
        payload.put("occurred_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        eventPublisher.publish(ROUTING_KEY, payload);
    }

    public void publish(String type) {
        publish(type, null, null, null);
    }

    /** blog 专用（slug 维度，old_slug 给定时旧路径一并失效——E-MKT-29 STEP-MKT-06） */
    public void publishBlog(String slug, String oldSlug) {
        publish(TYPE_BLOG_CHANGED, slug, oldSlug, null);
    }

    /** wedding 专用（id 维度——EVT-MKT 路径映射 /real-weddings/{id}） */
    public void publishWedding(Long id) {
        publish(TYPE_WEDDING_CHANGED, null, null, id);
    }
}
