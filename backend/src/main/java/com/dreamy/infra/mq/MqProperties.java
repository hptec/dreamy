package com.dreamy.infra.mq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RabbitMQ 事件拓扑配置（data-flow.md「MQ 事件拓扑」，BE-DIM-4）。
 * - topic exchange `dreamy.events`；5 队列 q.mail / q.showroom / q.catalog.sales / q.catalog.rating / q.invalidate；
 * - 重试经 `dreamy.retry.{queue}`（per-message TTL 阶梯 + DLX 回投主队列）×3 → 超限路由
 *   DLX `dreamy.dlx` → `dreamy.dlq`（告警 + 人工重放）；
 * - 阶梯缺省 5s/30s/180s（showroom data-detail §8.2 口径），catalog 两队列 1s/4s/16s（catalog data-detail §8）；
 * - mode=stub（dev 缺省）：发布器同步直调进程内订阅者，bootRun 不因缺 RabbitMQ 失败。
 */
@Data
@Component
@ConfigurationProperties(prefix = "dreamy.mq")
public class MqProperties {

    /** stub | real */
    private String mode = "stub";

    /** 领域事件 topic exchange */
    private String exchange = "dreamy.events";

    /** 死信 exchange（dreamy.dlx → dreamy.dlq） */
    private String deadLetterExchange = "dreamy.dlx";

    /** 死信队列（告警 + 人工重放；邮件类同步标记 MailRecord=dead） */
    private String deadLetterQueue = "dreamy.dlq";

    /** 重试队列命名前缀（dreamy.retry.{queue}） */
    private String retryQueuePrefix = "dreamy.retry.";

    /** nack 重试上限（×3 → 死信） */
    private int maxRetries = 3;

    /** 缺省重试 TTL 阶梯（毫秒） */
    private List<Long> defaultRetryTtlMs = new ArrayList<>(List.of(5_000L, 30_000L, 180_000L));

    /** 队列定义：队列名（yml 中以 "[q.mail]" 形式键入）→ 绑定与重试参数 */
    private Map<String, QueueSpec> queues = new LinkedHashMap<>();

    @Data
    public static class QueueSpec {
        /** topic binding keys（如 order.*、review.moderated） */
        private List<String> bindingKeys = new ArrayList<>();
        /** 本队列重试 TTL 阶梯（缺省取 default-retry-ttl-ms） */
        private List<Long> retryTtlMs;
        /** 消费 prefetch（七域设计统一 8） */
        private int prefetch = 8;
    }

    public String retryQueueName(String queue) {
        return retryQueuePrefix + queue;
    }

    /** 取队列重试阶梯；attempt 超出阶梯长度时取末档 */
    public long retryTtlMs(String queue, int attempt) {
        QueueSpec spec = queues.get(queue);
        List<Long> ladder = (spec != null && spec.getRetryTtlMs() != null && !spec.getRetryTtlMs().isEmpty())
                ? spec.getRetryTtlMs() : defaultRetryTtlMs;
        if (ladder.isEmpty()) {
            return 5_000L;
        }
        int idx = Math.min(Math.max(attempt, 0), ladder.size() - 1);
        return ladder.get(idx);
    }

    public int prefetch(String queue) {
        QueueSpec spec = queues.get(queue);
        return spec == null ? 8 : spec.getPrefetch();
    }
}
