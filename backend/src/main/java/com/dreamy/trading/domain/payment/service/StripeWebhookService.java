package com.dreamy.trading.domain.payment.service;

import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.infra.stripe.StripeSignatureVerifier;
import com.dreamy.trading.domain.enums.OrderStatus;
import com.dreamy.trading.domain.enums.PaymentStatus;
import com.dreamy.trading.domain.order.entity.Order;
import com.dreamy.trading.domain.order.entity.OrderLine;
import com.dreamy.trading.domain.order.repository.OrderLineRepository;
import com.dreamy.trading.domain.order.repository.OrderRepository;
import com.dreamy.trading.domain.payment.entity.Payment;
import com.dreamy.trading.domain.payment.repository.PaymentRepository;
import com.dreamy.trading.domain.payment.repository.ProcessedEventRepository;
import com.dreamy.trading.domain.refund.repository.RefundRepository;
import com.dreamy.trading.error.TradingErrorCode;
import com.dreamy.trading.error.TradingException;
import com.dreamy.trading.infra.TradingAfterCommitRunner;
import com.dreamy.trading.infra.TradingTxRunner;
import com.dreamy.trading.mq.TradingEventsPublisher;
import com.dreamy.trading.support.Money;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Stripe webhook 处理（FLOW-P07，决策 7/25；TASK-039/057；webhook 安全五条全落）：
 * 1. 验签失败 401601——不读取负载、不写任何业务数据、不落 processed_event（V-TRD-028/029）。
 * 2. event_id 幂等：INSERT processed_event uk_event_id，冲突 → 200 空操作；落表与业务变更同事务（TX-TRD-002 ①）。
 * 3. 金额/币种核对：不符 → 整体回滚（含 processed_event）+ 告警，200 受理（允许 Stripe 重投复核）。
 * 4. 状态 guard：cancelled 收迟到 succeeded → 不复活订单，提交后自动全额退款补偿 + 告警（TX-TRD-010）。
 * 5. 仅 POST+JSON（控制器映射承载）；JWT 白名单豁免（application.yml store-public-paths）。
 */
@Service
public class StripeWebhookService {

    public static final String TYPE_SUCCEEDED = "payment_intent.succeeded";
    public static final String TYPE_PAYMENT_FAILED = "payment_intent.payment_failed";
    public static final String TYPE_CHARGE_REFUNDED = "charge.refunded";

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

    public StripeWebhookService(StripeSignatureVerifier signatureVerifier, ObjectMapper objectMapper,
                                ProcessedEventRepository processedEventRepository,
                                PaymentRepository paymentRepository, OrderRepository orderRepository,
                                OrderLineRepository orderLineRepository, RefundRepository refundRepository,
                                StripeClient stripeClient, TradingTxRunner txRunner,
                                TradingAfterCommitRunner afterCommit, TradingEventsPublisher eventsPublisher) {
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
        String eventId = event.path("id").asText(null);
        String eventType = event.path("type").asText(null);
        if (eventId == null || eventId.isBlank() || eventType == null || eventType.isBlank()) {
            throw new TradingException(TradingErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }

        try {
            // TX-TRD-002：processed_event 幂等闸 + 业务变更同事务
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
        // 2. 金额/币种核对（安全第 3 条，决策 14 连带）
        long eventAmount = object.path("amount").asLong(Long.MIN_VALUE);
        String eventCurrency = object.path("currency").asText("");
        if (eventAmount != Money.toMinor(order.getTotalAmount())
                || !order.getCurrency().equalsIgnoreCase(eventCurrency)) {
            throw new AmountMismatch();
        }
        // 3. 状态 guard（安全第 4 条）
        if (order.getStatus() == OrderStatus.PENDING) {
            LocalDateTime now = LocalDateTime.now();
            if (orderRepository.casUpdateStatus(order.getId(), OrderStatus.PENDING, OrderStatus.PAID,
                    uw -> uw.set(Order::getPaidAt, now)) == 0) {
                // 并发竞态：重读后按当前态处理（已 paid 幂等 / 已 cancelled 走补偿）
                Order reloaded = orderRepository.findById(order.getId());
                if (reloaded != null && reloaded.getStatus() == OrderStatus.CANCELLED) {
                    compensateLatePayment(reloaded, payment);
                }
                return;
            }
            paymentRepository.casUpdateStatus(payment.getId(),
                    List.of(PaymentStatus.CREATED, PaymentStatus.PROCESSING), PaymentStatus.SUCCEEDED,
                    now, extractCardSummary(object));
            // 4. 提交后 MQ order.paid（EVT-TRD-001 扇出：邮件/showroom/销量回写；locale 取 PI metadata）
            String locale = object.path("metadata").path("locale").asText("en");
            Order paidOrder = order;
            paidOrder.setPaidAt(now);
            List<OrderLine> lines = orderLineRepository.listByOrderId(order.getId());
            afterCommit.run(() -> eventsPublisher.publishOrderPaid(paidOrder, lines, locale));
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

    /** TX-TRD-010 迟到支付补偿（订单不复活；资金回退非业务退款，不生成 Refund 工单） */
    private void compensateLatePayment(Order order, Payment payment) {
        log.error("[WEBHOOK][ALERT] late succeeded on cancelled order order_no={} —— 自动全额退款补偿，人工核对",
                order.getOrderNo());
        afterCommit.run(() -> {
            try {
                stripeClient.createRefund(payment.getPaymentIntentId(), null, "order_cancelled_late_payment");
            } catch (Exception ex) {
                log.error("[WEBHOOK][ALERT] late payment auto-refund failed order_no={} —— 人工介入",
                        order.getOrderNo(), ex);
            }
        });
    }

    /** STEP-TRD-04 payment_intent.payment_failed（订单保持 pending 可重试；BNPL 异步拒绝同路） */
    private void handlePaymentFailed(JsonNode event) {
        String paymentIntentId = event.path("data").path("object").path("id").asText(null);
        Payment payment = paymentIntentId == null ? null : paymentRepository.findByPaymentIntentId(paymentIntentId);
        if (payment == null) {
            log.warn("[WEBHOOK] payment_failed without matching payment, payment_intent={}", paymentIntentId);
            return;
        }
        paymentRepository.casUpdateStatus(payment.getId(),
                List.of(PaymentStatus.CREATED, PaymentStatus.PROCESSING), PaymentStatus.FAILED, null, null);
    }

    /** STEP-TRD-05 charge.refunded（与 FLOW-P10 审核路径幂等汇合） */
    private void handleChargeRefunded(JsonNode event) {
        JsonNode object = event.path("data").path("object");
        String paymentIntentId = object.path("payment_intent").asText(null);
        Payment payment = paymentIntentId == null ? null : paymentRepository.findByPaymentIntentId(paymentIntentId);
        Order order = payment == null ? null : orderRepository.findById(payment.getOrderId());
        if (payment == null || order == null) {
            log.warn("[WEBHOOK] charge.refunded without matching payment, payment_intent={}", paymentIntentId);
            return;
        }
        boolean approvedAndRefunded = order.getStatus() == OrderStatus.REFUNDED
                && refundRepository.existsApprovedByOrderId(order.getId())
                && payment.getStatus() == PaymentStatus.REFUNDED;
        if (approvedAndRefunded) {
            // 审核路径已收敛：幂等空操作
            return;
        }
        // 异常态（如审核超时假失败后 Stripe 实际成功）：对账告警 + 推进 payment 终态（TC-TRD-081 不双退）
        log.error("[WEBHOOK][ALERT] charge.refunded 与审核路径未收敛 order_no={} order_status={} —— 对账告警",
                order.getOrderNo(), order.getStatus());
        paymentRepository.casUpdateStatus(payment.getId(),
                List.of(PaymentStatus.SUCCEEDED), PaymentStatus.REFUNDED, null, null);
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
