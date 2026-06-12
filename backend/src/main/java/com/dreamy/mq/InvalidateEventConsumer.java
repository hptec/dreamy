package com.dreamy.mq;

import com.dreamy.infra.mq.AbstractIdempotentEventConsumer;
import com.dreamy.infra.mq.DomainEvent;
import com.dreamy.infra.mq.EventIdempotencyGuard;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * EVT-MKT-002：q.invalidate 失效消费者（FLOW-P03 落点；TASK-056 消费者侧——catalog + marketing
 * 两域生产者共用，catalog task-allocation shared_with 声明归本域承载）。
 * ① event_id 幂等（AbstractIdempotentEventConsumer 幂等闸，重复投递空操作 ack——TC-MKT-026）
 * ② 按 type 查路径映射表（InvalidatePathMapper）×3 locale（决策 27）
 * ③ POST {NEXT_INTERNAL_URL}/api/revalidate {paths[]}（header x-revalidate-token，仅内网）
 * ④ Cloudflare purge API（zone token 后端配置，按完整 URL 列表）
 * ⑤ 任一步失败抛出 → real 模式 nack → dreamy.retry.q.invalidate TTL 阶梯 ×3 → dreamy.dlq 告警人工重放
 *   （期间 CDN 靠 s-maxage TTL + serve-stale 兜底，决策 22——TC-MKT-027/073）。
 * 队列参数（durable/prefetch=8/DLX）见 application.yml dreamy.mq.queues."[q.invalidate]"。
 */
@Component
public class InvalidateEventConsumer extends AbstractIdempotentEventConsumer {

    public static final String QUEUE = "q.invalidate";

    private final NextRevalidateClient revalidateClient;
    private final CloudflarePurgeClient purgeClient;

    public InvalidateEventConsumer(EventIdempotencyGuard idempotencyGuard,
                                   NextRevalidateClient revalidateClient,
                                   CloudflarePurgeClient purgeClient) {
        super(idempotencyGuard);
        this.revalidateClient = revalidateClient;
        this.purgeClient = purgeClient;
    }

    @Override
    public String queue() {
        return QUEUE;
    }

    @Override
    public List<String> bindingKeys() {
        return List.of("content.invalidated");
    }

    @Override
    protected void handle(DomainEvent event) {
        // payload.type（catalog/marketing/review/trading 生产者均在 payload 内携带 type；routing key 恒 content.invalidated）
        Object typeRaw = event.payload() == null ? null : event.payload().get("type");
        String type = typeRaw == null ? null : String.valueOf(typeRaw);
        // EVT-TRD-005 特判：exchange_rates_updated 自带 purge_paths（API 路径，无 locale 展开），
        // 仅 CDN purge——Next 页面无此缓存面，JetCache 已由 @CacheInvalidate 同步失效（trading-data-detail §6）
        if ("exchange_rates_updated".equals(type)) {
            List<String> purgePaths = extractPurgePaths(event.payload());
            if (purgePaths.isEmpty()) {
                log.warn("[EVT-MKT-002] event_id={} exchange_rates_updated without purge_paths, skipped",
                        event.eventId());
                return;
            }
            purgeClient.purge(purgePaths);
            log.info("[EVT-MKT-002] purged type={} event_id={} paths={}", type, event.eventId(), purgePaths.size());
            return;
        }
        // ② type → 路径映射 ×3 locale
        List<String> paths = InvalidatePathMapper.localizedPaths(type, event.payload());
        if (paths.isEmpty()) {
            log.warn("[EVT-MKT-002] event_id={} unknown/empty type={} skipped (forward-compat no-op)",
                    event.eventId(), type);
            return;
        }
        // ③ Next revalidate → ④ Cloudflare purge（任一失败抛出 → ⑤ nack）
        revalidateClient.revalidate(paths);
        purgeClient.purge(paths);
        log.info("[EVT-MKT-002] invalidated type={} event_id={} paths={}", type, event.eventId(), paths.size());
    }

    private List<String> extractPurgePaths(java.util.Map<String, Object> payload) {
        Object raw = payload == null ? null : payload.get("purge_paths");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
    }
}
