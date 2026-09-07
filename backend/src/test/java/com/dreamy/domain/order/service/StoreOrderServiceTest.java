package com.dreamy.domain.order.service;

import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.infra.stripe.StripePaymentIntent;
import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.domain.checkout.entity.CheckoutConfig;
import com.dreamy.enums.OrderStatus;
import com.dreamy.enums.PaymentStatus;
import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.entity.OrderLine;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.payment.entity.Payment;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.domain.refund.repository.RefundRepository;
import com.dreamy.dto.TradingDtos.PaymentCredential;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.testsupport.TradingImmediateTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.argThat;
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
    @Mock
    OrderEventRecorder orderEventRecorder;
    @Mock
    com.dreamy.domain.cart.service.StoreCartService storeCartService;
    @Mock
    com.dreamy.domain.shipment.service.ShipmentQueryService shipmentQueryService;

    StoreOrderService service;

    /** 初始化 MyBatis-Plus lambda 缓存（LambdaUpdateWrapper.set(Order::getX) 需 Order TableInfo）。 */
    @org.junit.jupiter.api.BeforeAll
    static void initMybatisPlusCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
                new org.apache.ibatis.session.Configuration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Order.class);
    }

    @BeforeEach
    void setUp() {
        service = new StoreOrderService(orderRepository, orderLineRepository, paymentRepository,
                refundRepository, checkoutConfigRepository, orderCancelService, stripeClient,
                orderEventRecorder, new TradingImmediateTxRunner(), storeCartService, shipmentQueryService);
        lenient().when(shipmentQueryService.listByOrder(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());
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

    // ==================== order-flow-complete B/I ====================

    @Test
    @DisplayName("confirm-delivery：SHIPPED→COMPLETED 同写 delivered_at + completed_at；order_event(STATUS_CHANGED, CUSTOMER, 可见)")
    void confirmDeliveryFromShipped() {
        Order shipped = order(OrderStatus.SHIPPED, null);
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(shipped);
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.SHIPPED), eq(OrderStatus.COMPLETED), any()))
                .thenAnswer(inv -> {
                    com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order> uw =
                            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
                    java.util.function.Consumer<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order>> c =
                            inv.getArgument(3);
                    c.accept(uw);
                    assertThat(uw.getSqlSet()).contains("completedAt").contains("deliveredAt");
                    return 1;
                });
        Order completed = order(OrderStatus.COMPLETED, null);
        when(orderRepository.findById(ORDER_ID)).thenReturn(completed);
        stubDetailDeps();

        var detail = service.confirmDelivery(CUSTOMER, ORDER_ID);

        assertThat(detail.status()).isEqualTo(OrderStatus.COMPLETED.getKey());
        verify(orderEventRecorder).statusChanged(eq(ORDER_ID), eq(OrderStatus.SHIPPED), eq(OrderStatus.COMPLETED),
                eq(com.dreamy.enums.OrderActorType.CUSTOMER), eq(CUSTOMER), any(), eq(true));
    }

    @Test
    @DisplayName("confirm-delivery：DELIVERED→COMPLETED 不再覆盖 delivered_at；PAID/COMPLETED → 409602；CAS 竞态 affected=0 → 409602")
    void confirmDeliveryGuards() {
        Order delivered = order(OrderStatus.DELIVERED, null);
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(delivered);
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.DELIVERED), eq(OrderStatus.COMPLETED), any()))
                .thenAnswer(inv -> {
                    com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order> uw =
                            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
                    java.util.function.Consumer<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order>> c =
                            inv.getArgument(3);
                    c.accept(uw);
                    assertThat(uw.getSqlSet()).contains("completedAt").doesNotContain("deliveredAt");
                    return 1;
                });
        when(orderRepository.findById(ORDER_ID)).thenReturn(order(OrderStatus.COMPLETED, null));
        stubDetailDeps();
        service.confirmDelivery(CUSTOMER, ORDER_ID);

        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(order(OrderStatus.PAID, null));
        assertThatThrownBy(() -> service.confirmDelivery(CUSTOMER, ORDER_ID))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_STATE_INVALID));
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(order(OrderStatus.COMPLETED, null));
        assertThatThrownBy(() -> service.confirmDelivery(CUSTOMER, ORDER_ID))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_STATE_INVALID));

        // STATE-5 竞态：自动完成先成功 → CAS affected=0 → 409602
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(order(OrderStatus.SHIPPED, null));
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.SHIPPED), eq(OrderStatus.COMPLETED), any()))
                .thenReturn(0);
        assertThatThrownBy(() -> service.confirmDelivery(CUSTOMER, ORDER_ID))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_STATE_INVALID));
    }

    @Test
    @DisplayName("reorder：逐行 addItem；缺货 409601 / 下架 404501 跳过并返回 skipped[reason_code]；added_count 统计")
    void reorderSkipsUnavailable() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(order(OrderStatus.COMPLETED, null));
        OrderLine ok = line(1L, 11L, 21L, 1);
        OrderLine oos = line(2L, 12L, 22L, 2);
        OrderLine gone = line(3L, 13L, null, 1);
        gone.setCustomSizeData(Map.of("bust", new BigDecimal("90"), "waist", new BigDecimal("70"),
                "hips", new BigDecimal("95"), "hollow_to_floor", new BigDecimal("150")));
        when(orderLineRepository.listByOrderId(ORDER_ID)).thenReturn(List.of(ok, oos, gone));
        when(storeCartService.addItem(eq(CUSTOMER), argThat(r -> r != null && r.skuId() != null && r.skuId() == 21L), eq("en")))
                .thenReturn(null);
        when(storeCartService.addItem(eq(CUSTOMER), argThat(r -> r != null && r.skuId() != null && r.skuId() == 22L), eq("en")))
                .thenThrow(new TradingException(TradingErrorCode.STOCK_INSUFFICIENT, Map.of("sku_id", 22L)));
        when(storeCartService.addItem(eq(CUSTOMER), argThat(r -> r != null && r.skuId() == null), eq("en")))
                .thenThrow(new com.dreamy.error.CatalogException(com.dreamy.error.CatalogErrorCode.PRODUCT_NOT_FOUND));

        var result = service.reorder(CUSTOMER, ORDER_ID, "en");

        assertThat(result.addedCount()).isEqualTo(1);
        assertThat(result.skipped()).extracting(s -> s.orderLineId()).containsExactly(2L, 3L);
        assertThat(result.skipped()).extracting(s -> s.reasonCode())
                .containsExactly(TradingErrorCode.STOCK_INSUFFICIENT.getCode(),
                        com.dreamy.error.CatalogErrorCode.PRODUCT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("详情装配：新增 production_stage/delivered_at/tax_amount/refunded_amount/amount_version/events(customer_visible)/shipments 空；refund_eligible 考虑剩余可退额")
    void detailAssemblyNewFields() {
        Order order = order(OrderStatus.PAID, null);
        order.setProductionStage(com.dreamy.enums.ProductionStage.IN_PRODUCTION);
        order.setAmountVersion(2);
        order.setTaxAmount(new BigDecimal("12.00"));
        order.setTaxBreakdown(List.of(Map.of("type", 1, "label", "VAT", "rate_scaled", 2000,
                "base", "60.00", "amount", 12.0)));
        order.setRefundedAmount(new BigDecimal("222.00"));
        order.setPaidAt(LocalDateTime.now());
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(order);
        stubDetailDeps();
        com.dreamy.dto.TradingDtos.OrderEventDto ev = new com.dreamy.dto.TradingDtos.OrderEventDto(1L, 4, 1, null,
                null, "Payment received", null, null, true, LocalDateTime.now());
        when(orderEventRecorder.listCustomerVisible(ORDER_ID)).thenReturn(List.of(ev));

        var detail = service.getOrderDetail(CUSTOMER, ORDER_ID);

        assertThat(detail.productionStage()).isEqualTo(2);
        assertThat(detail.amountVersion()).isEqualTo(2);
        assertThat(detail.taxAmount()).isEqualByComparingTo("12.00");
        assertThat(detail.taxBreakdown()).hasSize(1);
        assertThat(detail.taxBreakdown().get(0).label()).isEqualTo("VAT");
        assertThat(detail.taxBreakdown().get(0).base()).isEqualByComparingTo("60.00");
        assertThat(detail.taxBreakdown().get(0).amount()).isEqualByComparingTo("12.0");
        assertThat(detail.refundedAmount()).isEqualByComparingTo("222.00");
        // 剩余可退额 0 → refund_eligible=false（状态 PAID 本可退）
        assertThat(detail.refundEligible()).isFalse();
        assertThat(detail.events()).hasSize(1);
        assertThat(detail.shipments()).isEmpty();
        verify(orderEventRecorder, never()).listAdmin(anyLong());
    }

    private void stubDetailDeps() {
        lenient().when(orderLineRepository.listByOrderId(ORDER_ID)).thenReturn(List.of());
        lenient().when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(null);
        lenient().when(refundRepository.listByOrderId(ORDER_ID)).thenReturn(List.of());
        lenient().when(orderEventRecorder.listCustomerVisible(ORDER_ID)).thenReturn(List.of());
    }

    private static OrderLine line(long id, long productId, Long skuId, int qty) {
        OrderLine line = new OrderLine();
        line.setId(id);
        line.setOrderId(ORDER_ID);
        line.setProductId(productId);
        line.setSkuId(skuId);
        line.setQty(qty);
        line.setProductName("P" + productId);
        line.setUnitPrice(new BigDecimal("100.00"));
        return line;
    }
}
