package com.dreamy.domain.payment.service;

import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.infra.stripe.StripeSignatureVerifier;
import com.dreamy.enums.OrderStatus;
import com.dreamy.enums.PaymentStatus;
import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.payment.entity.Payment;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.domain.payment.repository.ProcessedEventRepository;
import com.dreamy.domain.refund.repository.RefundRepository;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingAfterCommitRunner;
import com.dreamy.mq.TradingEventsPublisher;
import com.dreamy.domain.order.service.OrderEventRecorder;
import com.dreamy.testsupport.TradingImmediateTxRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Stripe webhook 处理单测（webhook 安全五条逻辑面；processed_event 同事务回滚由 IT 层验证）。
 * L2 TRACE: TC-TRD-064 [P0]（坏签名 401601 无任何 DB 写入；未识别 type 仅落 processed_event）/
 * TC-TRD-029 [P0]（重复 event_id 幂等空操作）/ TC-TRD-030 [P0]（金额不符不变更订单）/
 * TC-TRD-031/033 [P0/P1]（迟到 succeeded 不复活 + 自动退款补偿）/ TC-TRD-007（payment_failed 推进）。
 */
@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    private static final String SIG = "t=1,v1=ok";

    @Mock
    StripeSignatureVerifier signatureVerifier;
    @Mock
    ProcessedEventRepository processedEventRepository;
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    OrderRepository orderRepository;
    @Mock
    OrderLineRepository orderLineRepository;
    @Mock
    RefundRepository refundRepository;
    @Mock
    StripeClient stripeClient;
    @Mock
    TradingEventsPublisher eventsPublisher;
    @Mock
    OrderEventRecorder orderEventRecorder;

    SimpleMeterRegistry meterRegistry;
    StripeWebhookService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new StripeWebhookService(signatureVerifier, new ObjectMapper(), processedEventRepository,
                paymentRepository, orderRepository, orderLineRepository, refundRepository, stripeClient,
                new TradingImmediateTxRunner(), new TradingAfterCommitRunner(), eventsPublisher,
                orderEventRecorder, meterRegistry);
        lenient().when(signatureVerifier.verify(anyString(), anyString())).thenReturn(true);
        lenient().when(processedEventRepository.insertIgnore(anyString(), anyString())).thenReturn(1);
    }

    private String succeededEvent(long amountMinor, String currency) {
        return "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":"
                + "{\"id\":\"pi_1\",\"amount\":" + amountMinor + ",\"currency\":\"" + currency + "\","
                + "\"metadata\":{\"locale\":\"fr\"}}}}";
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(9L);
        payment.setOrderId(100L);
        payment.setPaymentIntentId("pi_1");
        payment.setStatus(status);
        payment.setAmount(new BigDecimal("222.00"));
        payment.setCurrency("USD");
        return payment;
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(100L);
        order.setOrderNo("DRM-20260610-0001");
        order.setCustomerId(7L);
        order.setStatus(status);
        order.setCurrency("USD");
        order.setTotalAmount(new BigDecimal("222.00"));
        return order;
    }

    @Test
    @DisplayName("TC-TRD-064 [P0]: 验签失败 → 401601；不读负载、不写任何业务数据、不落 processed_event")
    void invalidSignatureRejected() {
        when(signatureVerifier.verify(anyString(), anyString())).thenReturn(false);
        assertThatThrownBy(() -> service.handle("{}", SIG))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.WEBHOOK_SIGNATURE_INVALID));
        verifyNoInteractions(processedEventRepository, paymentRepository, orderRepository, eventsPublisher);
    }

    @Test
    @DisplayName("V-TRD-029: 验签通过但 JSON 解析失败/缺 id → 401601 同等拒绝，不入业务")
    void unreadablePayloadRejected() {
        assertThatThrownBy(() -> service.handle("not-json", SIG))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.WEBHOOK_SIGNATURE_INVALID));
        assertThatThrownBy(() -> service.handle("{\"type\":\"payment_intent.succeeded\"}", SIG))
                .isInstanceOf(TradingException.class);
        verifyNoInteractions(processedEventRepository);
    }

    @Test
    @DisplayName("TC-TRD-029 [P0]: 重复 event_id → 幂等空操作（不触达 payment/order）")
    void duplicateEventSkipped() {
        when(processedEventRepository.insertIgnore("evt_1", "payment_intent.succeeded")).thenReturn(0);
        service.handle(succeededEvent(22200L, "usd"), SIG);
        verifyNoInteractions(paymentRepository, orderRepository, eventsPublisher);
    }

    @Test
    @DisplayName("TC-TRD-064 [P0]: 正确签名未识别 type → 200 受理，仅落 processed_event")
    void unknownTypeAcceptedNoOp() {
        service.handle("{\"id\":\"evt_x\",\"type\":\"customer.created\",\"data\":{\"object\":{}}}", SIG);
        verify(processedEventRepository).insertIgnore("evt_x", "customer.created");
        verifyNoInteractions(paymentRepository, orderRepository, eventsPublisher);
    }

    @Test
    @DisplayName("FLOW-P07 happy: pending 订单 → paid + payment succeeded + 提交后 order.paid（locale 取 PI metadata）")
    void succeededHappyPath() {
        when(paymentRepository.findByPaymentIntentId("pi_1")).thenReturn(payment(PaymentStatus.CREATED));
        when(orderRepository.findById(100L)).thenReturn(order(OrderStatus.PENDING));
        when(orderRepository.casUpdateStatus(eq(100L), eq(OrderStatus.PENDING), eq(OrderStatus.PAID), any()))
                .thenReturn(1);
        when(orderLineRepository.listByOrderId(100L)).thenReturn(List.of());
        service.handle(succeededEvent(22200L, "usd"), SIG);
        verify(paymentRepository).casUpdateStatus(eq(9L),
                eq(List.of(PaymentStatus.CREATED, PaymentStatus.PROCESSING)), eq(PaymentStatus.SUCCEEDED),
                any(), isNull());
        verify(eventsPublisher).publishOrderPaid(any(Order.class), anyList(), eq("fr"));
        verify(stripeClient, never()).createRefund(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("TC-TRD-030 [P0]: 金额差 1 分 → 不变更订单（AmountMismatch 回滚信号被吞，200 受理）")
    void amountMismatchNoChange() {
        when(paymentRepository.findByPaymentIntentId("pi_1")).thenReturn(payment(PaymentStatus.CREATED));
        when(orderRepository.findById(100L)).thenReturn(order(OrderStatus.PENDING));
        service.handle(succeededEvent(22201L, "usd"), SIG);
        verify(orderRepository, never()).casUpdateStatus(anyLong(), any(), any(), any());
        verify(paymentRepository, never()).casUpdateStatus(anyLong(), anyList(), any(), any(), any());
        verify(eventsPublisher, never()).publishOrderPaid(any(), anyList(), anyString());
    }

    @Test
    @DisplayName("order-flow-complete §4.5: PENDING→PAID 写 production_stage=1 + order_event(PAYMENT, SYSTEM, 可见) + card 摘要")
    void succeededWritesStageAndEvent() {
        when(paymentRepository.findByPaymentIntentId("pi_1")).thenReturn(payment(PaymentStatus.CREATED));
        when(orderRepository.findById(100L)).thenReturn(order(OrderStatus.PENDING));
        when(orderRepository.casUpdateStatus(eq(100L), eq(OrderStatus.PENDING), eq(OrderStatus.PAID), any()))
                .thenReturn(1);
        when(orderLineRepository.listByOrderId(100L)).thenReturn(List.of());
        service.handle("{\"id\":\"evt_c\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":"
                + "{\"id\":\"pi_1\",\"amount\":22200,\"currency\":\"usd\",\"charges\":{\"data\":[{\"payment_method_details\":"
                + "{\"card\":{\"brand\":\"visa\",\"last4\":\"4242\"}}}]}}}}", SIG);
        verify(orderEventRecorder).record(eq(100L), eq(com.dreamy.enums.OrderEventType.PAYMENT),
                eq(com.dreamy.enums.OrderActorType.SYSTEM), isNull(), eq("Payment received"),
                eq("Stripe · Visa ···4242"), any(), eq(true));
        // locale 缺失 → 取 orders.locale_snapshot（null → en）
        verify(eventsPublisher).publishOrderPaid(argThat(o -> o.getProductionStage()
                == com.dreamy.enums.ProductionStage.PENDING_REVIEW && o.getStatus() == OrderStatus.PAID),
                anyList(), eq("en"));
    }

    @Test
    @DisplayName("TAX-LEGACY: v1 旧订单（amount_version=1, tax_amount=0）+ 旧 webhook 负载（无 charges/metadata）金额核对通过 → PAID")
    void legacyV1OrderOldPayloadAccepted() {
        Order legacy = order(OrderStatus.PENDING);
        legacy.setAmountVersion(1);
        legacy.setTaxAmount(BigDecimal.ZERO);
        when(paymentRepository.findByPaymentIntentId("pi_1")).thenReturn(payment(PaymentStatus.CREATED));
        when(orderRepository.findById(100L)).thenReturn(legacy);
        when(orderRepository.casUpdateStatus(eq(100L), eq(OrderStatus.PENDING), eq(OrderStatus.PAID), any()))
                .thenReturn(1);
        when(orderLineRepository.listByOrderId(100L)).thenReturn(List.of());
        service.handle("{\"id\":\"evt_legacy\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":"
                + "{\"id\":\"pi_1\",\"amount\":22200,\"currency\":\"usd\"}}}", SIG);
        verify(orderRepository).casUpdateStatus(eq(100L), eq(OrderStatus.PENDING), eq(OrderStatus.PAID), any());
        verify(eventsPublisher).publishOrderPaid(any(Order.class), anyList(), eq("en"));
        assertThat(meterRegistry.find(StripeWebhookService.METRIC_WEBHOOK_MISMATCH).counter()).isNull();
    }

    @Test
    @DisplayName("TC-TRD-030 (order-flow-complete): 金额不符 → 回滚信号 + 计数 dreamy.webhook.mismatch.total + 无 order_event")
    void amountMismatchCounted() {
        when(paymentRepository.findByPaymentIntentId("pi_1")).thenReturn(payment(PaymentStatus.CREATED));
        when(orderRepository.findById(100L)).thenReturn(order(OrderStatus.PENDING));
        service.handle(succeededEvent(22201L, "usd"), SIG);
        assertThat(meterRegistry.counter(StripeWebhookService.METRIC_WEBHOOK_MISMATCH, "type",
                "payment_intent.succeeded").count()).isEqualTo(1.0);
        verifyNoInteractions(orderEventRecorder);
    }

    @Test
    @DisplayName("重放同 event_id（幂等闸 affected=0）→ 第二次不触达业务、不重复发布")
    void replaySameEventId() {
        when(processedEventRepository.insertIgnore("evt_1", "payment_intent.succeeded")).thenReturn(1, 0);
        when(paymentRepository.findByPaymentIntentId("pi_1")).thenReturn(payment(PaymentStatus.CREATED));
        when(orderRepository.findById(100L)).thenReturn(order(OrderStatus.PENDING));
        when(orderRepository.casUpdateStatus(eq(100L), eq(OrderStatus.PENDING), eq(OrderStatus.PAID), any()))
                .thenReturn(1);
        when(orderLineRepository.listByOrderId(100L)).thenReturn(List.of());
        service.handle(succeededEvent(22200L, "usd"), SIG);
        service.handle(succeededEvent(22200L, "usd"), SIG);
        verify(orderRepository, times(1)).casUpdateStatus(anyLong(), any(), any(), any());
        verify(eventsPublisher, times(1)).publishOrderPaid(any(Order.class), anyList(), anyString());
    }

    @Test
    @DisplayName("TC-TRD-031 (order-flow-complete §4.5): 迟到支付补偿 → order_event(PAYMENT, SYSTEM, detail=auto refund, 不可见)")
    void lateSucceededWritesHiddenEvent() {
        when(paymentRepository.findByPaymentIntentId("pi_1")).thenReturn(payment(PaymentStatus.CREATED));
        when(orderRepository.findById(100L)).thenReturn(order(OrderStatus.CANCELLED));
        service.handle(succeededEvent(22200L, "usd"), SIG);
        verify(orderEventRecorder).record(eq(100L), eq(com.dreamy.enums.OrderEventType.PAYMENT),
                eq(com.dreamy.enums.OrderActorType.SYSTEM), isNull(), any(), eq("auto refund"), any(), eq(false));
    }

    @Test
    @DisplayName("STEP-TRD-04 (order-flow-complete §4.5): payment_failed → order_event(PAYMENT, SYSTEM, 可见) 含失败原因")
    void paymentFailedWritesEvent() {
        when(paymentRepository.findByPaymentIntentId("pi_1")).thenReturn(payment(PaymentStatus.PROCESSING));
        service.handle("{\"id\":\"evt_f\",\"type\":\"payment_intent.payment_failed\",\"data\":{\"object\":"
                + "{\"id\":\"pi_1\",\"last_payment_error\":{\"message\":\"card_declined\"}}}}", SIG);
        verify(orderEventRecorder).record(eq(100L), eq(com.dreamy.enums.OrderEventType.PAYMENT),
                eq(com.dreamy.enums.OrderActorType.SYSTEM), isNull(), eq("Payment failed"), eq("card_declined"),
                any(), eq(true));
    }

    @Test
    @DisplayName("TC-TRD-030 [P0]: 币种不符同等拒绝")
    void currencyMismatchNoChange() {
        when(paymentRepository.findByPaymentIntentId("pi_1")).thenReturn(payment(PaymentStatus.CREATED));
        when(orderRepository.findById(100L)).thenReturn(order(OrderStatus.PENDING));
        service.handle(succeededEvent(22200L, "eur"), SIG);
        verify(orderRepository, never()).casUpdateStatus(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-TRD-031/033 [P0/P1]: cancelled 订单收迟到 succeeded → 不复活，自动全额退款补偿")
    void lateSucceededCompensated() {
        when(paymentRepository.findByPaymentIntentId("pi_1")).thenReturn(payment(PaymentStatus.CREATED));
        when(orderRepository.findById(100L)).thenReturn(order(OrderStatus.CANCELLED));
        service.handle(succeededEvent(22200L, "usd"), SIG);
        verify(orderRepository, never()).casUpdateStatus(anyLong(), any(), any(), any());
        // TX-TRD-010：全额退款补偿（amount=null 全额）；不生成 Refund 工单、不发 order.paid
        verify(stripeClient).createRefund(eq("pi_1"), isNull(), anyString());
        verify(eventsPublisher, never()).publishOrderPaid(any(), anyList(), anyString());
    }

    @Test
    @DisplayName("STEP-TRD-04: payment_failed → payment failed（订单保持 pending 可重试）")
    void paymentFailed() {
        when(paymentRepository.findByPaymentIntentId("pi_1")).thenReturn(payment(PaymentStatus.PROCESSING));
        service.handle("{\"id\":\"evt_2\",\"type\":\"payment_intent.payment_failed\",\"data\":{\"object\":"
                + "{\"id\":\"pi_1\"}}}", SIG);
        verify(paymentRepository).casUpdateStatus(eq(9L),
                eq(List.of(PaymentStatus.CREATED, PaymentStatus.PROCESSING)), eq(PaymentStatus.FAILED),
                isNull(), isNull());
        verify(orderRepository, never()).casUpdateStatus(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("STEP-TRD-05: charge.refunded 与审核路径已收敛 → 幂等空操作（TC-TRD-081 不双退）")
    void chargeRefundedConverged() {
        Payment refunded = payment(PaymentStatus.REFUNDED);
        when(paymentRepository.findByPaymentIntentId("pi_1")).thenReturn(refunded);
        when(orderRepository.findById(100L)).thenReturn(order(OrderStatus.REFUNDED));
        when(refundRepository.existsApprovedByOrderId(100L)).thenReturn(true);
        service.handle("{\"id\":\"evt_3\",\"type\":\"charge.refunded\",\"data\":{\"object\":"
                + "{\"id\":\"ch_1\",\"payment_intent\":\"pi_1\"}}}", SIG);
        verify(paymentRepository, never()).casUpdateStatus(anyLong(), anyList(), any(), any(), any());
        verify(stripeClient, never()).createRefund(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("STEP-TRD-05 (order-flow-complete): charge.refunded amount_refunded 与 Payment.refunded_amount 不一致 → 仅告警，不改状态")
    void chargeRefundedReconciliationAlertOnly() {
        Payment p = payment(PaymentStatus.PARTIALLY_REFUNDED);
        p.setRefundedAmount(new BigDecimal("50.00"));
        when(paymentRepository.findByPaymentIntentId("pi_1")).thenReturn(p);
        when(orderRepository.findById(100L)).thenReturn(order(OrderStatus.PAID));
        service.handle("{\"id\":\"evt_4\",\"type\":\"charge.refunded\",\"data\":{\"object\":"
                + "{\"id\":\"ch_1\",\"payment_intent\":\"pi_1\",\"amount_refunded\":7000}}}", SIG);
        verify(paymentRepository, never()).casUpdateStatus(anyLong(), anyList(), any(), any(), any());
        verify(paymentRepository, never()).applyRefund(anyLong(), any());
        verify(orderRepository, never()).casUpdateStatus(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("STEP-TRD-05 (order-flow-complete): charge.refunded amount_refunded == Payment.refunded_amount → 幂等空操作")
    void chargeRefundedAmountConverged() {
        Payment p = payment(PaymentStatus.PARTIALLY_REFUNDED);
        p.setRefundedAmount(new BigDecimal("50.00"));
        when(paymentRepository.findByPaymentIntentId("pi_1")).thenReturn(p);
        when(orderRepository.findById(100L)).thenReturn(order(OrderStatus.PAID));
        service.handle("{\"id\":\"evt_5\",\"type\":\"charge.refunded\",\"data\":{\"object\":"
                + "{\"id\":\"ch_1\",\"payment_intent\":\"pi_1\",\"amount_refunded\":5000}}}", SIG);
        verify(paymentRepository, never()).casUpdateStatus(anyLong(), anyList(), any(), any(), any());
        verifyNoInteractions(refundRepository);
    }
}
