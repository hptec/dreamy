package com.dreamy.infra.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.context.SmartLifecycle;

import java.util.ArrayList;
import java.util.List;

/**
 * real 模式消费者装配（每个 DomainEventSubscriber 一个监听容器）：
 * durable 队列、manual ack、prefetch 按配置（七域设计统一 8）。
 * nack 重试约定（BE-DIM-4 / error-strategy 降级矩阵）：
 * - 消费异常且尝试次数 < maxRetries：原消息按队列 TTL 阶梯（x-dreamy-retry 头计数）改投
 *   `dreamy.retry.{queue}`（per-message expiration，到期 DLX 回投主队列）后 ack；
 * - 超限：basicReject(requeue=false) → 队列 DLX dreamy.dlx → dreamy.dlq（告警 + 人工重放）。
 */
public class RabbitEventConsumerBootstrap implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RabbitEventConsumerBootstrap.class);
    private static final String RETRY_HEADER = "x-dreamy-retry";

    private final ConnectionFactory connectionFactory;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final MqProperties props;
    private final List<DomainEventSubscriber> subscribers;
    private final List<SimpleMessageListenerContainer> containers = new ArrayList<>();
    private volatile boolean running;

    public RabbitEventConsumerBootstrap(ConnectionFactory connectionFactory,
                                        RabbitTemplate rabbitTemplate,
                                        ObjectMapper objectMapper,
                                        MqProperties props,
                                        List<DomainEventSubscriber> subscribers) {
        this.connectionFactory = connectionFactory;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.props = props;
        this.subscribers = subscribers;
    }

    @Override
    public void start() {
        for (DomainEventSubscriber subscriber : subscribers) {
            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
            container.setQueueNames(subscriber.queue());
            container.setPrefetchCount(props.prefetch(subscriber.queue()));
            container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
            container.setMessageListener((ChannelAwareMessageListener) (message, channel) -> {
                long deliveryTag = message.getMessageProperties().getDeliveryTag();
                try {
                    DomainEvent event = objectMapper.readValue(message.getBody(), DomainEvent.class);
                    subscriber.onEvent(event);
                    channel.basicAck(deliveryTag, false);
                } catch (Exception ex) {
                    int attempt = currentAttempt(message.getMessageProperties());
                    if (attempt < props.getMaxRetries()) {
                        long ttl = props.retryTtlMs(subscriber.queue(), attempt);
                        requeueWithDelay(subscriber.queue(), message, attempt + 1, ttl);
                        channel.basicAck(deliveryTag, false);
                        log.warn("[MQ] queue={} consume failed, retry {}/{} after {}ms message_id={}",
                                subscriber.queue(), attempt + 1, props.getMaxRetries(), ttl,
                                message.getMessageProperties().getMessageId(), ex);
                    } else {
                        // 超限 → 队列 DLX（dreamy.dlx）→ dreamy.dlq
                        channel.basicReject(deliveryTag, false);
                        log.error("[MQ] queue={} consume failed after {} retries, routed to {} message_id={}",
                                subscriber.queue(), props.getMaxRetries(), props.getDeadLetterQueue(),
                                message.getMessageProperties().getMessageId(), ex);
                    }
                }
            });
            container.afterPropertiesSet();
            container.start();
            containers.add(container);
            log.info("[MQ] listener started queue={} prefetch={}", subscriber.queue(),
                    props.prefetch(subscriber.queue()));
        }
        running = true;
    }

    private int currentAttempt(MessageProperties mp) {
        Object header = mp.getHeaders().get(RETRY_HEADER);
        if (header instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private void requeueWithDelay(String queue, Message original, int nextAttempt, long ttlMs) {
        MessageProperties mp = original.getMessageProperties();
        mp.setHeader(RETRY_HEADER, nextAttempt);
        mp.setExpiration(String.valueOf(ttlMs));
        rabbitTemplate.send("", props.retryQueueName(queue), new Message(original.getBody(), mp));
    }

    @Override
    public void stop() {
        containers.forEach(SimpleMessageListenerContainer::stop);
        containers.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
