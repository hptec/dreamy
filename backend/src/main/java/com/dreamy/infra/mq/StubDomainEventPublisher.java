package com.dreamy.infra.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * stub 发布器（dev 缺省，dreamy.mq.mode=stub）：不连 RabbitMQ，按 bindingKeys topic 匹配
 * 同步直调进程内订阅者（dev 与 real 行为对齐：邮件/回写/失效消费者在本地同样生效），
 * 消费异常仅告警不重试（重试阶梯/死信为 real 模式 broker 语义）。
 * 保证 bootRun 在无 RabbitMQ 环境零外部依赖可启动。
 */
@Component
@ConditionalOnProperty(name = "dreamy.mq.mode", havingValue = "stub", matchIfMissing = true)
public class StubDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(StubDomainEventPublisher.class);

    private final ObjectMapper objectMapper;
    private final ObjectProvider<DomainEventSubscriber> subscribers;

    public StubDomainEventPublisher(ObjectMapper objectMapper,
                                    ObjectProvider<DomainEventSubscriber> subscribers) {
        this.objectMapper = objectMapper;
        this.subscribers = subscribers;
    }

    @Override
    public String publish(String routingKey, Object payload) {
        String eventId = UUID.randomUUID().toString();
        DomainEvent event = new DomainEvent(eventId, routingKey,
                OffsetDateTime.now(ZoneOffset.UTC).toString(), toMap(payload));
        log.info("[MQ-STUB] publish key={} event_id={}", routingKey, eventId);
        subscribers.stream().forEach(subscriber -> {
            boolean matched = subscriber.bindingKeys().stream()
                    .anyMatch(binding -> TopicPatternMatcher.matches(binding, routingKey));
            if (!matched) {
                return;
            }
            try {
                subscriber.onEvent(event);
            } catch (RuntimeException ex) {
                // stub 模式无重试阶梯：仅告警（real 模式由监听器走 dreamy.retry.* → dreamy.dlq）
                log.error("[MQ-STUB] consumer queue={} failed event_id={} key={}",
                        subscriber.queue(), eventId, routingKey, ex);
            }
        });
        return eventId;
    }

    private Map<String, Object> toMap(Object payload) {
        if (payload == null) {
            return Map.of();
        }
        return objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {
        });
    }
}
