package com.dreamy.trading.domain.order.service;

import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.marketing.domain.coupon.service.CouponDomainService;
import com.dreamy.trading.domain.enums.OrderStatus;
import com.dreamy.trading.domain.order.entity.Order;
import com.dreamy.trading.domain.order.entity.OrderLine;
import com.dreamy.trading.domain.order.repository.OrderLineRepository;
import com.dreamy.trading.domain.order.repository.OrderRepository;
import com.dreamy.trading.domain.payment.entity.Payment;
import com.dreamy.trading.domain.payment.repository.PaymentRepository;
import com.dreamy.trading.infra.TradingAfterCommitRunner;
import com.dreamy.trading.infra.TradingTxRunner;
import com.dreamy.trading.mq.TradingEventsPublisher;
import com.dreamy.trading.port.SkuStockAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 待支付订单取消 + 回补共用事务体（TX-TRD-005，FLOW-P08）。
 * 调用方：cancelStoreOrder（customer）/ patchAdminOrderStatus cancelled（admin）/
 * SCHED-TRD-001 超时扫描（timeout）/ retryOrderPayment 内联超时取消。
 * 序列：① casUpdateStatus(pending→cancelled)（affected=0 → 与 webhook 竞态放弃）→ ② 现货行回补 →
 * ③ 券回滚 → COMMIT → 事务外 cancelPaymentIntent（失败仅告警，迟到支付由 TX-TRD-010 兜底）+ MQ order.cancelled。
 */
@Service
public class OrderCancelService {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelService.class);

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final PaymentRepository paymentRepository;
    private final SkuStockAdapter skuStockAdapter;
    private final CouponDomainService couponDomainService;
    private final StripeClient stripeClient;
    private final TradingTxRunner txRunner;
    private final TradingAfterCommitRunner afterCommit;
    private final TradingEventsPublisher eventsPublisher;

    public OrderCancelService(OrderRepository orderRepository, OrderLineRepository orderLineRepository,
                              PaymentRepository paymentRepository, SkuStockAdapter skuStockAdapter,
                              CouponDomainService couponDomainService, StripeClient stripeClient,
                              TradingTxRunner txRunner, TradingAfterCommitRunner afterCommit,
                              TradingEventsPublisher eventsPublisher) {
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.paymentRepository = paymentRepository;
        this.skuStockAdapter = skuStockAdapter;
        this.couponDomainService = couponDomainService;
        this.stripeClient = stripeClient;
        this.txRunner = txRunner;
        this.afterCommit = afterCommit;
        this.eventsPublisher = eventsPublisher;
    }

    /**
     * 单单事务取消（SCHED-TRD-001：一单失败不影响其余）。
     *
     * @return true=本线程完成取消；false=guard 不命中（已被 webhook 推进或他处取消，放弃本单）
     */
    public boolean cancelPending(Order order, String cancelReason) {
        Boolean cancelled = txRunner.inTx(() -> {
            // ① 条件更新防与 webhook 竞态（TC-TRD-031）
            if (orderRepository.casUpdateStatus(order.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED, null) == 0) {
                return false;
            }
            // ② 现货行回补（定制行不回补，决策 6）
            for (OrderLine line : orderLineRepository.listSpotLines(order.getId())) {
                skuStockAdapter.restock(line.getSkuId(), line.getQty());
            }
            // ③ 已核销券回滚（RM-TRD-113，GREATEST 防负）
            if (order.getCouponId() != null) {
                couponDomainService.rollbackRedeem(order.getCouponId());
            }
            // COMMIT 后边界外动作（CP-031）
            afterCommit.run(() -> {
                cancelPaymentIntentQuietly(order);
                eventsPublisher.publishOrderCancelled(order, cancelReason);
            });
            return true;
        });
        return Boolean.TRUE.equals(cancelled);
    }

    /** 事务外作废 PaymentIntent（失败仅告警：webhook 幂等闸 + cancelled guard + 迟到支付自动退款兜底，TC-TRD-082） */
    private void cancelPaymentIntentQuietly(Order order) {
        try {
            Payment payment = paymentRepository.findByOrderId(order.getId());
            if (payment != null && payment.getPaymentIntentId() != null) {
                stripeClient.cancelPaymentIntent(payment.getPaymentIntentId());
            }
        } catch (Exception ex) {
            log.warn("[ORDER-CANCEL] cancelPaymentIntent failed order_no={} (webhook guard 兜底)",
                    order.getOrderNo(), ex);
        }
    }
}
