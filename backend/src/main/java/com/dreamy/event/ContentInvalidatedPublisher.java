package com.dreamy.event;

import com.dreamy.domain.cache.service.AdminCacheService;
import com.dreamy.infra.CdnInvalidationService;
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
 * category_changed|collection_changed, slug?, old_slug?, locales:[en,es,fr], occurred_at}；
 * event_id 由 DomainEventPublisher 生成（DomainEvent 信封）。
 * 发布失败不回滚（EC-CAT-002 / TX-CAT-001 备注：MQ 失败 TTL 兜底）；消费者 q.invalidate 归基建侧。
 *
 * 方案 B 增强：发布事件后记录日志到 cache_invalidation_log 表，供「发布中心」监控页展示。
 * 最新调整：同步调用 CDN API 清除缓存（异步执行，不阻塞主流程）。
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
    public static final String TYPE_COLLECTION_CHANGED = "collection_changed";

    private final DomainEventPublisher eventPublisher;
    private final AdminCacheService cacheService;
    private final CdnInvalidationService cdnService;

    public ContentInvalidatedPublisher(DomainEventPublisher eventPublisher,
                                       AdminCacheService cacheService,
                                       CdnInvalidationService cdnService) {
        this.eventPublisher = eventPublisher;
        this.cacheService = cacheService;
        this.cdnService = cdnService;
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

        // 方案 B：记录失效日志到数据库（失败不影响主流程）
        Long logId = null;
        List<String> affectedPaths = null;
        try {
            String resourceType = extractResourceType(type);
            logId = cacheService.logInvalidation(type, resourceType, null, slug, oldSlug, ALL_LOCALES, "system");
            affectedPaths = buildAffectedPaths(resourceType, slug, oldSlug);
        } catch (Exception e) {
            // 日志记录失败不影响主流程（MQ 已发送）
        }

        // 异步调用 CDN API 清除缓存，并回写日志状态
        if (affectedPaths != null && !affectedPaths.isEmpty()) {
            cdnService.invalidatePaths(affectedPaths, logId);
        } else if (logId != null) {
            // 无路径需清除，直接标记完成
            cacheService.updateLogStatus(logId, 1, null);
        }
    }

    public void publish(String type) {
        publish(type, null, null);
    }

    /** 从事件类型提取资源类型 */
    private String extractResourceType(String eventType) {
        if (eventType.startsWith("product_")) {
            return "product";
        } else if (eventType.startsWith("category_")) {
            return "category";
        } else if (eventType.startsWith("collection_")) {
            return "collection";
        }
        return "unknown";
    }

    /** 构建受影响的路径列表 */
    private List<String> buildAffectedPaths(String resourceType, String slug, String oldSlug) {
        java.util.List<String> paths = new java.util.ArrayList<>();

        if ("product".equals(resourceType) && slug != null) {
            for (String locale : ALL_LOCALES) {
                String prefix = "en".equals(locale) ? "" : "/" + locale;
                paths.add(prefix + "/product/" + slug);
            }
            // 旧 slug 也需要失效
            if (oldSlug != null && !oldSlug.equals(slug)) {
                for (String locale : ALL_LOCALES) {
                    String prefix = "en".equals(locale) ? "" : "/" + locale;
                    paths.add(prefix + "/product/" + oldSlug);
                }
            }
        } else if ("category".equals(resourceType) || "collection".equals(resourceType)) {
            // 分类/集合变更影响列表页
            paths.add("/products");
            paths.add("/es/products");
            paths.add("/fr/products");
        }

        return paths;
    }
}
