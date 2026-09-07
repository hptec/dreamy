package com.dreamy.mq;

import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.outbox.entity.EventOutbox;
import com.dreamy.domain.outbox.repository.EventOutboxRepository;
import com.dreamy.domain.refund.entity.Refund;
import com.dreamy.enums.OutboxStatus;
import com.dreamy.enums.ProductionStage;
import com.dreamy.infra.TradingAfterCommitRunner;
import com.dreamy.infra.mq.DomainEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发件箱发布器单测（order-flow-complete §4.4 / §6.3 第 12 条）：
 * publish* → outbox(PENDING) 落表 → 无事务即时投递 → SENT；投递抛异常 → 保留 PENDING + 退避 next_attempt_at；
 * 第 8 次失败 → DEAD + 计数；relay 从 JSON 反序列化载荷（BigDecimal 保留）；新增三事件 payload 形状。
 */
@ExtendWith(MockitoExtension.class)
class TradingEventsPublisherTest {

    @Mock
    DomainEventPublisher domainEventPublisher;
    @Mock
    EventOutboxRepository outboxRepository;

    SimpleMeterRegistry meterRegistry;
    TradingEventsPublisher publisher;
    final AtomicLong ids = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        publisher = new TradingEventsPublisher(domainEventPublisher, outboxRepository,
                new TradingAfterCommitRunner(), new ObjectMapper(), meterRegistry);
        lenient().doAnswer(inv -> {
            EventOutbox row = inv.getArgument(0);
            row.setId(ids.getAndIncrement());
            return null;
        }).when(outboxRepository).insert(any(EventOutbox.class));
    }

    private Order order() {
        Order order = new Order();
        order.setId(100L);
        order.setOrderNo("DRM-1");
        order.setCustomerId(7L);
        order.setCurrency("USD");
        order.setTotalAmount(new BigDecimal("222.00"));
        order.setLocaleSnapshot("es");
        order.setDeliveredAt(LocalDateTime.of(2026, 9, 7, 10, 0));
        return order;
    }

    @Test
    @DisplayName("publishOrderPaid → outbox PENDING 落表 → 即时投递成功 → markSent(attempts=1)")
    void publishSuccessMarksSent() {
        when(domainEventPublisher.publishOrThrow(anyString(), any())).thenReturn("evt-1");
        publisher.publishOrderPaid(order(), List.of(), "fr");
        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(outboxRepository).insert(captor.capture());
        EventOutbox row = captor.getValue();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(row.getRoutingKey()).isEqualTo("order.paid");
        assertThat(row.getPayload()).contains("\"order_no\":\"DRM-1\"").contains("\"locale\":\"fr\"");
        verify(domainEventPublisher).publishOrThrow(eq("order.paid"), any());
        verify(outboxRepository).markSent(eq(1L), eq(1), any());
        verify(outboxRepository, never()).markRetry(anyLong(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("即时投递抛异常 → 保留 PENDING：markRetry(attempts=1, next=+1m, last_error) 不 markSent")
    void publishFailureKeepsPending() {
        doThrow(new IllegalStateException("broker down")).when(domainEventPublisher).publishOrThrow(anyString(), any());
        publisher.publishOrderCancelled(order(), "timeout");
        ArgumentCaptor<LocalDateTime> next = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxRepository).markRetry(eq(1L), eq(1), next.capture(), eq("IllegalStateException: broker down"));
        assertThat(next.getValue()).isAfter(LocalDateTime.now().plusSeconds(50));
        verify(outboxRepository, never()).markSent(anyLong(), anyInt(), any());
        verify(outboxRepository, never()).markDead(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("relay deliver：从 JSON 反序列化载荷（BigDecimal 保留）→ 投递成功 → SENT")
    void relayDeliverFromJson() {
        EventOutbox row = new EventOutbox();
        row.setId(9L);
        row.setRoutingKey("refund.resolved");
        row.setPayload("{\"refund_no\":\"RFD-1\",\"amount\":37.50,\"customer_id\":7}");
        row.setAttempts(2);
        row.setStatus(OutboxStatus.PENDING);
        when(domainEventPublisher.publishOrThrow(anyString(), any())).thenReturn("evt-x");

        assertThat(publisher.deliver(row)).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(domainEventPublisher).publishOrThrow(eq("refund.resolved"), payload.capture());
        assertThat(payload.getValue().get("amount")).isInstanceOf(BigDecimal.class)
                .isEqualTo(new BigDecimal("37.50"));
        verify(outboxRepository).markSent(eq(9L), eq(3), any());
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    @DisplayName("relay 第 8 次失败 → DEAD + dreamy.outbox.dead.total 计数 + 不再 markRetry")
    void relayDeadAfterMaxAttempts() {
        EventOutbox row = new EventOutbox();
        row.setId(9L);
        row.setRoutingKey("order.shipped");
        row.setPayload("{\"order_no\":\"DRM-1\"}");
        row.setAttempts(TradingEventsPublisher.MAX_ATTEMPTS - 1);
        row.setStatus(OutboxStatus.PENDING);
        doThrow(new RuntimeException("still down")).when(domainEventPublisher).publishOrThrow(anyString(), any());

        assertThat(publisher.deliver(row)).isFalse();

        verify(outboxRepository).markDead(eq(9L), eq(TradingEventsPublisher.MAX_ATTEMPTS),
                eq("RuntimeException: still down"));
        verify(outboxRepository, never()).markRetry(anyLong(), anyInt(), any(), anyString());
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(meterRegistry.counter(TradingEventsPublisher.METRIC_OUTBOX_DEAD, "routing_key", "order.shipped")
                .count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("退避阶梯 1m/5m/15m/1h，之后固定 1h")
    void backoffLadder() {
        assertThat(TradingEventsPublisher.backoff(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(TradingEventsPublisher.backoff(2)).isEqualTo(Duration.ofMinutes(5));
        assertThat(TradingEventsPublisher.backoff(3)).isEqualTo(Duration.ofMinutes(15));
        assertThat(TradingEventsPublisher.backoff(4)).isEqualTo(Duration.ofMinutes(60));
        assertThat(TradingEventsPublisher.backoff(7)).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    @DisplayName("outbox 落表失败 → 向上抛（业务事务回滚），绝不静默直投")
    void outboxInsertFailurePropagates() {
        doThrow(new RuntimeException("table missing")).when(outboxRepository).insert(any(EventOutbox.class));
        assertThatThrownBy(() -> publisher.publishOrderShipped(order(), null))
                .isInstanceOf(IllegalStateException.class);
        verify(domainEventPublisher, never()).publish(anyString(), any());
        verify(domainEventPublisher, never()).publishOrThrow(anyString(), any());
        verify(outboxRepository, never()).markSent(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("新增事件 payload：order.delivered / order.production / refund.requested 形状（order_id 供邮件消费者写 EMAIL 事件）")
    void newEventPayloads() {
        when(domainEventPublisher.publishOrThrow(anyString(), any())).thenReturn("evt");
        publisher.publishOrderDelivered(order(), null);
        publisher.publishOrderProduction(order(), ProductionStage.IN_PRODUCTION, "en");
        Refund refund = new Refund();
        refund.setRefundNo("RFD-1");
        refund.setOrderId(100L);
        refund.setCustomerId(7L);
        refund.setAmount(new BigDecimal("37.00"));
        refund.setCurrency("USD");
        publisher.publishRefundRequested(refund, "DRM-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(domainEventPublisher).publishOrThrow(eq("order.delivered"), payload.capture());
        assertThat(payload.getValue()).containsEntry("order_id", 100L).containsEntry("locale", "es")
                .containsEntry("delivered_at", "2026-09-07T10:00");
        verify(domainEventPublisher).publishOrThrow(eq("order.production"), payload.capture());
        assertThat(payload.getValue()).containsEntry("production_stage", 2).containsEntry("stage_name", "in_production");
        verify(domainEventPublisher).publishOrThrow(eq("refund.requested"), payload.capture());
        assertThat(payload.getValue()).containsEntry("refund_no", "RFD-1").containsEntry("order_id", 100L)
                .containsEntry("amount", new BigDecimal("37.00"));
    }
}
