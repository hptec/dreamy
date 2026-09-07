package com.dreamy.domain.payment.service;

import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.order.service.StoreOrderService;
import com.dreamy.domain.payment.entity.Payment;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.dto.TradingDtos.StoreOrderDetail;
import com.dreamy.enums.OrderStatus;
import com.dreamy.enums.PaymentStatus;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.infra.stripe.StubStripeClient;
import com.dreamy.support.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * stub 支付确认（order-flow-complete §4.1 A）：仅 dreamy.stripe.mode=stub 注册（与控制器同条件；real 模式 Bean
 * 不存在 → 端点 404）。服务端合成 payment_intent.succeeded 事件走 {@link StripeWebhookService#processVerified}
 * 同一处理链（processed_event 幂等 / 金额核对 / CAS→PAID / order_event / outbox MQ 全部复用）。
 * 事件 id 确定性 `evt_stub_{payment_intent_id}`：重复/并发确认只有一次副作用，其余落幂等空操作后同样返回最新详情。
 * 金额/币种/locale 全部取自服务端 Order/Payment 记录，入参为空（不信任前端）。
 */
@Service
@ConditionalOnProperty(name = "dreamy.stripe.mode", havingValue = "stub", matchIfMissing = true)
public class StubPaymentConfirmService {

    public static final String EVENT_ID_PREFIX = "evt_stub_";
    public static final String METRIC_PAYMENT_CONFIRM = "dreamy.payment.confirm.total";

    private static final Logger log = LoggerFactory.getLogger(StubPaymentConfirmService.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final StripeWebhookService stripeWebhookService;
    private final StoreOrderService storeOrderService;
    private final StripeClient stripeClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public StubPaymentConfirmService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                                     StripeWebhookService stripeWebhookService, StoreOrderService storeOrderService,
                                     StripeClient stripeClient, ObjectMapper objectMapper,
                                     MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.stripeWebhookService = stripeWebhookService;
        this.storeOrderService = storeOrderService;
        this.stripeClient = stripeClient;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /** E-confirmStubPayment：POST /api/store/orders/{id}/payment/confirm（无请求体） */
    public StoreOrderDetail confirm(Long customerId, Long orderId) {
        // 归属校验（BE-DIM-6：跨用户/不存在 → 404601 防探测）
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId);
        if (order == null) {
            throw new TradingException(TradingErrorCode.ORDER_NOT_FOUND);
        }
        // 仅 PENDING 可确认（终防线为 webhook 链内 CAS；此处 js_guard → 409602）
        if (order.getStatus() != OrderStatus.PENDING) {
            throw TradingException.orderStateInvalid();
        }
        Payment payment = paymentRepository.findByOrderId(orderId);
        if (payment == null || payment.getPaymentIntentId() == null
                || (payment.getStatus() != PaymentStatus.CREATED && payment.getStatus() != PaymentStatus.PROCESSING)) {
            throw TradingException.orderStateInvalid();
        }
        String paymentIntentId = payment.getPaymentIntentId();
        ObjectNode event = buildSucceededEvent(order, paymentIntentId);
        stripeWebhookService.processVerified(event);
        meterRegistry.counter(METRIC_PAYMENT_CONFIRM, "mode", "stub").increment();
        // stub 内存表标记 PI succeeded（retryOrderPayment 重读时不再复用凭据）
        if (stripeClient instanceof StubStripeClient stub) {
            stub.markSucceeded(paymentIntentId);
        }
        log.info("[PAYMENT-CONFIRM][STUB] order_no={} payment_intent={} event_id={}",
                order.getOrderNo(), paymentIntentId, eventIdOf(paymentIntentId));
        return storeOrderService.getOrderDetail(customerId, orderId);
    }

    /** 事件 id 确定性：evt_stub_{payment_intent_id} */
    public static String eventIdOf(String paymentIntentId) {
        return EVENT_ID_PREFIX + paymentIntentId;
    }

    /** 完全由服务端记录构造 payment_intent.succeeded（§4.1 第 3 步；card 摘要 visa ···4242） */
    ObjectNode buildSucceededEvent(Order order, String paymentIntentId) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("id", eventIdOf(paymentIntentId));
        event.put("type", StripeWebhookService.TYPE_SUCCEEDED);
        ObjectNode object = event.putObject("data").putObject("object");
        object.put("id", paymentIntentId);
        object.put("object", "payment_intent");
        object.put("amount", Money.toMinor(order.getTotalAmount()));
        object.put("currency", order.getCurrency().toLowerCase());
        object.put("status", "succeeded");
        ObjectNode metadata = object.putObject("metadata");
        metadata.put("order_no", order.getOrderNo());
        metadata.put("order_id", String.valueOf(order.getId()));
        metadata.put("locale", order.getLocaleSnapshot() != null ? order.getLocaleSnapshot() : "en");
        ObjectNode card = object.putObject("charges").putArray("data").addObject()
                .putObject("payment_method_details").putObject("card");
        card.put("brand", "visa");
        card.put("last4", "4242");
        return event;
    }
}
