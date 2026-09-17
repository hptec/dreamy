package com.dreamy.domain.refund.service;

import com.dreamy.infra.grpc.CustomerInfoPort;
import com.dreamy.infra.grpc.IdentityGateClient;
import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.infra.stripe.StripeRefund;
import com.dreamy.infra.stripe.StripeUnavailableException;
import com.dreamy.domain.checkout.entity.CheckoutConfig;
import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.enums.OrderActorType;
import com.dreamy.enums.OrderEventType;
import com.dreamy.enums.OrderStatus;
import com.dreamy.enums.PaymentStatus;
import com.dreamy.enums.ProductionStage;
import com.dreamy.enums.RefundStatus;
import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.entity.OrderLine;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.order.service.OrderEventRecorder;
import com.dreamy.domain.order.service.OrderNoGenerator;
import com.dreamy.domain.payment.entity.Payment;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.domain.refund.entity.Refund;
import com.dreamy.domain.refund.repository.RefundRepository;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingAfterCommitRunner;
import com.dreamy.infra.TradingAuditRecorder;
import com.dreamy.mq.TradingEventsPublisher;
import com.dreamy.port.SkuStockAdapter;
import com.dreamy.testsupport.TradingImmediateTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    CustomerInfoPort customerInfoPort;
    @Mock
    IdentityGateClient identityGateClient;
    @Mock
    OrderEventRecorder orderEventRecorder;

    RefundService service;

    /** 初始化 MyBatis-Plus lambda 缓存（findUserIdsByNameOrEmailLike 的 LambdaQueryWrapper 需要 User TableInfo）。 */
    @BeforeEach
    void setUp() {
        service = new RefundService(refundRepository, orderRepository, orderLineRepository, paymentRepository,
                checkoutConfigRepository, orderNoGenerator, skuStockAdapter, stripeClient,
                new TradingImmediateTxRunner(), new TradingAfterCommitRunner(), audit, eventsPublisher,
                customerInfoPort, identityGateClient,
                orderEventRecorder);
        CheckoutConfig config = new CheckoutConfig();
        config.setGiftWrapFeeUsd(new BigDecimal("15.00"));
        config.setCustomRefundGraceHours(24);
        lenient().when(checkoutConfigRepository.getSingleton()).thenReturn(config);
        lenient().when(orderNoGenerator.nextRefundNo()).thenReturn("RFD-20260610-0001");
        lenient().when(customerInfoPort.byIds(any())).thenReturn(java.util.Map.of());
        lenient().when(identityGateClient.listUsers(any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(dreamy.identity.v1.ListUsersResponse.getDefaultInstance());
    }

    private Order order(OrderStatus status, LocalDateTime paidAt, LocalDateTime shippedAt) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("DRM-20260610-0001");
        order.setCustomerId(7L);
        order.setStatus(status);
        order.setCurrency("USD");
        order.setTotalAmount(new BigDecimal("237.00"));
        order.setRefundedAmount(BigDecimal.ZERO);
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
    @DisplayName("TX-TRD-009a: 消费端申请 → 先 CAS paid→refunding 再 INSERT pending（金额=剩余可退额，from_status/from_stage 快照）+ 事件 + refund.requested")
    void applyStoreRefundHappy() {
        Order order = order(OrderStatus.PAID, LocalDateTime.now().minusHours(1), null);
        order.setProductionStage(ProductionStage.IN_PRODUCTION);
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, 7L)).thenReturn(order);
        when(orderLineRepository.existsCustomLine(ORDER_ID)).thenReturn(false);
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.PAID), eq(OrderStatus.REFUNDING),
                isNull())).thenReturn(1);
        var dto = service.applyStoreRefund(7L, ORDER_ID, "wrong size");
        assertThat(dto.amount()).isEqualByComparingTo("237.00");
        assertThat(dto.status()).isEqualTo(1);
        assertThat(dto.fromStatus()).isEqualTo(OrderStatus.PAID.getKey());
        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).insert(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(captor.getValue().getFromStage()).isEqualTo(ProductionStage.IN_PRODUCTION);
        // §4.5 →REFUNDING 一条 order_event(REFUND, CUSTOMER, 可见)
        verify(orderEventRecorder).record(eq(ORDER_ID), eq(OrderEventType.REFUND), eq(OrderActorType.CUSTOMER),
                eq(7L), any(), any(), any(), eq(true));
        verify(eventsPublisher).publishRefundRequested(any(Refund.class), eq("DRM-20260610-0001"));
    }

    @Test
    @DisplayName("order-flow-complete: 部分已退后再申请 → 金额 = total − refunded_amount")
    void applyStoreRefundUsesRemaining() {
        Order order = order(OrderStatus.DELIVERED, LocalDateTime.now().minusHours(1), LocalDateTime.now());
        order.setRefundedAmount(new BigDecimal("37.00"));
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, 7L)).thenReturn(order);
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.DELIVERED), eq(OrderStatus.REFUNDING),
                isNull())).thenReturn(1);
        var dto = service.applyStoreRefund(7L, ORDER_ID, "damaged");
        assertThat(dto.amount()).isEqualByComparingTo("200.00");
        assertThat(dto.fromStatus()).isEqualTo(OrderStatus.DELIVERED.getKey());
    }

    @Test
    @DisplayName("409907: 订单已 REFUNDING（已有挂起工单）→ REFUND_PENDING_EXISTS（js_guard）")
    void pendingRefundExistsGuard() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, 7L))
                .thenReturn(order(OrderStatus.REFUNDING, LocalDateTime.now(), null));
        assertThatThrownBy(() -> service.applyStoreRefund(7L, ORDER_ID, "reason"))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.REFUND_PENDING_EXISTS));
        verify(refundRepository, never()).insert(any());
    }

    @Test
    @DisplayName("409907: 并发第二张——CAS →REFUNDING affected=0 → REFUND_PENDING_EXISTS，不插工单（DB 终防线）")
    void secondPendingRefundRejectedByCas() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, 7L))
                .thenReturn(order(OrderStatus.PAID, LocalDateTime.now(), null));
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.PAID), eq(OrderStatus.REFUNDING),
                isNull())).thenReturn(0);
        assertThatThrownBy(() -> service.applyStoreRefund(7L, ORDER_ID, "reason"))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.REFUND_PENDING_EXISTS));
        verify(refundRepository, never()).insert(any());
        verify(eventsPublisher, never()).publishRefundRequested(any(), anyString());
    }

    @Test
    @DisplayName("TC-TRD-004 [P0]: 定制行超宽限 → 422602 + details.grace_deadline（后台同样生效）")
    void customProducedRejected() {
        LocalDateTime paidAt = LocalDateTime.now().minusHours(25);
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, 7L))
                .thenReturn(order(OrderStatus.PAID, paidAt, null));
        when(orderLineRepository.existsCustomLine(ORDER_ID)).thenReturn(true);
        assertThatThrownBy(() -> service.applyStoreRefund(7L, ORDER_ID, "reason"))
                .isInstanceOfSatisfying(TradingException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.CUSTOM_ITEM_NOT_REFUNDABLE);
                    assertThat(ex.getDetails()).containsEntry("grace_deadline", paidAt.plusHours(24));
                });
        verify(refundRepository, never()).insert(any());
        verify(orderRepository, never()).casUpdateStatus(anyLong(), any(), any(), any());
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
        when(orderLineRepository.existsCustomLine(ORDER_ID)).thenReturn(false);
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.PAID), eq(OrderStatus.REFUNDING),
                isNull())).thenReturn(1);
        var dto = service.createAdminRefund(ORDER_ID, new BigDecimal("237.00"), "reason");
        assertThat(dto.amount()).isEqualByComparingTo("237.00");
        verify(audit).record(eq(TradingAuditRecorder.ACTION_REFUND_CREATE), anyString(), anyString());
    }

    @Test
    @DisplayName("422908: admin amount > total − refunded_amount（部分已退）→ REFUND_TOTAL_EXCEEDED + max_refundable=剩余")
    void refundRemainingLimit() {
        Order order = order(OrderStatus.PAID, LocalDateTime.now(), null);
        order.setRefundedAmount(new BigDecimal("200.00"));
        when(orderRepository.findById(ORDER_ID)).thenReturn(order);
        assertThatThrownBy(() -> service.createAdminRefund(ORDER_ID, new BigDecimal("37.01"), "reason"))
                .isInstanceOfSatisfying(TradingException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.REFUND_TOTAL_EXCEEDED);
                    assertThat(ex.getDetails()).containsEntry("max_refundable", new BigDecimal("37.00"));
                });
        verify(refundRepository, never()).insert(any());
    }

    @Test
    @DisplayName("409602: 状态 ∉ {paid, shipped, delivered} 不可申请")
    void applyInvalidState() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, 7L))
                .thenReturn(order(OrderStatus.PENDING, null, null));
        assertThatThrownBy(() -> service.applyStoreRefund(7L, ORDER_ID, "reason"))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_STATE_INVALID));
    }

    // ==================== 审核 ====================

    private Payment succeededPayment() {
        Payment payment = new Payment();
        payment.setId(5L);
        payment.setOrderId(ORDER_ID);
        payment.setPaymentIntentId("pi_1");
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setAmount(new BigDecimal("237.00"));
        payment.setRefundedAmount(BigDecimal.ZERO);
        return payment;
    }

    @Test
    @DisplayName("TC-TRD-035 [P0]: 全额批准成功链——casApprove→refunded_amount+=→Stripe delta→refund_id→payment 累计→单条 UPDATE→REFUNDED→回补→事件→审计→MQ")
    void approveHappyChainFull() {
        Refund refund = pendingRefund();
        refund.setFromStatus(OrderStatus.PAID);
        when(refundRepository.findById(REFUND_ID)).thenReturn(refund);
        Order refunding = order(OrderStatus.REFUNDING, LocalDateTime.now().minusHours(1), null);
        Order refunded = order(OrderStatus.REFUNDED, LocalDateTime.now().minusHours(1), null);
        refunded.setRefundedAmount(new BigDecimal("237.00"));
        when(orderRepository.findById(ORDER_ID)).thenReturn(refunding, refunded, refunded);
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(succeededPayment());
        when(refundRepository.casApprove(REFUND_ID, "SF123")).thenReturn(1);
        when(orderRepository.addRefundedAmount(ORDER_ID, new BigDecimal("237.00"))).thenReturn(1);
        when(stripeClient.createRefund(eq("pi_1"), eq(23700L), anyString(), anyString()))
                .thenReturn(new StripeRefund("re_1", "succeeded", 23700L, "usd"));
        when(paymentRepository.applyRefund(5L, new BigDecimal("237.00"))).thenReturn(1);
        when(orderRepository.casResolveRefunding(ORDER_ID, OrderStatus.PAID, null)).thenReturn(1);
        OrderLine spot = new OrderLine();
        spot.setSkuId(21L);
        spot.setQty(2);
        when(orderLineRepository.listSpotLines(ORDER_ID)).thenReturn(List.of(spot));

        service.approve(REFUND_ID, "SF123");

        verify(refundRepository).updateStripeRefundId(REFUND_ID, "re_1");
        verify(skuStockAdapter).restock(21L, 2);
        verify(paymentRepository).applyRefund(5L, new BigDecimal("237.00"));
        verify(orderEventRecorder).record(eq(ORDER_ID), eq(OrderEventType.REFUND), eq(OrderActorType.ADMIN),
                any(), eq("Refund approved (full)"), any(), any(), eq(true));
        verify(audit).record(eq(TradingAuditRecorder.ACTION_REFUND_APPROVE), eq("RFD-20260610-0001"), anyString());
        verify(eventsPublisher).publishRefundResolved(any(Refund.class), eq("DRM-20260610-0001"),
                eq("approved"), isNull());
    }

    @Test
    @DisplayName("order-flow-complete: 部分批准 → 还原 from_status（casResolveRefunding 携带 from_stage）；不回补库存；payment 仅退 delta")
    void approvePartialRestoresFromStatus() {
        Refund refund = pendingRefund();
        refund.setAmount(new BigDecimal("37.00"));
        refund.setFromStatus(OrderStatus.PAID);
        refund.setFromStage(ProductionStage.QUALITY_CHECK);
        when(refundRepository.findById(REFUND_ID)).thenReturn(refund);
        Order refunding = order(OrderStatus.REFUNDING, LocalDateTime.now().minusHours(1), null);
        Order restored = order(OrderStatus.PAID, LocalDateTime.now().minusHours(1), null);
        restored.setRefundedAmount(new BigDecimal("37.00"));
        restored.setProductionStage(ProductionStage.QUALITY_CHECK);
        when(orderRepository.findById(ORDER_ID)).thenReturn(refunding, restored, restored);
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(succeededPayment());
        when(refundRepository.casApprove(REFUND_ID, null)).thenReturn(1);
        when(orderRepository.addRefundedAmount(ORDER_ID, new BigDecimal("37.00"))).thenReturn(1);
        when(stripeClient.createRefund(eq("pi_1"), eq(3700L), anyString(), anyString()))
                .thenReturn(new StripeRefund("re_2", "succeeded", 3700L, "usd"));
        when(paymentRepository.applyRefund(5L, new BigDecimal("37.00"))).thenReturn(1);
        when(orderRepository.casResolveRefunding(ORDER_ID, OrderStatus.PAID, ProductionStage.QUALITY_CHECK))
                .thenReturn(1);

        service.approve(REFUND_ID, null);

        verify(stripeClient).createRefund(eq("pi_1"), eq(3700L), anyString(), anyString());
        verify(orderRepository).casResolveRefunding(ORDER_ID, OrderStatus.PAID, ProductionStage.QUALITY_CHECK);
        verify(skuStockAdapter, never()).restock(anyLong(), anyInt());
        verify(orderEventRecorder).record(eq(ORDER_ID), eq(OrderEventType.REFUND), eq(OrderActorType.ADMIN),
                any(), eq("Refund approved (partial)"), any(), any(), eq(true));
    }

    @Test
    @DisplayName("order-flow-complete: 两次部分批准累计达 total → 第二次 REFUNDED + 回补（终态由 SQL 判定，服务按重读状态回补）")
    void approveSecondPartialReachesTotal() {
        Refund refund = pendingRefund();
        refund.setAmount(new BigDecimal("200.00"));
        refund.setFromStatus(OrderStatus.SHIPPED);
        when(refundRepository.findById(REFUND_ID)).thenReturn(refund);
        Order refunding = order(OrderStatus.REFUNDING, LocalDateTime.now().minusHours(1), LocalDateTime.now());
        refunding.setRefundedAmount(new BigDecimal("37.00"));
        Order refunded = order(OrderStatus.REFUNDED, LocalDateTime.now().minusHours(1), LocalDateTime.now());
        refunded.setRefundedAmount(new BigDecimal("237.00"));
        when(orderRepository.findById(ORDER_ID)).thenReturn(refunding, refunded, refunded);
        Payment payment = succeededPayment();
        payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        payment.setRefundedAmount(new BigDecimal("37.00"));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(payment);
        when(refundRepository.casApprove(REFUND_ID, null)).thenReturn(1);
        when(orderRepository.addRefundedAmount(ORDER_ID, new BigDecimal("200.00"))).thenReturn(1);
        when(stripeClient.createRefund(eq("pi_1"), eq(20000L), anyString(), anyString()))
                .thenReturn(new StripeRefund("re_3", "succeeded", 20000L, "usd"));
        when(paymentRepository.applyRefund(5L, new BigDecimal("200.00"))).thenReturn(1);
        when(orderRepository.casResolveRefunding(ORDER_ID, OrderStatus.SHIPPED, null)).thenReturn(1);
        when(orderLineRepository.listSpotLines(ORDER_ID)).thenReturn(List.of());

        var dto = service.approve(REFUND_ID, null);

        verify(orderLineRepository).listSpotLines(ORDER_ID);
        verify(orderEventRecorder).record(eq(ORDER_ID), eq(OrderEventType.REFUND), eq(OrderActorType.ADMIN),
                any(), eq("Refund approved (full)"), any(), any(), eq(true));
        assertThat(dto.fromStatus()).isEqualTo(OrderStatus.SHIPPED.getKey());
    }

    @Test
    @DisplayName("422908: 批准时 refunded_amount + amount > total（条件更新 affected=0）→ REFUND_TOTAL_EXCEEDED，Stripe 未触达")
    void approveOverTotalRejected() {
        Refund refund = pendingRefund();
        when(refundRepository.findById(REFUND_ID)).thenReturn(refund);
        Order refunding = order(OrderStatus.REFUNDING, LocalDateTime.now(), null);
        refunding.setRefundedAmount(new BigDecimal("200.00"));
        when(orderRepository.findById(ORDER_ID)).thenReturn(refunding);
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(succeededPayment());
        when(refundRepository.casApprove(REFUND_ID, null)).thenReturn(1);
        when(orderRepository.addRefundedAmount(ORDER_ID, new BigDecimal("237.00"))).thenReturn(0);
        assertThatThrownBy(() -> service.approve(REFUND_ID, null))
                .isInstanceOfSatisfying(TradingException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.REFUND_TOTAL_EXCEEDED);
                    assertThat(ex.getDetails()).containsEntry("max_refundable", new BigDecimal("37.00"));
                });
        verify(stripeClient, never()).createRefund(anyString(), any(), anyString(), anyString());
        verify(orderRepository, never()).casResolveRefunding(anyLong(), any(), any());
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
        verify(stripeClient, never()).createRefund(anyString(), anyLong(), anyString(), anyString());
        verify(orderRepository, never()).addRefundedAmount(anyLong(), any());
    }

    @Test
    @DisplayName("TC-TRD-034 [P0] 单测面: Stripe 失败异常透出（502601）——账务推进步骤一律未执行（回滚由 TX 承载）")
    void approveStripeFailureRollsBack() {
        when(refundRepository.findById(REFUND_ID)).thenReturn(pendingRefund());
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(order(OrderStatus.REFUNDING, LocalDateTime.now(), null));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(succeededPayment());
        when(refundRepository.casApprove(REFUND_ID, null)).thenReturn(1);
        when(orderRepository.addRefundedAmount(eq(ORDER_ID), any())).thenReturn(1);
        when(stripeClient.createRefund(anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new StripeUnavailableException("down", null));
        assertThatThrownBy(() -> service.approve(REFUND_ID, null))
                .isInstanceOf(StripeUnavailableException.class);
        verify(refundRepository, never()).updateStripeRefundId(anyLong(), anyString());
        verify(paymentRepository, never()).applyRefund(anyLong(), any());
        verify(orderRepository, never()).casResolveRefunding(anyLong(), any(), any());
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
    @DisplayName("TC-TRD-037 [P1]: 拒绝还原 from_status 快照（PAID 回写 from_stage；DELIVERED 还原 DELIVERED）；无快照按 shipped_at 推断；reject_reason 落独立列")
    void rejectRestoresState() {
        Refund refund = pendingRefund();
        refund.setFromStatus(OrderStatus.PAID);
        refund.setFromStage(ProductionStage.IN_PRODUCTION);
        when(refundRepository.findById(REFUND_ID)).thenReturn(refund);
        when(refundRepository.casReject(eq(REFUND_ID), anyString())).thenReturn(1);
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(order(OrderStatus.REFUNDING, LocalDateTime.now(), null));
        service.reject(REFUND_ID, "退货未收到");
        verify(orderRepository).casRestoreFromRefunding(ORDER_ID, OrderStatus.PAID, ProductionStage.IN_PRODUCTION);
        verify(refundRepository).casReject(REFUND_ID, "退货未收到");
        verify(orderEventRecorder).record(eq(ORDER_ID), eq(OrderEventType.REFUND), eq(OrderActorType.ADMIN),
                any(), eq("Refund rejected"), eq("退货未收到"), any(), eq(true));
        verify(eventsPublisher).publishRefundResolved(any(Refund.class), anyString(), eq("rejected"),
                eq("退货未收到"));
        // DELIVERED 快照 → 还原 DELIVERED
        refund.setFromStatus(OrderStatus.DELIVERED);
        refund.setFromStage(null);
        service.reject(REFUND_ID, "再次拒绝");
        verify(orderRepository).casRestoreFromRefunding(ORDER_ID, OrderStatus.DELIVERED, null);
        // 存量工单无快照 → 按 shipped_at 推断 SHIPPED
        refund.setFromStatus(null);
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(order(OrderStatus.REFUNDING, LocalDateTime.now(), LocalDateTime.now()));
        service.reject(REFUND_ID, "第三次");
        verify(orderRepository).casRestoreFromRefunding(ORDER_ID, OrderStatus.SHIPPED, null);
    }

    // ==================== 后台取消已支付（STATE-7） ====================

    @Test
    @DisplayName("STATE-7: adminCancelPaidOrder → PAID→REFUNDING → 创建+批准全额剩余 → Stripe delta → REFUNDING→CANCELLED → 回补 → 事件×2 → MQ order.cancelled + refund.resolved")
    void adminCancelPaidOrder() {
        Order order = order(OrderStatus.PAID, LocalDateTime.now().minusHours(1), null);
        order.setRefundedAmount(new BigDecimal("37.00"));
        order.setProductionStage(ProductionStage.PENDING_REVIEW);
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(succeededPayment());
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.PAID), eq(OrderStatus.REFUNDING),
                isNull())).thenReturn(1);
        when(refundRepository.casApprove(any(), isNull())).thenReturn(1);
        when(orderRepository.addRefundedAmount(ORDER_ID, new BigDecimal("200.00"))).thenReturn(1);
        when(stripeClient.createRefund(eq("pi_1"), eq(20000L), anyString(), anyString()))
                .thenReturn(new StripeRefund("re_c", "succeeded", 20000L, "usd"));
        when(paymentRepository.applyRefund(eq(5L), any())).thenReturn(1);
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.REFUNDING), eq(OrderStatus.CANCELLED),
                any())).thenReturn(1);
        OrderLine spot = new OrderLine();
        spot.setSkuId(21L);
        spot.setQty(1);
        when(orderLineRepository.listSpotLines(ORDER_ID)).thenReturn(List.of(spot));

        Refund refund = service.adminCancelPaidOrder(order);

        assertThat(refund.getAmount()).isEqualByComparingTo("200.00");
        assertThat(refund.getFromStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(refund.getFromStage()).isEqualTo(ProductionStage.PENDING_REVIEW);
        verify(skuStockAdapter).restock(21L, 1);
        verify(orderEventRecorder).statusChanged(eq(ORDER_ID), eq(OrderStatus.PAID), eq(OrderStatus.CANCELLED),
                eq(OrderActorType.ADMIN), any(), any(), eq(true));
        verify(orderEventRecorder).record(eq(ORDER_ID), eq(OrderEventType.REFUND), eq(OrderActorType.ADMIN),
                any(), eq("Refund approved (admin cancel)"), any(), any(), eq(true));
        verify(eventsPublisher).publishOrderCancelled(eq(order), eq(TradingEventsPublisher.CANCEL_REASON_ADMIN));
        verify(eventsPublisher).publishRefundResolved(any(Refund.class), eq("DRM-20260610-0001"),
                eq("approved"), isNull());
    }

    @Test
    @DisplayName("STATE-7: adminCancelPaidOrder 首步 CAS PAID→REFUNDING affected=0（已有挂起工单）→ 409602，无任何账务动作")
    void adminCancelPaidOrderCasFails() {
        Order order = order(OrderStatus.PAID, LocalDateTime.now(), null);
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.PAID), eq(OrderStatus.REFUNDING),
                isNull())).thenReturn(0);
        assertThatThrownBy(() -> service.adminCancelPaidOrder(order))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_STATE_INVALID));
        verify(refundRepository, never()).insert(any());
        verify(stripeClient, never()).createRefund(anyString(), any(), anyString(), anyString());
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

    @Test
    @DisplayName("RM-TRD-02: 客户名/邮箱模糊 → user ids（API-TRD-03 listAdminOrders 搜索范围扩展，ALIGN-015）")
    void findUserIdsByNameOrEmailLike() {
        dreamy.identity.v1.ListUsersResponse resp = dreamy.identity.v1.ListUsersResponse.newBuilder()
                .addItems(dreamy.identity.v1.UserRecord.newBuilder().setId(7L))
                .build();
        when(identityGateClient.listUsers(any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(resp);
        assertThat(service.findUserIdsByNameOrEmailLike("Alice")).containsExactly(7L);
    }
}
