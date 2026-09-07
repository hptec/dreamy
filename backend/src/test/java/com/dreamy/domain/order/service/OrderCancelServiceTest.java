package com.dreamy.domain.order.service;

import com.dreamy.domain.coupon.service.CouponDomainService;
import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.entity.OrderLine;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.payment.entity.Payment;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.enums.OrderActorType;
import com.dreamy.enums.OrderStatus;
import com.dreamy.infra.TradingAfterCommitRunner;
import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.mq.TradingEventsPublisher;
import com.dreamy.port.SkuStockAdapter;
import com.dreamy.testsupport.TradingImmediateTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 待支付取消事务体单测（TX-TRD-005；order-flow-complete §4.5 PENDING→CANCELLED 事件 actor 矩阵：
 * customer=CUSTOMER(actor_id=customer_id) / timeout=SYSTEM / admin=ADMIN(actor_id=operator)）。
 */
@ExtendWith(MockitoExtension.class)
class OrderCancelServiceTest {

    @Mock
    OrderRepository orderRepository;
    @Mock
    OrderLineRepository orderLineRepository;
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    SkuStockAdapter skuStockAdapter;
    @Mock
    CouponDomainService couponDomainService;
    @Mock
    StripeClient stripeClient;
    @Mock
    TradingEventsPublisher eventsPublisher;
    @Mock
    OrderEventRecorder orderEventRecorder;

    OrderCancelService service;

    @BeforeEach
    void setUp() {
        service = new OrderCancelService(orderRepository, orderLineRepository, paymentRepository, skuStockAdapter,
                couponDomainService, stripeClient, new TradingImmediateTxRunner(), new TradingAfterCommitRunner(),
                eventsPublisher, orderEventRecorder);
    }

    private Order order() {
        Order order = new Order();
        order.setId(100L);
        order.setOrderNo("DRM-1");
        order.setCustomerId(7L);
        order.setStatus(OrderStatus.PENDING);
        order.setCouponId(55L);
        return order;
    }

    @Test
    @DisplayName("customer 取消：CAS→回补→券回滚→order_event(STATUS_CHANGED, CUSTOMER, actor_id=customer_id)→MQ order.cancelled→事务外作废 PI")
    void customerCancel() {
        Order order = order();
        when(orderRepository.casUpdateStatus(100L, OrderStatus.PENDING, OrderStatus.CANCELLED, null)).thenReturn(1);
        OrderLine spot = new OrderLine();
        spot.setSkuId(21L);
        spot.setQty(2);
        when(orderLineRepository.listSpotLines(100L)).thenReturn(List.of(spot));
        Payment payment = new Payment();
        payment.setPaymentIntentId("pi_1");
        when(paymentRepository.findByOrderId(100L)).thenReturn(payment);

        assertThat(service.cancelPending(order, TradingEventsPublisher.CANCEL_REASON_CUSTOMER)).isTrue();

        verify(skuStockAdapter).restock(21L, 2);
        verify(couponDomainService).rollbackRedeem(55L);
        verify(orderEventRecorder).statusChanged(eq(100L), eq(OrderStatus.PENDING), eq(OrderStatus.CANCELLED),
                eq(OrderActorType.CUSTOMER), eq(7L), eq("cancel_reason=customer"), eq(true));
        verify(eventsPublisher).publishOrderCancelled(order, "customer");
        verify(stripeClient).cancelPaymentIntent("pi_1");
    }

    @Test
    @DisplayName("timeout 取消 → actor=SYSTEM actor_id=null；admin 取消（带 operatorId）→ actor=ADMIN")
    void actorMatrix() {
        when(orderRepository.casUpdateStatus(100L, OrderStatus.PENDING, OrderStatus.CANCELLED, null)).thenReturn(1);
        when(orderLineRepository.listSpotLines(100L)).thenReturn(List.of());

        service.cancelPending(order(), TradingEventsPublisher.CANCEL_REASON_TIMEOUT);
        verify(orderEventRecorder).statusChanged(eq(100L), eq(OrderStatus.PENDING), eq(OrderStatus.CANCELLED),
                eq(OrderActorType.SYSTEM), isNull(), eq("cancel_reason=timeout"), eq(true));

        service.cancelPending(order(), TradingEventsPublisher.CANCEL_REASON_ADMIN, 3L);
        verify(orderEventRecorder).statusChanged(eq(100L), eq(OrderStatus.PENDING), eq(OrderStatus.CANCELLED),
                eq(OrderActorType.ADMIN), eq(3L), eq("cancel_reason=admin"), eq(true));
    }

    @Test
    @DisplayName("TC-TRD-031：CAS affected=0（与 webhook 竞态）→ false，无回补无事件无 MQ")
    void casMissReturnsFalse() {
        when(orderRepository.casUpdateStatus(100L, OrderStatus.PENDING, OrderStatus.CANCELLED, null)).thenReturn(0);
        assertThat(service.cancelPending(order(), TradingEventsPublisher.CANCEL_REASON_TIMEOUT)).isFalse();
        verify(skuStockAdapter, never()).restock(anyLong(), any(Integer.class));
        verify(orderEventRecorder, never()).statusChanged(anyLong(), any(), any(), any(), any(), anyString(), eq(true));
        verify(eventsPublisher, never()).publishOrderCancelled(any(), anyString());
    }

    @Test
    @DisplayName("TC-TRD-082：事务外 cancelPaymentIntent 失败仅告警，取消结果仍为 true")
    void cancelIntentFailureTolerated() {
        when(orderRepository.casUpdateStatus(100L, OrderStatus.PENDING, OrderStatus.CANCELLED, null)).thenReturn(1);
        when(orderLineRepository.listSpotLines(100L)).thenReturn(List.of());
        Payment payment = new Payment();
        payment.setPaymentIntentId("pi_1");
        when(paymentRepository.findByOrderId(100L)).thenReturn(payment);
        doThrow(new RuntimeException("stripe down")).when(stripeClient).cancelPaymentIntent("pi_1");
        assertThat(service.cancelPending(order(), TradingEventsPublisher.CANCEL_REASON_CUSTOMER)).isTrue();
    }
}
