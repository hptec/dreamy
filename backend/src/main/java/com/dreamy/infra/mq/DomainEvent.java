package com.dreamy.infra.mq;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 领域事件信封（data-flow.md：Svc ⇄ MQ「领域事件 JSON（event_id + type + payload），消费侧按 event_id 幂等」）。
 * JSON 字段 snake_case（CP-001）；event_id 为 UUID（发布器生成），消费幂等键。
 */
public record DomainEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("type") String type,
        @JsonProperty("occurred_at") String occurredAt,
        @JsonProperty("payload") Map<String, Object> payload
) {
}
