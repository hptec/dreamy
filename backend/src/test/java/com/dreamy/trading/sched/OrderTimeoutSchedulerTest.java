package com.dreamy.trading.sched;

import com.dreamy.trading.domain.order.entity.Order;
import com.dreamy.trading.domain.order.repository.OrderRepository;
import com.dreamy.trading.domain.order.service.OrderCancelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超时取消调度单测（逻辑面；分布式锁/DB 终态由 IT 层验证）。
 * L2 TRACE: TC-TRD-032 [P0] 单测面（过期 pending 逐单取消；单单事务一单失败不阻塞批次）。
 */
@ExtendWith(MockitoExtension.class)
class OrderTimeoutSchedulerTest {

    @Mock
    RedissonClient redissonClient;
    @Mock
    OrderRepository orderRepository;
    @Mock
    OrderCancelService orderCancelService;

    @InjectMocks
    OrderTimeoutScheduler scheduler;

    private Order order(long id) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("DRM-20260610-000" + id);
        return order;
    }

    @Test
    @DisplayName("TC-TRD-032 [P0]: 逐单取消（cancel_reason=timeout）；一单抛错不阻塞其余")
    void sweepContinuesOnSingleFailure() {
        Order a = order(1L);
        Order b = order(2L);
        Order c = order(3L);
        when(orderRepository.listExpiredPending(any(), anyInt())).thenReturn(List.of(a, b, c));
        when(orderCancelService.cancelPending(eq(a), eq("timeout"))).thenReturn(true);
        when(orderCancelService.cancelPending(eq(b), eq("timeout"))).thenThrow(new RuntimeException("boom"));
        when(orderCancelService.cancelPending(eq(c), eq("timeout"))).thenReturn(true);

        scheduler.cancelExpiredOrders();

        verify(orderCancelService).cancelPending(eq(a), eq("timeout"));
        verify(orderCancelService).cancelPending(eq(b), eq("timeout"));
        // 关键断言：b 失败后 c 仍被处理（单单事务，TC-TRD-032）
        verify(orderCancelService).cancelPending(eq(c), eq("timeout"));
    }
}
