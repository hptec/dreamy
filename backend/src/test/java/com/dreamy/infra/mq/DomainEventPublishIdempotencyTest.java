package com.dreamy.infra.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 领域事件发布/消费幂等单元测试（data-flow「MQ 事件拓扑」消费幂等规范 + BE-DIM-4）：
 * event_id UUID 信封、topic 匹配同步分发（stub 模式）、AbstractIdempotentEventConsumer
 * event_id 去重 + 失败释放允许重放（TC-SHR-054 口径）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DomainEventPublishIdempotencyTest {

    /** 内存幂等闸（Redis SETNX 语义等价） */
    static class InMemoryGuard implements EventIdempotencyGuard {
        final Set<String> acquired = ConcurrentHashMap.newKeySet();

        @Override
        public boolean tryAcquire(String queue, String eventId) {
            return acquired.add(queue + ":" + eventId);
        }

        @Override
        public void release(String queue, String eventId) {
            acquired.remove(queue + ":" + eventId);
        }
    }

    /** q.mail 形态的测试消费者（order.* / showroom.* / refund.resolved 绑定） */
    static class RecordingConsumer extends AbstractIdempotentEventConsumer {
        final List<DomainEvent> handled = new ArrayList<>();
        final AtomicInteger failuresToThrow = new AtomicInteger();

        RecordingConsumer(EventIdempotencyGuard guard) {
            super(guard);
        }

        @Override
        public String queue() {
            return "q.mail";
        }

        @Override
        public List<String> bindingKeys() {
            return List.of("order.*", "showroom.*", "refund.resolved");
        }

        @Override
        protected void handle(DomainEvent event) {
            if (failuresToThrow.getAndDecrement() > 0) {
                throw new IllegalStateException("transient failure");
            }
            handled.add(event);
        }
    }

    @Mock
    private ObjectProvider<DomainEventSubscriber> subscribers;

    private InMemoryGuard guard;
    private RecordingConsumer consumer;
    private StubDomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        guard = new InMemoryGuard();
        consumer = new RecordingConsumer(guard);
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        when(subscribers.stream()).thenAnswer(inv -> java.util.stream.Stream.of(
                (DomainEventSubscriber) consumer));
        publisher = new StubDomainEventPublisher(mapper, subscribers);
    }

    @Test
    @DisplayName("发布生成 event_id（UUID）+ snake_case payload 信封，topic 匹配同步直调消费者")
    void publishDispatchesWithEnvelope() {
        record OrderPaid(String orderNo, long customerId) {
        }
        String eventId = publisher.publish("order.paid", new OrderPaid("DR20260610001", 9L));

        assertThat(eventId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(consumer.handled).hasSize(1);
        DomainEvent event = consumer.handled.get(0);
        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.type()).isEqualTo("order.paid");
        assertThat(event.occurredAt()).isNotBlank();
        assertThat(event.payload()).containsEntry("order_no", "DR20260610001"); // CP-001 snake_case
    }

    @Test
    @DisplayName("binding key 不匹配的事件不分发（content.invalidated 不进 q.mail）")
    void unmatchedRoutingKeyNotDispatched() {
        publisher.publish("content.invalidated", Map.of("slug", "a"));
        assertThat(consumer.handled).isEmpty();
    }

    @Test
    @DisplayName("同 event_id 重复投递 → 幂等空操作（仅消费一次）")
    void duplicateEventIdConsumedOnce() {
        DomainEvent event = new DomainEvent("evt-fixed-1", "order.paid", "2026-06-10T00:00:00Z", Map.of());
        consumer.onEvent(event);
        consumer.onEvent(event);
        consumer.onEvent(event);
        assertThat(consumer.handled).hasSize(1);
    }

    @Test
    @DisplayName("消费失败 → 释放幂等键 + 异常上抛（nack 重试约定）；重放可成功")
    void failureReleasesGuardAllowingReplay() {
        DomainEvent event = new DomainEvent("evt-fixed-2", "showroom.remind", "2026-06-10T00:00:00Z", Map.of());
        consumer.failuresToThrow.set(1);
        assertThatThrownBy(() -> consumer.onEvent(event)).isInstanceOf(IllegalStateException.class);
        assertThat(guard.acquired).isEmpty(); // SETNX 键已释放（TC-SHR-054）
        consumer.onEvent(event); // 重投重入
        assertThat(consumer.handled).hasSize(1);
    }

    @Test
    @DisplayName("stub 模式消费异常不中断发布方（告警吞掉，本地事务不回滚语义）")
    void consumerFailureDoesNotPropagateToPublisher() {
        consumer.failuresToThrow.set(99);
        String eventId = publisher.publish("order.paid", Map.of());
        assertThat(eventId).isNotBlank(); // publish 正常返回
    }

    @Test
    @DisplayName("AMQP topic 匹配：* 恰一词 / # 任意段 / 精确键")
    void topicPatternMatching() {
        assertThat(TopicPatternMatcher.matches("order.*", "order.paid")).isTrue();
        assertThat(TopicPatternMatcher.matches("order.*", "order.shipped")).isTrue();
        assertThat(TopicPatternMatcher.matches("order.*", "order.paid.extra")).isFalse();
        assertThat(TopicPatternMatcher.matches("order.*", "order")).isFalse();
        assertThat(TopicPatternMatcher.matches("refund.resolved", "refund.resolved")).isTrue();
        assertThat(TopicPatternMatcher.matches("refund.resolved", "refund.requested")).isFalse();
        assertThat(TopicPatternMatcher.matches("showroom.*", "showroom.invite")).isTrue();
        assertThat(TopicPatternMatcher.matches("#", "anything.at.all")).isTrue();
        assertThat(TopicPatternMatcher.matches("review.moderated", "content.invalidated")).isFalse();
    }

    @Test
    @DisplayName("重试阶梯配置：缺省 5s/30s/180s，catalog 队列 1s/4s/16s，超出取末档")
    void retryLadderResolution() {
        MqProperties props = new MqProperties();
        MqProperties.QueueSpec sales = new MqProperties.QueueSpec();
        sales.setBindingKeys(List.of("order.paid"));
        sales.setRetryTtlMs(List.of(1000L, 4000L, 16000L));
        props.getQueues().put("q.catalog.sales", sales);
        MqProperties.QueueSpec mail = new MqProperties.QueueSpec();
        mail.setBindingKeys(List.of("order.*"));
        props.getQueues().put("q.mail", mail);

        assertThat(props.retryTtlMs("q.mail", 0)).isEqualTo(5000L);
        assertThat(props.retryTtlMs("q.mail", 1)).isEqualTo(30000L);
        assertThat(props.retryTtlMs("q.mail", 2)).isEqualTo(180000L);
        assertThat(props.retryTtlMs("q.mail", 9)).isEqualTo(180000L);
        assertThat(props.retryTtlMs("q.catalog.sales", 0)).isEqualTo(1000L);
        assertThat(props.retryTtlMs("q.catalog.sales", 2)).isEqualTo(16000L);
        assertThat(props.retryQueueName("q.mail")).isEqualTo("dreamy.retry.q.mail");
        assertThat(props.prefetch("q.mail")).isEqualTo(8);
    }
}
