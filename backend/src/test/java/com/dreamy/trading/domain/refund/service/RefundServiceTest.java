package com.dreamy.trading.domain.refund.service;

import com.dreamy.identity.domain.user.repository.UserMapper;
import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.infra.stripe.StripeRefund;
import com.dreamy.infra.stripe.StripeUnavailableException;
import com.dreamy.trading.domain.checkout.entity.CheckoutConfig;
import com.dreamy.trading.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.trading.domain.enums.OrderStatus;
import com.dreamy.trading.domain.enums.PaymentStatus;
import com.dreamy.trading.domain.enums.RefundStatus;
import com.dreamy.trading.domain.order.entity.Order;
import com.dreamy.trading.domain.order.entity.OrderLine;
import com.dreamy.trading.domain.order.repository.OrderLineRepository;
import com.dreamy.trading.domain.order.repository.OrderRepository;
import com.dreamy.trading.domain.order.service.OrderNoGenerator;
import com.dreamy.trading.domain.payment.entity.Payment;
import com.dreamy.trading.domain.payment.repository.PaymentRepository;
import com.dreamy.trading.domain.refund.entity.Refund;
import com.dreamy.trading.domain.refund.repository.RefundRepository;
import com.dreamy.trading.error.TradingErrorCode;
import com.dreamy.trading.error.TradingException;
import com.dreamy.trading.infra.TradingAfterCommitRunner;
import com.dreamy.trading.infra.TradingAuditRecorder;
import com.dreamy.trading.mq.TradingEventsPublisher;
import com.dreamy.trading.port.SkuStockAdapter;
import com.dreamy.trading.testsupport.ImmediateTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款流服务单测（FLOW-P10 逻辑面；TX 原子回滚由 IT 层验证）。
 * L2 TRACE: TC-TRD-013 [P0]（422603 上限/=上限通过）/ TC-TRD-004（422602 + grace_deadline）/
 * TC-TRD-034 [P0] 单测面（Stripe 失败异常透出，后续账务步骤不执行）/ TC-TRD-035 [P0]（成功链）/
 * TC-TRD-036 [P0]（并发双审 casApprove=0 → 409604）/ TC-TRD-037 [P1]（拒绝还原 paid/shipped）/ 409605。
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final long ORDER_ID = 100L;
    private static final long REFUND_ID = 9L;

    @Mock
    RefundRepository refundRepository;
    @Mock
    OrderRepository orderRepository;
    @Mock
    OrderLineRepository orderLineRepository;
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    CheckoutConfigRepository checkoutConfigRepository;
    @Mock
    OrderNoGenerator orderNoGenerator;
    @Mock
    SkuStockAdapter skuStockAdapter;
    @Mock
    StripeClient stripeClient;
    @Mock
    TradingAuditRecorder audit;
    @Mock
    TradingEventsPublisher eventsPublisher;
    @Mock
    UserMapper userMapper;

    RefundService service;

    @BeforeEach
    void setUp() {
        service = new RefundService(refundRepository, orderRepository, orderLineRepository, paymentRepository,
                checkoutConfigRepository, orderNoGenerator, skuStockAdapter, stripeClient,
                new ImmediateTxRunner(), new TradingAfterCommitRunner(), audit, eventsPublisher, userMapper);
        CheckoutConfig config = new CheckoutConfig();
        config.setGiftWrapFeeUsd(new BigDecimal("15.00"));
        config.setCustomRefundGraceHours(24);
        lenient().when(checkoutConfigRepository.getSingleton()).thenReturn(config);
        lenient().when(orderNoGenerator.nextRefundNo()).thenReturn("RFD-20260610-0001");
        lenient().when(userMapper.selectByIds(any())).thenReturn(List.of());
    }

    private Order order(OrderStatus status, LocalDateTime paidAt, LocalDateTime shippedAt) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("DRM-20260610-0001");
        order.setCustomerId(7L);
        order.setStatus(status);
        order.setCurrency("USD");
        order.setTotalAmount(new BigDecimal("237.00"));
        order.setPaidAt(paidAt);
        order.setShippedAt(shippedAt);
        return order;
    }

    private Refund pendingRefund() {
        Refund refund = new Refund();
        refund.setId(REFUND_ID);
        refund.setRefundNo("RFD-20260610-0001");
        refund.setOrderId(ORDER_ID);
        refund.setCustomerId(7L);
        refund.setAmount(new BigDecimal("237.00"));
        refund.setCurrency("USD");
        refund.setStatus(RefundStatus.PENDING);
        refund.setAppliedAt(LocalDateTime.now());
        return refund;
    }

    // ==================== 申请 ====================

    @Test
    @DisplayName("TX-TRD-009a: 消费端申请 → INSERT pending（全额含 gift_wrap_fee）+ orders→refunding")
    void applyStoreRefundHappy() {
        Order order = order(OrderStatus.PAID, LocalDateTime.now().minusHours(1), null);
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, 7L)).thenReturn(order);
        when(refundRepository.existsPendingByOrderId(ORDER_ID)).thenReturn(false);
        when(orderLineRepository.existsCustomLine(ORDER_ID)).thenReturn(false);
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.PAID), eq(OrderStatus.REFUNDING),
                isNull())).thenReturn(1);
        var dto = service.applyStoreRefund(7L, ORDER_ID, "wrong size");
        assertThat(dto.amount()).isEqualByComparingTo("237.00");
        assertThat(dto.status()).isEqualTo("pending");
        verify(refundRepository).insert(any(Refund.class));
    }

    @Test
    @DisplayName("409605: 已有进行中工单 → REFUND_ALREADY_EXISTS")
    void pendingRefundExists() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, 7L))
                .thenReturn(order(OrderStatus.PAID, LocalDateTime.now(), null));
        when(refundRepository.existsPendingByOrderId(ORDER_ID)).thenReturn(true);
        assertThatThrownBy(() -> service.applyStoreRefund(7L, ORDER_ID, "reason"))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.REFUND_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("TC-TRD-004 [P0]: 定制行超宽限 → 422602 + details.grace_deadline（后台同样生效）")
    void customProducedRejected() {
        LocalDateTime paidAt = LocalDateTime.now().minusHours(25);
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, 7L))
                .thenReturn(order(OrderStatus.PAID, paidAt, null));
        when(refundRepository.existsPendingByOrderId(ORDER_ID)).thenReturn(false);
        when(orderLineRepository.existsCustomLine(ORDER_ID)).thenReturn(true);
        assertThatThrownBy(() -> service.applyStoreRefund(7L, ORDER_ID, "reason"))
                .isInstanceOfSatisfying(TradingException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.CUSTOM_ITEM_NOT_REFUNDABLE);
                    assertThat(ex.getDetails()).containsEntry("grace_deadline", paidAt.plusHours(24));
                });
        verify(refundRepository, never()).insert(any());
    }

    @Test
    @DisplayName("TC-TRD-013 [P0]: admin amount > total_amount → 422603 + max_refundable；= 上限通过")
    void refundAmountLimit() {
        Order order = order(OrderStatus.PAID, LocalDateTime.now(), null);
        when(orderRepository.findById(ORDER_ID)).thenReturn(order);
        assertThatThrownBy(() -> service.createAdminRefund(ORDER_ID, new BigDecimal("237.01"), "reason"))
                .isInstanceOfSatisfying(TradingException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.REFUND_AMOUNT_EXCEEDED);
                    assertThat(ex.getDetails()).containsEntry("max_refundable", new BigDecimal("237.00"));
                });
        // = 上限通过
        when(refundRepository.existsPendingByOrderId(ORDER_ID)).thenReturn(false);
        when(orderLineRepository.existsCustomLine(ORDER_ID)).thenReturn(false);
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.PAID), eq(OrderStatus.REFUNDING),
                isNull())).thenReturn(1);
        var dto = service.createAdminRefund(ORDER_ID, new BigDecimal("237.00"), "reason");
        assertThat(dto.amount()).isEqualByComparingTo("237.00");
        verify(audit).record(eq(TradingAuditRecorder.ACTION_REFUND_CREATE), anyString(), anyString());
    }

    @Test
    @DisplayName("409602: 状态 ∉ {paid, shipped} 不可申请")
    void applyInvalidState() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, 7L))
                .thenReturn(order(OrderStatus.PENDING, null, null));
        assertThatThrownBy(() -> service.applyStoreRefund(7L, ORDER_ID, "reason"))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_STATE_INVALID));
    }

    // ==================== 审核 ====================

    @Test
    @DisplayName("TC-TRD-035 [P0]: 审核通过成功链——casApprove→Stripe→refund_id→订单 refunded→回补→payment→审计→MQ")
    void approveHappyChain() {
        Refund refund = pendingRefund();
        when(refundRepository.findById(REFUND_ID)).thenReturn(refund);
        Order order = order(OrderStatus.REFUNDING, LocalDateTime.now().minusHours(1), null);
        when(orderRepository.findById(ORDER_ID)).thenReturn(order);
        Payment payment = new Payment();
        payment.setId(5L);
        payment.setOrderId(ORDER_ID);
        payment.setPaymentIntentId("pi_1");
        payment.setStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(payment);
        when(refundRepository.casApprove(REFUND_ID, "SF123")).thenReturn(1);
        when(stripeClient.createRefund(eq("pi_1"), eq(23700L), anyString()))
                .thenReturn(new StripeRefund("re_1", "succeeded", 23700L, "usd"));
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.REFUNDING), eq(OrderStatus.REFUNDED),
                isNull())).thenReturn(1);
        OrderLine spot = new OrderLine();
        spot.setSkuId(21L);
        spot.setQty(2);
        when(orderLineRepository.listSpotLines(ORDER_ID)).thenReturn(List.of(spot));

        service.approve(REFUND_ID, "SF123");

        verify(refundRepository).updateStripeRefundId(REFUND_ID, "re_1");
        verify(skuStockAdapter).restock(21L, 2);
        verify(paymentRepository).casUpdateStatus(eq(5L), eq(List.of(PaymentStatus.SUCCEEDED)),
                eq(PaymentStatus.REFUNDED), isNull(), isNull());
        verify(audit).record(eq(TradingAuditRecorder.ACTION_REFUND_APPROVE), eq("RFD-20260610-0001"), anyString());
        verify(eventsPublisher).publishRefundResolved(any(Refund.class), eq("DRM-20260610-0001"),
                eq("approved"), isNull());
    }

    @Test
    @DisplayName("TC-TRD-036 [P0]: 并发双审 casApprove=0 → 409604（恰一次生效）")
    void concurrentDoubleApprove() {
        when(refundRepository.findById(REFUND_ID)).thenReturn(pendingRefund());
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(order(OrderStatus.REFUNDING, LocalDateTime.now(), null));
        when(refundRepository.casApprove(REFUND_ID, null)).thenReturn(0);
        assertThatThrownBy(() -> service.approve(REFUND_ID, null))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.REFUND_STATE_INVALID));
        verify(stripeClient, never()).createRefund(anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("TC-TRD-034 [P0] 单测面: Stripe 失败异常透出（502601）——账务推进步骤一律未执行（回滚由 TX 承载）")
    void approveStripeFailureRollsBack() {
        when(refundRepository.findById(REFUND_ID)).thenReturn(pendingRefund());
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(order(OrderStatus.REFUNDING, LocalDateTime.now(), null));
        Payment payment = new Payment();
        payment.setId(5L);
        payment.setPaymentIntentId("pi_1");
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(payment);
        when(refundRepository.casApprove(REFUND_ID, null)).thenReturn(1);
        when(stripeClient.createRefund(anyString(), anyLong(), anyString()))
                .thenThrow(new StripeUnavailableException("down", null));
        assertThatThrownBy(() -> service.approve(REFUND_ID, null))
                .isInstanceOf(StripeUnavailableException.class);
        verify(refundRepository, never()).updateStripeRefundId(anyLong(), anyString());
        verify(orderRepository, never()).casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.REFUNDING),
                eq(OrderStatus.REFUNDED), isNull());
        verify(skuStockAdapter, never()).restock(anyLong(), anyInt());
        verify(eventsPublisher, never()).publishRefundResolved(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("TC-TRD-008: 非 pending 审核 → 409604（js_guard）")
    void approveNonPending() {
        Refund approved = pendingRefund();
        approved.setStatus(RefundStatus.APPROVED);
        when(refundRepository.findById(REFUND_ID)).thenReturn(approved);
        assertThatThrownBy(() -> service.approve(REFUND_ID, null))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.REFUND_STATE_INVALID));
    }

    @Test
    @DisplayName("TC-TRD-037 [P1]: 拒绝还原——未发货 refunding→paid；已发货 refunding→shipped；reject_reason 落独立列")
    void rejectRestoresState() {
        Refund refund = pendingRefund();
        when(refundRepository.findById(REFUND_ID)).thenReturn(refund);
        when(refundRepository.casReject(eq(REFUND_ID), anyString())).thenReturn(1);
        // 未发货 → paid
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(order(OrderStatus.REFUNDING, LocalDateTime.now(), null));
        service.reject(REFUND_ID, "退货未收到");
        verify(orderRepository).casRestoreFromRefunding(ORDER_ID, OrderStatus.PAID);
        verify(refundRepository).casReject(REFUND_ID, "退货未收到");
        verify(eventsPublisher).publishRefundResolved(any(Refund.class), anyString(), eq("rejected"),
                eq("退货未收到"));
        // 已发货 → shipped
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(order(OrderStatus.REFUNDING, LocalDateTime.now(), LocalDateTime.now()));
        service.reject(REFUND_ID, "再次拒绝");
        verify(orderRepository).casRestoreFromRefunding(ORDER_ID, OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("决策 31: pending 工单登记退货单号；非 pending → 409604（已审结不可改登记）")
    void patchReturnTrackingNo() {
        Refund refund = pendingRefund();
        when(refundRepository.findById(REFUND_ID)).thenReturn(refund);
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(order(OrderStatus.REFUNDING, LocalDateTime.now(), null));
        service.patchReturnTrackingNo(REFUND_ID, "SF999");
        verify(refundRepository).updateReturnTrackingNo(REFUND_ID, "SF999");
        verify(eventsPublisher, never()).publishRefundResolved(any(), anyString(), anyString(), any());

        Refund rejected = pendingRefund();
        rejected.setStatus(RefundStatus.REJECTED);
        when(refundRepository.findById(REFUND_ID)).thenReturn(rejected);
        assertThatThrownBy(() -> service.patchReturnTrackingNo(REFUND_ID, "SF999"))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.REFUND_STATE_INVALID));
    }
}
