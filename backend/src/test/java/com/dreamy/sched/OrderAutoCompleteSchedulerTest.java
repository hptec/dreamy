package com.dreamy.sched;

import com.dreamy.domain.checkout.entity.CheckoutConfig;
import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.order.service.OrderEventRecorder;
import com.dreamy.enums.OrderActorType;
import com.dreamy.enums.OrderStatus;
import com.dreamy.mq.TradingEventsPublisher;
import com.dreamy.testsupport.TradingImmediateTxRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自动签收/自动完成调度单测（order-flow-complete §4.2 / §6.1 第 2 条）：两条规则、单单失败不阻塞、
 * CAS affected=0（用户已确认）幂等跳过、锁未获取直接返回、每条转换一条 order_event(STATUS_CHANGED, SYSTEM)。
 */
@ExtendWith(MockitoExtension.class)
class OrderAutoCompleteSchedulerTest {

    @Mock
    RedissonClient redissonClient;
    @Mock
    RLock lock;
    @Mock
    OrderRepository orderRepository;
    @Mock
    CheckoutConfigRepository checkoutConfigRepository;
    @Mock
    OrderEventRecorder orderEventRecorder;
    @Mock
    TradingEventsPublisher eventsPublisher;

    SimpleMeterRegistry meterRegistry;
    OrderAutoCompleteScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new OrderAutoCompleteScheduler(redissonClient, orderRepository, checkoutConfigRepository,
                orderEventRecorder, eventsPublisher, new TradingImmediateTxRunner(), meterRegistry);
        CheckoutConfig config = new CheckoutConfig();
        config.setAutoCompleteDays(7);
        config.setAutoDeliverDays(30);
        lenient().when(checkoutConfigRepository.getSingleton()).thenReturn(config);
    }

    private Order order(long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("DRM-" + id);
        order.setCustomerId(7L);
        order.setStatus(status);
        order.setLocaleSnapshot("en");
        return order;
    }

    @Test
    @DisplayName("锁未获取 → 直接返回")
    void lockNotAcquired() {
        when(redissonClient.getLock(OrderAutoCompleteScheduler.LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);
        scheduler.run();
        verify(orderRepository, never()).listDeliveredBefore(any(), anyInt());
    }

    @Test
    @DisplayName("规则一：delivered_at + 7d → DELIVERED→COMPLETED（completed_at）+ 事件 + 计数；cutoff 按配置")
    void ruleAutoComplete() {
        Order a = order(1L, OrderStatus.DELIVERED);
        when(orderRepository.listDeliveredBefore(any(), eq(200))).thenReturn(List.of(a));
        when(orderRepository.listShippedBefore(any(), anyInt())).thenReturn(List.of());
        when(orderRepository.casUpdateStatus(eq(1L), eq(OrderStatus.DELIVERED), eq(OrderStatus.COMPLETED), any()))
                .thenReturn(1);

        int[] result = scheduler.sweep();

        assertThat(result).containsExactly(1, 0);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).listDeliveredBefore(cutoff.capture(), eq(200));
        assertThat(cutoff.getValue()).isBefore(LocalDateTime.now().minusDays(7).plusMinutes(1))
                .isAfter(LocalDateTime.now().minusDays(7).minusMinutes(1));
        verify(orderEventRecorder).statusChanged(eq(1L), eq(OrderStatus.DELIVERED), eq(OrderStatus.COMPLETED),
                eq(OrderActorType.SYSTEM), isNull(), any(), eq(true));
        assertThat(meterRegistry.counter(OrderAutoCompleteScheduler.METRIC_AUTOCOMPLETE, "rule", "complete").count())
                .isEqualTo(1.0);
        verify(eventsPublisher, never()).publishOrderDelivered(any(), any());
    }

    @Test
    @DisplayName("规则二：shipped_at + 30d 且无签收 → SHIPPED→DELIVERED（delivered_at）+ 事件 + order.delivered + 计数")
    void ruleAutoDeliver() {
        Order b = order(2L, OrderStatus.SHIPPED);
        when(orderRepository.listDeliveredBefore(any(), anyInt())).thenReturn(List.of());
        when(orderRepository.listShippedBefore(any(), eq(200))).thenReturn(List.of(b));
        when(orderRepository.casUpdateStatus(eq(2L), eq(OrderStatus.SHIPPED), eq(OrderStatus.DELIVERED), any()))
                .thenReturn(1);

        int[] result = scheduler.sweep();

        assertThat(result).containsExactly(0, 1);
        verify(orderEventRecorder).statusChanged(eq(2L), eq(OrderStatus.SHIPPED), eq(OrderStatus.DELIVERED),
                eq(OrderActorType.SYSTEM), isNull(), any(), eq(true));
        verify(eventsPublisher).publishOrderDelivered(eq(b), eq("en"));
        assertThat(b.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(meterRegistry.counter(OrderAutoCompleteScheduler.METRIC_AUTOCOMPLETE, "rule", "deliver").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("STATE-5：CAS affected=0（用户已确认收货）→ 幂等跳过，无事件无 MQ 无计数")
    void casMissIsIdempotent() {
        Order a = order(1L, OrderStatus.DELIVERED);
        Order b = order(2L, OrderStatus.SHIPPED);
        when(orderRepository.listDeliveredBefore(any(), anyInt())).thenReturn(List.of(a));
        when(orderRepository.listShippedBefore(any(), anyInt())).thenReturn(List.of(b));
        when(orderRepository.casUpdateStatus(anyLong(), any(), any(), any())).thenReturn(0);

        int[] result = scheduler.sweep();

        assertThat(result).containsExactly(0, 0);
        verify(orderEventRecorder, never()).statusChanged(anyLong(), any(), any(), any(), any(), any(), eq(true));
        verify(eventsPublisher, never()).publishOrderDelivered(any(), any());
        assertThat(meterRegistry.find(OrderAutoCompleteScheduler.METRIC_AUTOCOMPLETE).counter()).isNull();
    }

    @Test
    @DisplayName("单单独立事务：一单抛错不阻塞批次其余订单")
    void singleFailureDoesNotBlockBatch() {
        Order a = order(1L, OrderStatus.DELIVERED);
        Order b = order(2L, OrderStatus.DELIVERED);
        Order c = order(3L, OrderStatus.DELIVERED);
        when(orderRepository.listDeliveredBefore(any(), anyInt())).thenReturn(List.of(a, b, c));
        when(orderRepository.listShippedBefore(any(), anyInt())).thenReturn(List.of());
        when(orderRepository.casUpdateStatus(eq(1L), any(), any(), any())).thenReturn(1);
        when(orderRepository.casUpdateStatus(eq(2L), any(), any(), any())).thenThrow(new RuntimeException("db"));
        when(orderRepository.casUpdateStatus(eq(3L), any(), any(), any())).thenReturn(1);

        int[] result = scheduler.sweep();

        assertThat(result).containsExactly(2, 0);
        verify(orderRepository).casUpdateStatus(eq(3L), eq(OrderStatus.DELIVERED), eq(OrderStatus.COMPLETED), any());
    }
}
