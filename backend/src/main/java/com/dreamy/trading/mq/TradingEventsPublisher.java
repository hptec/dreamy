package com.dreamy.trading.mq;

import com.dreamy.infra.mq.DomainEventPublisher;
import com.dreamy.trading.domain.order.entity.Order;
import com.dreamy.trading.domain.order.entity.OrderLine;
import com.dreamy.trading.domain.refund.entity.Refund;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * trading 域领域事件发布器（EVT-TRD-001~005，topic exchange dreamy.events；TASK-057）。
 * 可靠性参数（trading-data-detail §6）：事务提交后发布（调用方经 TradingAfterCommitRunner）；
 * publish 失败不回滚本地事务，记告警日志（邮件/回写类人工补偿；缓存类靠 TTL 兜底）。
 * event_id 由 DomainEventPublisher 信封生成（消费幂等键）。
 */
@Component
public class TradingEventsPublisher {

    public static final String KEY_ORDER_PAID = "order.paid";
    public static final String KEY_ORDER_SHIPPED = "order.shipped";
    public static final String KEY_ORDER_CANCELLED = "order.cancelled";
    public static final String KEY_REFUND_RESOLVED = "refund.resolved";
    public static final String KEY_CONTENT_INVALIDATED = "content.invalidated";

    public static final String CANCEL_REASON_TIMEOUT = "timeout";
    public static final String CANCEL_REASON_CUSTOMER = "customer";
    public static final String CANCEL_REASON_ADMIN = "admin";

    private static final Logger log = LoggerFactory.getLogger(TradingEventsPublisher.class);

    private final DomainEventPublisher eventPublisher;

    public TradingEventsPublisher(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /** EVT-TRD-001 order.paid（TX-TRD-002 提交后；扇出 q.mail/q.showroom/q.catalog.sales） */
    public void publishOrderPaid(Order order, List<OrderLine> lines, String locale) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order_no", order.getOrderNo());
        payload.put("order_id", order.getId());
        payload.put("customer_id", order.getCustomerId());
        payload.put("locale", locale == null ? "en" : locale);
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

    /** EVT-TRD-002 order.shipped（TX-TRD-004a 提交后 → q.mail shipped 邮件） */
    public void publishOrderShipped(Order order, String locale) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order_no", order.getOrderNo());
        payload.put("customer_id", order.getCustomerId());
        payload.put("locale", locale == null ? "en" : locale);
        payload.put("carrier", order.getCarrier());
        payload.put("tracking_no", order.getTrackingNo());
        publish(KEY_ORDER_SHIPPED, payload);
    }

    /** EVT-TRD-003 order.cancelled（TX-TRD-005/004b 提交后；cancel_reason: timeout|customer|admin） */
    public void publishOrderCancelled(Order order, String cancelReason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order_no", order.getOrderNo());
        payload.put("customer_id", order.getCustomerId());
        payload.put("locale", "en");
        payload.put("cancel_reason", cancelReason);
        publish(KEY_ORDER_CANCELLED, payload);
    }

    /** EVT-TRD-004 refund.resolved（TX-TRD-003/009c 提交后 → q.mail refund_result 邮件） */
    public void publishRefundResolved(Refund refund, String orderNo, String result, String rejectReason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("refund_no", refund.getRefundNo());
        payload.put("order_no", orderNo);
        payload.put("customer_id", refund.getCustomerId());
        payload.put("locale", "en");
        payload.put("result", result);
        payload.put("amount", refund.getAmount());
        payload.put("currency", refund.getCurrency());
        if (rejectReason != null) {
            payload.put("reject_reason", rejectReason);
        }
        publish(KEY_REFUND_RESOLVED, payload);
    }

    /** EVT-TRD-005 content.invalidated（type=exchange_rates_updated，TX-TRD-011 提交后 → q.invalidate） */
    public void publishExchangeRatesInvalidated() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "exchange_rates_updated");
        payload.put("purge_paths", List.of("/api/store/exchange-rates"));
        publish(KEY_CONTENT_INVALIDATED, payload);
    }

    private void publish(String routingKey, Map<String, Object> payload) {
        try {
            eventPublisher.publish(routingKey, payload);
        } catch (Exception ex) {
            // 降级矩阵：publish 失败不回滚本地事务，告警日志人工补偿
            log.error("[EVT-TRD] publish failed key={} payload_keys={}", routingKey, payload.keySet(), ex);
        }
    }
}
