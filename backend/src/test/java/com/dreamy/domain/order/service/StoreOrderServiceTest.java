package com.dreamy.domain.order.service;

import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.infra.stripe.StripePaymentIntent;
import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.domain.checkout.entity.CheckoutConfig;
import com.dreamy.enums.OrderStatus;
import com.dreamy.enums.PaymentStatus;
import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.payment.entity.Payment;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.domain.refund.repository.RefundRepository;
import com.dreamy.dto.TradingDtos.PaymentCredential;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 消费端订单服务单测（retryOrderPayment 矩阵 + 取消 guard）。
 * L2 TRACE: TC-TRD-063 [P1]（pending 未超时复用 client_secret / 已超时 410601 / 已支付 409602 /
 * PI canceled 重建新 PI）/ TC-TRD-044 [P0] 单测面（跨用户 404601 防探测）。
 */
@ExtendWith(MockitoExtension.class)
class StoreOrderServiceTest {

    private static final long CUSTOMER = 7L;
    private static final long ORDER_ID = 100L;

    @Mock
    OrderRepository orderRepository;
    @Mock
    OrderLineRepository orderLineRepository;
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    RefundRepository refundRepository;
    @Mock
    CheckoutConfigRepository checkoutConfigRepository;
    @Mock
    OrderCancelService orderCancelService;
    @Mock
    StripeClient stripeClient;

    StoreOrderService service;

    @BeforeEach
    void setUp() {
        service = new StoreOrderService(orderRepository, orderLineRepository, paymentRepository,
                refundRepository, checkoutConfigRepository, orderCancelService, stripeClient);
        CheckoutConfig config = new CheckoutConfig();
        config.setCustomRefundGraceHours(24);
        config.setGiftWrapFeeUsd(new BigDecimal("15.00"));
        lenient().when(checkoutConfigRepository.getSingleton()).thenReturn(config);
    }

    private Order order(OrderStatus status, LocalDateTime expiresAt) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("DRM-20260610-0001");
        order.setCustomerId(CUSTOMER);
        order.setStatus(status);
        order.setCurrency("USD");
        order.setTotalAmount(new BigDecimal("222.00"));
        order.setExpiresAt(expiresAt);
        return order;
    }

    @Test
    @DisplayName("TC-TRD-044 [P0] 单测面: 跨用户/不存在订单 → 404601（响应不泄露存在性）")
    void crossUserNotFound() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(null);
        assertThatThrownBy(() -> service.getOrderDetail(CUSTOMER, ORDER_ID))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_NOT_FOUND));
        assertThatThrownBy(() -> service.retryPayment(CUSTOMER, ORDER_ID))
                .isInstanceOf(TradingException.class);
    }

    @Test
    @DisplayName("TC-TRD-063 [P1]: pending 未超时 + PI requires_payment_method → 复用 client_secret")
    void retryReusesClientSecret() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER))
                .thenReturn(order(OrderStatus.PENDING, LocalDateTime.now().plusMinutes(10)));
        Payment payment = new Payment();
        payment.setId(5L);
        payment.setPaymentIntentId("pi_1");
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(payment);
        when(stripeClient.retrievePaymentIntent("pi_1"))
                .thenReturn(new StripePaymentIntent("pi_1", "pi_1_secret", "requires_payment_method", 22200L, "usd"));
        PaymentCredential credential = service.retryPayment(CUSTOMER, ORDER_ID);
        assertThat(credential.clientSecret()).isEqualTo("pi_1_secret");
        verify(stripeClient, never()).createPaymentIntent(anyLong(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("TC-TRD-063 [P1]: PI canceled → 重建新 PI 并 rebind（payment status 复位 created）")
    void retryRebuildsCanceledIntent() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER))
                .thenReturn(order(OrderStatus.PENDING, LocalDateTime.now().plusMinutes(10)));
        Payment payment = new Payment();
        payment.setId(5L);
        payment.setPaymentIntentId("pi_1");
        payment.setStatus(PaymentStatus.CREATED);
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(payment);
        when(stripeClient.retrievePaymentIntent("pi_1"))
                .thenReturn(new StripePaymentIntent("pi_1", null, "canceled", 22200L, "usd"));
        when(stripeClient.createPaymentIntent(eq(22200L), eq("usd"), anyString(), anyMap()))
                .thenReturn(new StripePaymentIntent("pi_2", "pi_2_secret", "requires_payment_method", 22200L, "usd"));
        PaymentCredential credential = service.retryPayment(CUSTOMER, ORDER_ID);
        assertThat(credential.paymentIntentId()).isEqualTo("pi_2");
        verify(paymentRepository).rebindPaymentIntent(5L, "pi_2");
    }

    @Test
    @DisplayName("TC-TRD-063 [P1]: cancelled 且 expires_at 已过 → 410601 ORDER_EXPIRED")
    void retryExpiredCancelled() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER))
                .thenReturn(order(OrderStatus.CANCELLED, LocalDateTime.now().minusMinutes(5)));
        assertThatThrownBy(() -> service.retryPayment(CUSTOMER, ORDER_ID))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_EXPIRED));
    }

    @Test
    @DisplayName("TC-TRD-063 [P1]: 已支付 → 409602")
    void retryPaidRejected() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER))
                .thenReturn(order(OrderStatus.PAID, LocalDateTime.now().minusMinutes(5)));
        assertThatThrownBy(() -> service.retryPayment(CUSTOMER, ORDER_ID))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_STATE_INVALID));
    }

    @Test
    @DisplayName("STEP-TRD-03: pending 已过期（调度器未及时取消）→ 内联超时取消 + 410601")
    void retryInlineTimeoutCancel() {
        Order order = order(OrderStatus.PENDING, LocalDateTime.now().minusMinutes(1));
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(order);
        assertThatThrownBy(() -> service.retryPayment(CUSTOMER, ORDER_ID))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_EXPIRED));
        verify(orderCancelService).cancelPending(eq(order), eq("timeout"));
    }

    @Test
    @DisplayName("E-cancelStoreOrder: 非 pending → 409602；与 webhook 竞态（cancelPending=false）→ 409602")
    void cancelGuards() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER))
                .thenReturn(order(OrderStatus.PAID, LocalDateTime.now()));
        assertThatThrownBy(() -> service.cancelOrder(CUSTOMER, ORDER_ID))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_STATE_INVALID));

        Order pending = order(OrderStatus.PENDING, LocalDateTime.now().plusMinutes(10));
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(pending);
        when(orderCancelService.cancelPending(any(Order.class), anyString())).thenReturn(false);
        assertThatThrownBy(() -> service.cancelOrder(CUSTOMER, ORDER_ID))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_STATE_INVALID));
    }
}
