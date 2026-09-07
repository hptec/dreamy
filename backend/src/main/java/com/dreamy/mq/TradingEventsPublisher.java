package com.dreamy.mq;

import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.entity.OrderLine;
import com.dreamy.domain.outbox.entity.EventOutbox;
import com.dreamy.domain.outbox.repository.EventOutboxRepository;
import com.dreamy.domain.refund.entity.Refund;
import com.dreamy.enums.OutboxStatus;
import com.dreamy.enums.ProductionStage;
import com.dreamy.infra.TradingAfterCommitRunner;
import com.dreamy.infra.mq.DomainEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * trading 域领域事件发布器（EVT-TRD-001~004 + order-flow-complete 新增 order.delivered / order.production /
 * refund.requested；topic exchange dreamy.events）。
 * 可靠性（order-flow-complete §4.4 Outbox）：publish* 在业务事务内落 event_outbox(PENDING) → afterCommit
 * 立即尝试投递 → 成功置 SENT；失败保留 PENDING + last_error，由 OutboxRelayScheduler 指数退避重投
 * （1m/5m/15m/1h，最多 {@link #MAX_ATTEMPTS} 次）→ DEAD + [ALERT]。
 * 无活动事务时（调度/单测）：先落表再即时投递。
 * event_id 由 DomainEventPublisher 信封生成（消费幂等键）。
 */
@Component
public class TradingEventsPublisher {

    public static final String KEY_ORDER_PAID = "order.paid";
    public static final String KEY_ORDER_SHIPPED = "order.shipped";
    public static final String KEY_ORDER_CANCELLED = "order.cancelled";
    public static final String KEY_ORDER_DELIVERED = "order.delivered";
    public static final String KEY_ORDER_PRODUCTION = "order.production";
    public static final String KEY_REFUND_RESOLVED = "refund.resolved";
    public static final String KEY_REFUND_REQUESTED = "refund.requested";

    public static final String CANCEL_REASON_TIMEOUT = "timeout";
    public static final String CANCEL_REASON_CUSTOMER = "customer";
    public static final String CANCEL_REASON_ADMIN = "admin";

    /** 最大投递尝试次数（首次 + 7 次重投）；超过 → DEAD */
    public static final int MAX_ATTEMPTS = 8;
    /** 退避阶梯（分钟）：1m/5m/15m/1h，之后固定 1h */
    static final long[] BACKOFF_MINUTES = {1, 5, 15, 60};

    public static final String METRIC_OUTBOX_DEAD = "dreamy.outbox.dead.total";

    private static final Logger log = LoggerFactory.getLogger(TradingEventsPublisher.class);

    private final DomainEventPublisher eventPublisher;
    private final EventOutboxRepository outboxRepository;
    private final TradingAfterCommitRunner afterCommit;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public TradingEventsPublisher(DomainEventPublisher eventPublisher, EventOutboxRepository outboxRepository,
                                  TradingAfterCommitRunner afterCommit, ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry) {
        this.eventPublisher = eventPublisher;
        this.outboxRepository = outboxRepository;
        this.afterCommit = afterCommit;
        this.objectMapper = objectMapper
                .copy()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.meterRegistry = meterRegistry;
    }

    /** EVT-TRD-001 order.paid（TX-TRD-002 事务内落 outbox；扇出 q.mail/q.showroom/q.catalog.sales） */
    public void publishOrderPaid(Order order, List<OrderLine> lines, String locale) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order_no", order.getOrderNo());
        payload.put("order_id", order.getId());
        payload.put("customer_id", order.getCustomerId());
        payload.put("locale", localeOf(locale, order));
        payload.put("currency", order.getCurrency());
        payload.put("total_amount", order.getTotalAmount());
        List<Map<String, Object>> lineItems = new ArrayList<>();
        if (lines != null) {
            for (OrderLine line : lines) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("product_id", line.getProductId());
                if (line.getSkuId() != null) {
                    item.put("sku_id", line.getSkuId());
                }
                item.put("qty", line.getQty());
                lineItems.add(item);
            }
        }
        payload.put("lines", lineItems);
        publish(KEY_ORDER_PAID, payload);
    }

    /** EVT-TRD-002 order.shipped（TX-TRD-004a 事务内 → q.mail shipped 邮件） */
    public void publishOrderShipped(Order order, String locale) {
        Map<String, Object> payload = basePayload(order, locale);
        payload.put("carrier", order.getCarrier());
        payload.put("tracking_no", order.getTrackingNo());
        publish(KEY_ORDER_SHIPPED, payload);
    }

    /** EVT-TRD-003 order.cancelled（TX-TRD-005/004b 事务内；cancel_reason: timeout|customer|admin） */
    public void publishOrderCancelled(Order order, String cancelReason) {
        Map<String, Object> payload = basePayload(order, null);
        payload.put("cancel_reason", cancelReason);
        publish(KEY_ORDER_CANCELLED, payload);
    }

    /** order.delivered（SHIPPED→DELIVERED 聚合 / 自动签收；P1-B 签收聚合调用同一入口） */
    public void publishOrderDelivered(Order order, String locale) {
        Map<String, Object> payload = basePayload(order, locale);
        payload.put("delivered_at", order.getDeliveredAt() == null ? null : order.getDeliveredAt().toString());
        publish(KEY_ORDER_DELIVERED, payload);
    }

    /** order.production（制作阶段进入 IN_PRODUCTION） */
    public void publishOrderProduction(Order order, ProductionStage stage, String locale) {
        Map<String, Object> payload = basePayload(order, locale);
        payload.put("production_stage", stage == null ? null : stage.getKey());
        payload.put("stage_name", stage == null ? null : stage.name().toLowerCase());
        publish(KEY_ORDER_PRODUCTION, payload);
    }

    /** EVT-TRD-004 refund.resolved（TX-TRD-003/009c 事务内 → q.mail refund_result 邮件） */
    public void publishRefundResolved(Refund refund, String orderNo, String result, String rejectReason) {
        Map<String, Object> payload = refundPayload(refund, orderNo);
        payload.put("result", result);
        if (rejectReason != null) {
            payload.put("reject_reason", rejectReason);
        }
        publish(KEY_REFUND_RESOLVED, payload);
    }

    /** refund.requested（退款申请/后台创建工单 → 受理邮件） */
    public void publishRefundRequested(Refund refund, String orderNo) {
        publish(KEY_REFUND_REQUESTED, refundPayload(refund, orderNo));
    }

    // ==================== Outbox ====================

    /**
     * 事务内落 outbox → afterCommit 即时投递。无活动事务时直接落表 + 投递。
     * 落表失败向上抛：事务性发件箱是投递保证的前提，宁可让业务事务回滚（webhook 由 Stripe 重投、
     * 后台操作报错重试）也不接受"事件静默丢失"。
     */
    void publish(String routingKey, Map<String, Object> payload) {
        EventOutbox row = new EventOutbox();
        row.setEventType(routingKey);
        row.setRoutingKey(routingKey);
        row.setPayload(toJson(payload));
        row.setStatus(OutboxStatus.PENDING);
        row.setAttempts(0);
        row.setNextAttemptAt(LocalDateTime.now());
        try {
            outboxRepository.insert(row);
        } catch (Exception ex) {
            log.error("[EVT-TRD][ALERT] outbox insert failed key={} —— 业务事务回滚", routingKey, ex);
            throw new IllegalStateException("event outbox insert failed: " + routingKey, ex);
        }
        afterCommit.run(() -> deliver(row, payload));
    }

    /**
     * 投递一条 outbox 行（afterCommit 即时投递与 relay 重投共用）。
     *
     * @return true=已投递（SENT）
     */
    public boolean deliver(EventOutbox row) {
        return deliver(row, null);
    }

    private boolean deliver(EventOutbox row, Map<String, Object> inMemoryPayload) {
        int attempts = (row.getAttempts() == null ? 0 : row.getAttempts()) + 1;
        try {
            Map<String, Object> payload = inMemoryPayload != null ? inMemoryPayload : fromJson(row.getPayload());
            eventPublisher.publishOrThrow(row.getRoutingKey(), payload);
            outboxRepository.markSent(row.getId(), attempts, LocalDateTime.now());
            row.setAttempts(attempts);
            row.setStatus(OutboxStatus.SENT);
            return true;
        } catch (Exception ex) {
            String error = truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage(), 255);
            if (attempts >= MAX_ATTEMPTS) {
                outboxRepository.markDead(row.getId(), attempts, error);
                row.setAttempts(attempts);
                row.setStatus(OutboxStatus.DEAD);
                meterRegistry.counter(METRIC_OUTBOX_DEAD, "routing_key", row.getRoutingKey()).increment();
                log.error("[EVT-TRD][ALERT] outbox DEAD id={} key={} attempts={} —— 需人工重放（status→PENDING）",
                        row.getId(), row.getRoutingKey(), attempts, ex);
            } else {
                LocalDateTime next = LocalDateTime.now().plus(backoff(attempts));
                outboxRepository.markRetry(row.getId(), attempts, next, error);
                row.setAttempts(attempts);
                log.warn("[EVT-TRD] outbox deliver failed id={} key={} attempts={} next={} ({})",
                        row.getId(), row.getRoutingKey(), attempts, next, error);
            }
            return false;
        }
    }

    /** 退避阶梯：第 n 次失败后等待 BACKOFF_MINUTES[min(n-1, last)] */
    static Duration backoff(int attempts) {
        int idx = Math.min(Math.max(attempts - 1, 0), BACKOFF_MINUTES.length - 1);
        return Duration.ofMinutes(BACKOFF_MINUTES[idx]);
    }


    private Map<String, Object> basePayload(Order order, String locale) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order_no", order.getOrderNo());
        payload.put("order_id", order.getId());
        payload.put("customer_id", order.getCustomerId());
        payload.put("locale", localeOf(locale, order));
        return payload;
    }

    private Map<String, Object> refundPayload(Refund refund, String orderNo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("refund_no", refund.getRefundNo());
        payload.put("order_no", orderNo);
        payload.put("order_id", refund.getOrderId());
        payload.put("customer_id", refund.getCustomerId());
        payload.put("locale", "en");
        payload.put("amount", refund.getAmount());
        payload.put("currency", refund.getCurrency());
        return payload;
    }

    private static String localeOf(String locale, Order order) {
        if (locale != null && !locale.isBlank()) {
            return locale;
        }
        return order.getLocaleSnapshot() != null ? order.getLocaleSnapshot() : "en";
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("outbox payload serialize failed", ex);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("outbox payload deserialize failed", ex);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
