package com.dreamy.domain.payment.service;

import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.entity.OrderLine;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.order.service.OrderEventRecorder;
import com.dreamy.domain.payment.entity.Payment;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.domain.payment.repository.ProcessedEventRepository;
import com.dreamy.domain.refund.repository.RefundRepository;
import com.dreamy.enums.OrderActorType;
import com.dreamy.enums.OrderEventType;
import com.dreamy.enums.OrderStatus;
import com.dreamy.enums.PaymentStatus;
import com.dreamy.enums.ProductionStage;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingAfterCommitRunner;
import com.dreamy.infra.TradingTxRunner;
import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.infra.stripe.StripeSignatureVerifier;
import com.dreamy.mq.TradingEventsPublisher;
import com.dreamy.support.Money;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stripe webhook 处理（FLOW-P07，决策 7/25；TASK-039/057；webhook 安全五条全落）：
 * 1. 验签失败 401601——不读取负载、不写任何业务数据、不落 processed_event（V-TRD-028/029）。
 * 2. event_id 幂等：INSERT processed_event uk_event_id，冲突 → 200 空操作；落表与业务变更同事务（TX-TRD-002 ①）。
 * 3. 金额/币种核对：不符 → 整体回滚（含 processed_event）+ 告警 + 计数 dreamy.webhook.mismatch.total，200 受理。
 * 4. 状态 guard：cancelled 收迟到 succeeded → 不复活订单，提交后自动全额退款补偿 + 告警（TX-TRD-010）。
 * 5. 仅 POST+JSON（控制器映射承载）；JWT 白名单豁免（application.yml store-public-paths）。
 * order-flow-complete §4.1：handle() = 验签 → 解析 → {@link #processVerified(JsonNode)}；stub 支付确认
 * （StubPaymentConfirmService，仅 stub 模式注册）直接调用包级私有 processVerified，复用幂等/核对/CAS/事件/MQ 全链。
 * 金额兼容（TAX-LEGACY）：核对比较的是 orders.total_amount（Money.toMinor 同一路径），v1 旧订单旧负载天然兼容。
 */
@Service
public class StripeWebhookService {

    public static final String TYPE_SUCCEEDED = "payment_intent.succeeded";
    public static final String TYPE_PAYMENT_FAILED = "payment_intent.payment_failed";
    public static final String TYPE_CHARGE_REFUNDED = "charge.refunded";

    public static final String METRIC_WEBHOOK_MISMATCH = "dreamy.webhook.mismatch.total";

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final StripeSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final RefundRepository refundRepository;
    private final StripeClient stripeClient;
    private final TradingTxRunner txRunner;
    private final TradingAfterCommitRunner afterCommit;
    private final TradingEventsPublisher eventsPublisher;
    private final OrderEventRecorder orderEventRecorder;
    private final MeterRegistry meterRegistry;

    public StripeWebhookService(StripeSignatureVerifier signatureVerifier, ObjectMapper objectMapper,
                                ProcessedEventRepository processedEventRepository,
                                PaymentRepository paymentRepository, OrderRepository orderRepository,
                                OrderLineRepository orderLineRepository, RefundRepository refundRepository,
                                StripeClient stripeClient, TradingTxRunner txRunner,
                                TradingAfterCommitRunner afterCommit, TradingEventsPublisher eventsPublisher,
                                OrderEventRecorder orderEventRecorder, MeterRegistry meterRegistry) {
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
        this.processedEventRepository = processedEventRepository;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.refundRepository = refundRepository;
        this.stripeClient = stripeClient;
        this.txRunner = txRunner;
        this.afterCommit = afterCommit;
        this.eventsPublisher = eventsPublisher;
        this.orderEventRecorder = orderEventRecorder;
        this.meterRegistry = meterRegistry;
    }

    /** 金额/币种核对不符信号（TX-TRD-002 ②：整体回滚含 processed_event，外层吞掉返回 200 受理） */
    private static final class AmountMismatch extends RuntimeException {
        private AmountMismatch() {
            super("webhook amount/currency mismatch");
        }
    }

    /** E-stripeWebhook 入口（rawBody 原文验签——签名按字节负载计算，先于任何解析） */
    public void handle(String rawBody, String signatureHeader) {
        // V-TRD-028 验签（失败：不读负载、不写库、脱敏告警；Stripe 退避重投）
        if (!signatureVerifier.verify(rawBody, signatureHeader)) {
            log.warn("[WEBHOOK] signature verification failed (payload/signature [REDACTED])");
            throw new TradingException(TradingErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
        // V-TRD-029 解析失败同等拒绝
        JsonNode event;
        try {
            event = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new TradingException(TradingErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
        processVerified(event);
    }

    /**
     * 已验签事件处理体（order-flow-complete §4.1 第 4 步；包级私有——仅本包 StubPaymentConfirmService 可调）。
     * 缺 id/type → 401601 同等拒绝；TX-TRD-002：processed_event 幂等闸 + 业务变更同事务。
     */
    void processVerified(JsonNode event) {
        String eventId = event == null ? null : event.path("id").asText(null);
        String eventType = event == null ? null : event.path("type").asText(null);
        if (eventId == null || eventId.isBlank() || eventType == null || eventType.isBlank()) {
            throw new TradingException(TradingErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
        try {
            txRunner.inTx(() -> {
                // STEP-TRD-01 幂等闸（冲突 → 已消费，200 空操作）
                if (processedEventRepository.insertIgnore(eventId, eventType) == 0) {
                    log.info("[WEBHOOK] duplicate event_id={} skipped (idempotent no-op)", eventId);
                    return;
                }
                // STEP-TRD-02 按 type 分支（未识别类型仅落 processed_event）
                switch (eventType) {
                    case TYPE_SUCCEEDED -> handleSucceeded(event);
                    case TYPE_PAYMENT_FAILED -> handlePaymentFailed(event);
                    case TYPE_CHARGE_REFUNDED -> handleChargeRefunded(event);
                    default -> log.info("[WEBHOOK] unhandled type={} event_id={} (accepted, no-op)",
                            eventType, eventId);
                }
            });
        } catch (AmountMismatch mismatch) {
            // 安全第 3 条：不变更订单、整体回滚（含 processed_event）、告警人工介入、200 受理
            meterRegistry.counter(METRIC_WEBHOOK_MISMATCH, "type", eventType).increment();
            log.error("[WEBHOOK][ALERT] amount/currency mismatch event_id={} type={} —— 人工介入复核", eventId, eventType);
        }
        // 其余业务异常向上抛 → 500（Stripe 重投；processed_event 同滚保证可重入——STEP-TRD-06）
    }

    /** STEP-TRD-03 payment_intent.succeeded（TX-TRD-002 事务内） */
    private void handleSucceeded(JsonNode event) {
        JsonNode object = event.path("data").path("object");
        String paymentIntentId = object.path("id").asText(null);
        Payment payment = paymentIntentId == null ? null : paymentRepository.findByPaymentIntentId(paymentIntentId);
        Order order = payment == null ? null : orderRepository.findById(payment.getOrderId());
        if (payment == null || order == null) {
            // 1. 无匹配 → 告警日志，200 受理（processed_event 保留，防重复告警风暴）
            log.warn("[WEBHOOK][ALERT] succeeded event without matching payment, payment_intent={}", paymentIntentId);
            return;
        }
        // 2. 金额/币种核对（安全第 3 条，决策 14 连带；TAX-LEGACY：比较 orders.total_amount，v1/v2 同路径）
        long eventAmount = object.path("amount").asLong(Long.MIN_VALUE);
        String eventCurrency = object.path("currency").asText("");
        if (eventAmount != Money.toMinor(order.getTotalAmount())
                || !order.getCurrency().equalsIgnoreCase(eventCurrency)) {
            throw new AmountMismatch();
        }
        // 3. 状态 guard（安全第 4 条）
        if (order.getStatus() == OrderStatus.PENDING) {
            LocalDateTime now = LocalDateTime.now();
            // STATE-2：PENDING→PAID 时 production_stage 置 PENDING_REVIEW(1)
            if (orderRepository.casUpdateStatus(order.getId(), OrderStatus.PENDING, OrderStatus.PAID,
                    uw -> uw.set(Order::getPaidAt, now)
                            .set(Order::getProductionStage, ProductionStage.PENDING_REVIEW)) == 0) {
                // 并发竞态：重读后按当前态处理（已 paid 幂等 / 已 cancelled 走补偿）
                Order reloaded = orderRepository.findById(order.getId());
                if (reloaded != null && reloaded.getStatus() == OrderStatus.CANCELLED) {
                    compensateLatePayment(reloaded, payment);
                }
                return;
            }
            String cardSummary = extractCardSummary(object);
            paymentRepository.casUpdateStatus(payment.getId(),
                    List.of(PaymentStatus.CREATED, PaymentStatus.PROCESSING), PaymentStatus.SUCCEEDED,
                    now, cardSummary);
            // §4.5：PENDING→PAID → order_event(PAYMENT, SYSTEM, 可见)
            Map<String, Object> eventPayload = new LinkedHashMap<>();
            eventPayload.put("from", OrderStatus.PENDING.getKey());
            eventPayload.put("to", OrderStatus.PAID.getKey());
            eventPayload.put("payment_intent_id", paymentIntentId);
            eventPayload.put("amount", order.getTotalAmount());
            eventPayload.put("currency", order.getCurrency());
            if (cardSummary != null) {
                eventPayload.put("card_summary", cardSummary);
            }
            orderEventRecorder.record(order.getId(), OrderEventType.PAYMENT, OrderActorType.SYSTEM, null,
                    "Payment received", cardSummary, eventPayload, true);
            // 4. MQ order.paid（EVT-TRD-001 扇出：邮件/showroom/销量回写；locale 取 PI metadata；outbox 事务内落表）
            String locale = object.path("metadata").path("locale").asText(null);
            if (locale == null || locale.isBlank()) {
                locale = order.getLocaleSnapshot() != null ? order.getLocaleSnapshot() : "en";
            }
            Order paidOrder = order;
            paidOrder.setPaidAt(now);
            paidOrder.setStatus(OrderStatus.PAID);
            paidOrder.setProductionStage(ProductionStage.PENDING_REVIEW);
            List<OrderLine> lines = orderLineRepository.listByOrderId(order.getId());
            eventsPublisher.publishOrderPaid(paidOrder, lines, locale);
            return;
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            // 迟到支付（TX-TRD-010）：不复活订单，提交后自动全额退款补偿 + 告警
            compensateLatePayment(order, payment);
            return;
        }
        // 已 paid/后续态：重复事件幂等跳过
        log.info("[WEBHOOK] succeeded on order status={} order_no={} —— 幂等跳过", order.getStatus(), order.getOrderNo());
    }

    /** TX-TRD-010 迟到支付补偿（订单不复活；资金回退非业务退款，不生成 Refund 工单）；§4.5 事件不可见 */
    private void compensateLatePayment(Order order, Payment payment) {
        log.error("[WEBHOOK][ALERT] late succeeded on cancelled order order_no={} —— 自动全额退款补偿，人工核对",
                order.getOrderNo());
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("payment_intent_id", payment.getPaymentIntentId());
        eventPayload.put("amount", order.getTotalAmount());
        eventPayload.put("currency", order.getCurrency());
        orderEventRecorder.record(order.getId(), OrderEventType.PAYMENT, OrderActorType.SYSTEM, null,
                "Late payment on cancelled order", "auto refund", eventPayload, false);
        afterCommit.run(() -> {
            try {
                stripeClient.createRefund(payment.getPaymentIntentId(), null, "order_cancelled_late_payment",
                        "late-payment:" + payment.getPaymentIntentId());
            } catch (Exception ex) {
                log.error("[WEBHOOK][ALERT] late payment auto-refund failed order_no={} —— 人工介入",
                        order.getOrderNo(), ex);
            }
        });
    }

    /** STEP-TRD-04 payment_intent.payment_failed（订单保持 pending 可重试；BNPL 异步拒绝同路）；§4.5 事件可见 */
    private void handlePaymentFailed(JsonNode event) {
        JsonNode object = event.path("data").path("object");
        String paymentIntentId = object.path("id").asText(null);
        Payment payment = paymentIntentId == null ? null : paymentRepository.findByPaymentIntentId(paymentIntentId);
        if (payment == null) {
            log.warn("[WEBHOOK] payment_failed without matching payment, payment_intent={}", paymentIntentId);
            return;
        }
        paymentRepository.casUpdateStatus(payment.getId(),
                List.of(PaymentStatus.CREATED, PaymentStatus.PROCESSING), PaymentStatus.FAILED, null, null);
        String reason = object.path("last_payment_error").path("message").asText(null);
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("payment_intent_id", paymentIntentId);
        if (reason != null) {
            eventPayload.put("reason", reason);
        }
        orderEventRecorder.record(payment.getOrderId(), OrderEventType.PAYMENT, OrderActorType.SYSTEM, null,
                "Payment failed", reason, eventPayload, true);
    }

    /**
     * STEP-TRD-05 charge.refunded（order-flow-complete §2.2）：以 amount_refunded 与 Payment.refunded_amount
     * 对账；一致 → 幂等空操作；不一致仅 [ALERT] 告警，不改状态（审核路径 TX-TRD-003 为唯一账务推进者）。
     */
    private void handleChargeRefunded(JsonNode event) {
        JsonNode object = event.path("data").path("object");
        String paymentIntentId = object.path("payment_intent").asText(null);
        Payment payment = paymentIntentId == null ? null : paymentRepository.findByPaymentIntentId(paymentIntentId);
        Order order = payment == null ? null : orderRepository.findById(payment.getOrderId());
        if (payment == null || order == null) {
            log.warn("[WEBHOOK] charge.refunded without matching payment, payment_intent={}", paymentIntentId);
            return;
        }
        long eventRefunded = object.path("amount_refunded").asLong(Long.MIN_VALUE);
        BigDecimal recorded = payment.getRefundedAmount() == null ? BigDecimal.ZERO : payment.getRefundedAmount();
        long recordedMinor = Money.toMinor(recorded);
        boolean legacyConverged = eventRefunded == Long.MIN_VALUE
                && order.getStatus() == OrderStatus.REFUNDED
                && refundRepository.existsApprovedByOrderId(order.getId())
                && payment.getStatus() == PaymentStatus.REFUNDED;
        if (legacyConverged || (eventRefunded != Long.MIN_VALUE && eventRefunded == recordedMinor)) {
            // 审核路径已收敛：幂等空操作
            return;
        }
        log.error("[WEBHOOK][ALERT] charge.refunded 对账不一致 order_no={} order_status={} payment_status={} "
                        + "stripe_amount_refunded={} recorded_refunded_minor={} —— 仅告警，人工核对",
                order.getOrderNo(), order.getStatus(), payment.getStatus(), eventRefunded, recordedMinor);
    }

    /** card_summary 提取（best effort：charges.data[0].payment_method_details.card） */
    private String extractCardSummary(JsonNode object) {
        JsonNode card = object.path("charges").path("data").path(0).path("payment_method_details").path("card");
        if (card.isMissingNode() || card.path("last4").asText("").isEmpty()) {
            return null;
        }
        String brand = card.path("brand").asText("card");
        String capitalized = brand.isEmpty() ? "Card" : Character.toUpperCase(brand.charAt(0)) + brand.substring(1);
        return "Stripe · " + capitalized + " ···" + card.path("last4").asText("");
    }
}
