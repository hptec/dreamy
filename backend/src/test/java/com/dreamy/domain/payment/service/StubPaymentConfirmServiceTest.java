package com.dreamy.domain.payment.service;

import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.order.service.OrderEventRecorder;
import com.dreamy.domain.order.service.StoreOrderService;
import com.dreamy.domain.payment.entity.Payment;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.domain.payment.repository.ProcessedEventRepository;
import com.dreamy.domain.refund.repository.RefundRepository;
import com.dreamy.dto.TradingDtos.StoreOrderDetail;
import com.dreamy.enums.OrderActorType;
import com.dreamy.enums.OrderEventType;
import com.dreamy.enums.OrderStatus;
import com.dreamy.enums.PaymentStatus;
import com.dreamy.enums.ProductionStage;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingAfterCommitRunner;
import com.dreamy.infra.stripe.StripeSignatureVerifier;
import com.dreamy.infra.stripe.StubStripeClient;
import com.dreamy.mq.TradingEventsPublisher;
import com.dreamy.testsupport.TradingImmediateTxRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * stub 支付确认单测（order-flow-complete §4.1 A / §6.1 第 2 条）：
 * 归属校验 404601 / 非 PENDING 409602 / Payment 非 CREATED|PROCESSING 409602 / 事件 id 确定性 /
 * 金额币种 locale 全取服务端 / 并发 10 线程仅 1 次 order.paid 发布 / production_stage=1 / order_event(PAYMENT)。
 * 真实 StripeWebhookService 组装（仅 mock 仓储），processed_event 幂等闸以内存 Set 模拟 uk_event_id。
 */
@ExtendWith(MockitoExtension.class)
class StubPaymentConfirmServiceTest {

    private static final long CUSTOMER = 7L;
    private static final long ORDER_ID = 100L;
    private static final String PI = "pi_stub_abc";

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
    TradingEventsPublisher eventsPublisher;
    @Mock
    OrderEventRecorder orderEventRecorder;
    @Mock
    StoreOrderService storeOrderService;

    StubStripeClient stubStripeClient;
    StripeWebhookService webhookService;
    StubPaymentConfirmService service;
    SimpleMeterRegistry meterRegistry;
    /** uk_event_id 模拟 */
    final Set<String> processed = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        stubStripeClient = new StubStripeClient();
        webhookService = new StripeWebhookService(signatureVerifier, new ObjectMapper(), processedEventRepository,
                paymentRepository, orderRepository, orderLineRepository, refundRepository, stubStripeClient,
                new TradingImmediateTxRunner(), new TradingAfterCommitRunner(), eventsPublisher,
                orderEventRecorder, meterRegistry);
        service = new StubPaymentConfirmService(orderRepository, paymentRepository, webhookService,
                storeOrderService, stubStripeClient, new ObjectMapper(), meterRegistry);
        lenient().when(processedEventRepository.insertIgnore(anyString(), anyString()))
                .thenAnswer(inv -> processed.add(inv.getArgument(0)) ? 1 : 0);
        lenient().when(orderLineRepository.listByOrderId(ORDER_ID)).thenReturn(List.of());
        lenient().when(storeOrderService.getOrderDetail(CUSTOMER, ORDER_ID))
                .thenReturn(detail(OrderStatus.PAID.getKey()));
    }

    private StoreOrderDetail detail(int status) {
        return new StoreOrderDetail(ORDER_ID, "DRM-1", status, "EUR", BigDecimal.ONE, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, List.of(), Map.of(), null,
                false, null, List.of(), ProductionStage.PENDING_REVIEW.getKey(), null, null, List.of(), null,
                null, 2, null, null, null, List.of(), List.of());
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("DRM-20260907-0001");
        order.setCustomerId(CUSTOMER);
        order.setStatus(status);
        order.setCurrency("EUR");
        order.setLocaleSnapshot("fr");
        order.setTotalAmount(new BigDecimal("1234.56"));
        order.setAmountVersion(2);
        return order;
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(9L);
        payment.setOrderId(ORDER_ID);
        payment.setPaymentIntentId(PI);
        payment.setStatus(status);
        payment.setAmount(new BigDecimal("1234.56"));
        payment.setCurrency("EUR");
        return payment;
    }

    @Test
    @DisplayName("归属校验：跨用户/不存在 → 404601，不触达 webhook 链")
    void ownershipRejected() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(null);
        assertThatThrownBy(() -> service.confirm(CUSTOMER, ORDER_ID))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_NOT_FOUND));
        verifyNoInteractions(processedEventRepository, eventsPublisher);
    }

    @Test
    @DisplayName("非 PENDING → 409602（PAID/CANCELLED 均拒绝）")
    void nonPendingRejected() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER))
                .thenReturn(order(OrderStatus.PAID), order(OrderStatus.CANCELLED));
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.confirm(CUSTOMER, ORDER_ID))
                    .isInstanceOfSatisfying(TradingException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_STATE_INVALID));
        }
        verifyNoInteractions(processedEventRepository, eventsPublisher);
    }

    @Test
    @DisplayName("Payment 缺失或状态 ∉ {CREATED, PROCESSING}（如 FAILED/SUCCEEDED）→ 409602")
    void paymentStateRejected() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(order(OrderStatus.PENDING));
        when(paymentRepository.findByOrderId(ORDER_ID))
                .thenReturn(null, payment(PaymentStatus.FAILED), payment(PaymentStatus.SUCCEEDED));
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.confirm(CUSTOMER, ORDER_ID))
                    .isInstanceOfSatisfying(TradingException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_STATE_INVALID));
        }
        verifyNoInteractions(processedEventRepository);
    }

    @Test
    @DisplayName("事件 id 确定性 evt_stub_{pi}；金额/币种/locale/card 全取服务端记录")
    void eventBuiltFromServerRecords() {
        ObjectNode event = service.buildSucceededEvent(order(OrderStatus.PENDING), PI);
        assertThat(event.path("id").asText()).isEqualTo("evt_stub_" + PI);
        assertThat(StubPaymentConfirmService.eventIdOf(PI)).isEqualTo("evt_stub_" + PI);
        assertThat(event.path("type").asText()).isEqualTo(StripeWebhookService.TYPE_SUCCEEDED);
        var object = event.path("data").path("object");
        assertThat(object.path("id").asText()).isEqualTo(PI);
        assertThat(object.path("amount").asLong()).isEqualTo(123456L);
        assertThat(object.path("currency").asText()).isEqualTo("eur");
        assertThat(object.path("metadata").path("locale").asText()).isEqualTo("fr");
        var card = object.path("charges").path("data").path(0).path("payment_method_details").path("card");
        assertThat(card.path("brand").asText()).isEqualTo("visa");
        assertThat(card.path("last4").asText()).isEqualTo("4242");
    }

    @Test
    @DisplayName("happy：PENDING→PAID（production_stage=1）+ payment SUCCEEDED（card visa ···4242）+ order_event(PAYMENT, SYSTEM, 可见) + order.paid(locale=fr) + 计数 + 返回详情 status=2")
    void confirmHappyPath() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(order(OrderStatus.PENDING));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(payment(PaymentStatus.CREATED));
        when(paymentRepository.findByPaymentIntentId(PI)).thenReturn(payment(PaymentStatus.CREATED));
        when(orderRepository.findById(ORDER_ID)).thenReturn(order(OrderStatus.PENDING));
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.PENDING), eq(OrderStatus.PAID), any()))
                .thenReturn(1);

        StoreOrderDetail result = service.confirm(CUSTOMER, ORDER_ID);

        assertThat(result.status()).isEqualTo(OrderStatus.PAID.getKey());
        verify(processedEventRepository).insertIgnore("evt_stub_" + PI, StripeWebhookService.TYPE_SUCCEEDED);
        verify(paymentRepository).casUpdateStatus(eq(9L), eq(List.of(PaymentStatus.CREATED, PaymentStatus.PROCESSING)),
                eq(PaymentStatus.SUCCEEDED), any(), eq("Stripe · Visa ···4242"));
        verify(orderEventRecorder).record(eq(ORDER_ID), eq(OrderEventType.PAYMENT), eq(OrderActorType.SYSTEM),
                isNull(), eq("Payment received"), eq("Stripe · Visa ···4242"), any(), eq(true));
        verify(eventsPublisher).publishOrderPaid(any(Order.class), anyList(), eq("fr"));
        assertThat(meterRegistry.counter(StubPaymentConfirmService.METRIC_PAYMENT_CONFIRM, "mode", "stub").count())
                .isEqualTo(1.0);
        // stub 内存表 PI 置 succeeded（不存在于内存表时 computeIfPresent 无操作；retrieve 回 succeeded）
        assertThat(stubStripeClient.retrievePaymentIntent(PI).status()).isEqualTo("succeeded");
    }

    @Test
    @DisplayName("重复确认（同 event_id）→ processed_event 幂等闸拦截，无第二次副作用，仍返回最新详情")
    void duplicateConfirmIdempotent() {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER))
                .thenReturn(order(OrderStatus.PENDING), order(OrderStatus.PENDING));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(payment(PaymentStatus.CREATED));
        when(paymentRepository.findByPaymentIntentId(PI)).thenReturn(payment(PaymentStatus.CREATED));
        when(orderRepository.findById(ORDER_ID)).thenReturn(order(OrderStatus.PENDING));
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.PENDING), eq(OrderStatus.PAID), any()))
                .thenReturn(1);

        service.confirm(CUSTOMER, ORDER_ID);
        service.confirm(CUSTOMER, ORDER_ID);

        verify(orderRepository, times(1)).casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.PENDING),
                eq(OrderStatus.PAID), any());
        verify(eventsPublisher, times(1)).publishOrderPaid(any(Order.class), anyList(), anyString());
    }

    @Test
    @DisplayName("并发 10 线程确认同一订单 → 仅 1 次 order.paid 发布 / 1 次 CAS（事件 id 确定性 + uk_event_id 闸）")
    void concurrentConfirmPublishesOnce() throws Exception {
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER))
                .thenAnswer(inv -> order(OrderStatus.PENDING));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenAnswer(inv -> payment(PaymentStatus.CREATED));
        when(paymentRepository.findByPaymentIntentId(PI)).thenAnswer(inv -> payment(PaymentStatus.CREATED));
        when(orderRepository.findById(ORDER_ID)).thenAnswer(inv -> order(OrderStatus.PENDING));
        AtomicInteger casCount = new AtomicInteger();
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.PENDING), eq(OrderStatus.PAID), any()))
                .thenAnswer(inv -> casCount.incrementAndGet() == 1 ? 1 : 0);

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    service.confirm(CUSTOMER, ORDER_ID);
                } catch (Throwable t) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(errors.get()).isZero();
        assertThat(processed).containsExactly("evt_stub_" + PI);
        verify(eventsPublisher, times(1)).publishOrderPaid(any(Order.class), anyList(), anyString());
        verify(orderRepository, times(1)).casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.PENDING),
                eq(OrderStatus.PAID), any());
        verify(storeOrderService, times(threads)).getOrderDetail(CUSTOMER, ORDER_ID);
    }

    @Test
    @DisplayName("retryOrderPayment 重建的新 PI（PROCESSING 态 Payment）同样可确认")
    void rebuiltIntentConfirmable() {
        Payment rebuilt = payment(PaymentStatus.PROCESSING);
        rebuilt.setPaymentIntentId("pi_stub_new");
        when(orderRepository.findByIdAndCustomerId(ORDER_ID, CUSTOMER)).thenReturn(order(OrderStatus.PENDING));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(rebuilt);
        when(paymentRepository.findByPaymentIntentId("pi_stub_new")).thenReturn(rebuilt);
        when(orderRepository.findById(ORDER_ID)).thenReturn(order(OrderStatus.PENDING));
        when(orderRepository.casUpdateStatus(eq(ORDER_ID), eq(OrderStatus.PENDING), eq(OrderStatus.PAID), any()))
                .thenReturn(1);
        service.confirm(CUSTOMER, ORDER_ID);
        verify(processedEventRepository).insertIgnore("evt_stub_pi_stub_new", StripeWebhookService.TYPE_SUCCEEDED);
        verify(eventsPublisher).publishOrderPaid(any(Order.class), anyList(), eq("fr"));
        verify(paymentRepository, never()).rebindPaymentIntent(anyLong(), anyString());
    }
}
