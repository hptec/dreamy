package com.dreamy.domain.order.service;

import com.dreamy.infra.stripe.StripeClient;
import com.dreamy.domain.coupon.service.CouponDomainService;
import com.dreamy.enums.OrderActorType;
import com.dreamy.enums.OrderStatus;
import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.entity.OrderLine;
import com.dreamy.domain.order.repository.OrderLineRepository;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.payment.entity.Payment;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.infra.TradingAfterCommitRunner;
import com.dreamy.infra.TradingTxRunner;
import com.dreamy.mq.TradingEventsPublisher;
import com.dreamy.port.SkuStockAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 待支付订单取消 + 回补共用事务体（TX-TRD-005，FLOW-P08）。
 * 调用方：cancelStoreOrder（customer）/ patchAdminOrderStatus cancelled（admin）/
 * SCHED-TRD-001 超时扫描（timeout）/ retryOrderPayment 内联超时取消。
 * 序列：① casUpdateStatus(pending→cancelled)（affected=0 → 与 webhook 竞态放弃）→ ② 现货行回补 →
 * ③ 券回滚 → ④ order_event(STATUS_CHANGED，actor 按 cancel_reason：timeout=SYSTEM / customer=CUSTOMER / admin=ADMIN)
 * → ⑤ MQ order.cancelled（outbox 事务内落表，提交后投递）→ COMMIT → 事务外 cancelPaymentIntent（失败仅告警，
 * 迟到支付由 TX-TRD-010 兜底）。
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
    private final OrderEventRecorder orderEventRecorder;

    public OrderCancelService(OrderRepository orderRepository, OrderLineRepository orderLineRepository,
                              PaymentRepository paymentRepository, SkuStockAdapter skuStockAdapter,
                              CouponDomainService couponDomainService, StripeClient stripeClient,
                              TradingTxRunner txRunner, TradingAfterCommitRunner afterCommit,
                              TradingEventsPublisher eventsPublisher, OrderEventRecorder orderEventRecorder) {
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.paymentRepository = paymentRepository;
        this.skuStockAdapter = skuStockAdapter;
        this.couponDomainService = couponDomainService;
        this.stripeClient = stripeClient;
        this.txRunner = txRunner;
        this.afterCommit = afterCommit;
        this.eventsPublisher = eventsPublisher;
        this.orderEventRecorder = orderEventRecorder;
    }

    /**
     * 单单事务取消（SCHED-TRD-001：一单失败不影响其余）。actor_id 按 cancel_reason 推断
     * （customer → order.customer_id；timeout → null；admin → 调用方应使用带 actorId 的重载）。
     *
     * @return true=本线程完成取消；false=guard 不命中（已被 webhook 推进或他处取消，放弃本单）
     */
    public boolean cancelPending(Order order, String cancelReason) {
        Long actorId = TradingEventsPublisher.CANCEL_REASON_CUSTOMER.equals(cancelReason)
                ? order.getCustomerId() : null;
        return cancelPending(order, cancelReason, actorId);
    }

    /** 带 actor_id 的取消（后台取消传操作者 admin_user.id） */
    public boolean cancelPending(Order order, String cancelReason, Long actorId) {
        OrderActorType actor = actorOf(cancelReason);
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
            // ④ §4.5：PENDING→CANCELLED → order_event(STATUS_CHANGED, actor, 可见)
            orderEventRecorder.statusChanged(order.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED,
                    actor, actorId, "cancel_reason=" + cancelReason, true);
            // ⑤ MQ order.cancelled（outbox 事务内落表，提交后投递）
            eventsPublisher.publishOrderCancelled(order, cancelReason);
            // COMMIT 后边界外动作（CP-031）
            afterCommit.run(() -> cancelPaymentIntentQuietly(order));
            return true;
        });
        return Boolean.TRUE.equals(cancelled);
    }

    /** cancel_reason → 触发者类型（§4.5 矩阵：CUSTOMER/SYSTEM/ADMIN） */
    static OrderActorType actorOf(String cancelReason) {
        if (TradingEventsPublisher.CANCEL_REASON_CUSTOMER.equals(cancelReason)) {
            return OrderActorType.CUSTOMER;
        }
        if (TradingEventsPublisher.CANCEL_REASON_ADMIN.equals(cancelReason)) {
            return OrderActorType.ADMIN;
        }
        return OrderActorType.SYSTEM;
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
