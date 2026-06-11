package com.dreamy.infra.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * RabbitMQ 发布器（dreamy.mq.mode=real）：dreamy.events topic exchange，JSON 信封
 * {event_id, type, occurred_at, payload}，persistent 投递。
 * publish 失败不抛（error-strategy 降级矩阵：本地事务不回滚；失效链靠 JetCache 已失效 + CDN TTL
 * 兜底；邮件/回写类记录告警日志补偿）。
 */
@Component
@ConditionalOnProperty(name = "dreamy.mq.mode", havingValue = "real")
public class RabbitDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitDomainEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final MqProperties props;

    public RabbitDomainEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
                                      MqProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    public String publish(String routingKey, Object payload) {
        String eventId = UUID.randomUUID().toString();
        try {
            DomainEvent event = new DomainEvent(eventId, routingKey,
                    OffsetDateTime.now(ZoneOffset.UTC).toString(), toMap(payload));
            byte[] body = objectMapper.writeValueAsBytes(event);
            MessageProperties mp = new MessageProperties();
            mp.setMessageId(eventId);
            mp.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            mp.setContentEncoding(StandardCharsets.UTF_8.name());
            mp.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            rabbitTemplate.send(props.getExchange(), routingKey, new Message(body, mp));
            log.info("[MQ] publish key={} event_id={}", routingKey, eventId);
        } catch (Exception ex) {
            // 告警日志补偿，本地事务不回滚（缓存新鲜度退化为 TTL 级，功能不损）
            log.error("[MQ] publish failed key={} event_id={} (local tx NOT rolled back)",
                    routingKey, eventId, ex);
        }
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
