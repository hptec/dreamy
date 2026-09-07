package com.dreamy.sched;

import com.dreamy.domain.checkout.entity.CheckoutConfig;
import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.order.service.OrderEventRecorder;
import com.dreamy.enums.OrderActorType;
import com.dreamy.enums.OrderStatus;
import com.dreamy.infra.TradingTxRunner;
import com.dreamy.mq.TradingEventsPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单自动签收 / 自动完成调度（order-flow-complete §4.2 B）：每小时；Redisson 锁 trading:order-autocomplete；
 * 批 200；逐单独立事务（一单失败不阻塞批次）：
 * - 规则一：DELIVERED 且 delivered_at + auto_complete_days ≤ now → CAS DELIVERED→COMPLETED（completed_at）；
 * - 规则二：SHIPPED 且 shipped_at + auto_deliver_days ≤ now 且无签收 → CAS SHIPPED→DELIVERED（delivered_at）+ MQ order.delivered。
 * 与用户确认收货互不阻塞（STATE-5：CAS affected=0 幂等跳过）。每条转换写 order_event(STATUS_CHANGED, SYSTEM, 可见)。
 * 计数：dreamy.order.autocomplete.total{rule=complete|deliver}。
 */
@Component
public class OrderAutoCompleteScheduler {

    public static final String LOCK_KEY = "trading:order-autocomplete";
    public static final String METRIC_AUTOCOMPLETE = "dreamy.order.autocomplete.total";
    static final int BATCH_LIMIT = 200;

    private static final Logger log = LoggerFactory.getLogger(OrderAutoCompleteScheduler.class);

    private final RedissonClient redissonClient;
    private final OrderRepository orderRepository;
    private final CheckoutConfigRepository checkoutConfigRepository;
    private final OrderEventRecorder orderEventRecorder;
    private final TradingEventsPublisher eventsPublisher;
    private final TradingTxRunner txRunner;
    private final MeterRegistry meterRegistry;

    public OrderAutoCompleteScheduler(RedissonClient redissonClient, OrderRepository orderRepository,
                                      CheckoutConfigRepository checkoutConfigRepository,
                                      OrderEventRecorder orderEventRecorder, TradingEventsPublisher eventsPublisher,
                                      TradingTxRunner txRunner, MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.orderRepository = orderRepository;
        this.checkoutConfigRepository = checkoutConfigRepository;
        this.orderEventRecorder = orderEventRecorder;
        this.eventsPublisher = eventsPublisher;
        this.txRunner = txRunner;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(cron = "0 15 * * * *")
    public void run() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            return;
        }
        try {
            sweep();
        } catch (Exception ex) {
            log.error("[SCHED-AUTOCOMPLETE] sweep failed", ex);
        } finally {
            lock.unlock();
        }
    }

    /** 两条规则各扫一批（包级可见供单测/手动触发）；返回 {completed, delivered} */
    int[] sweep() {
        CheckoutConfig config = checkoutConfigRepository.getSingleton();
        int completeDays = config.getAutoCompleteDays() == null ? 7 : config.getAutoCompleteDays();
        int deliverDays = config.getAutoDeliverDays() == null ? 30 : config.getAutoDeliverDays();
        LocalDateTime now = LocalDateTime.now();
        int completed = 0;
        int delivered = 0;
        // 规则一：delivered_at + auto_complete_days ≤ now → COMPLETED
        List<Order> toComplete = orderRepository.listDeliveredBefore(now.minusDays(completeDays), BATCH_LIMIT);
        for (Order order : toComplete) {
            try {
                if (autoComplete(order, now)) {
                    completed++;
                }
            } catch (Exception ex) {
                log.error("[SCHED-AUTOCOMPLETE] auto complete failed order_no={}", order.getOrderNo(), ex);
            }
        }
        // 规则二：shipped_at + auto_deliver_days ≤ now 且无签收 → DELIVERED
        List<Order> toDeliver = orderRepository.listShippedBefore(now.minusDays(deliverDays), BATCH_LIMIT);
        for (Order order : toDeliver) {
            try {
                if (autoDeliver(order, now)) {
                    delivered++;
                }
            } catch (Exception ex) {
                log.error("[SCHED-AUTOCOMPLETE] auto deliver failed order_no={}", order.getOrderNo(), ex);
            }
        }
        if (!toComplete.isEmpty() || !toDeliver.isEmpty()) {
            log.info("[SCHED-AUTOCOMPLETE] scanned complete={} delivered={} → completed={} delivered={}",
                    toComplete.size(), toDeliver.size(), completed, delivered);
        }
        return new int[]{completed, delivered};
    }

    /** 单单独立事务：DELIVERED→COMPLETED（affected=0 → 已被用户确认，幂等跳过） */
    boolean autoComplete(Order order, LocalDateTime now) {
        Boolean done = txRunner.inTx(() -> {
            if (orderRepository.casUpdateStatus(order.getId(), OrderStatus.DELIVERED, OrderStatus.COMPLETED,
                    uw -> uw.set(Order::getCompletedAt, now)) == 0) {
                return false;
            }
            orderEventRecorder.statusChanged(order.getId(), OrderStatus.DELIVERED, OrderStatus.COMPLETED,
                    OrderActorType.SYSTEM, null, "auto completed after delivery window", true);
            return true;
        });
        if (Boolean.TRUE.equals(done)) {
            meterRegistry.counter(METRIC_AUTOCOMPLETE, "rule", "complete").increment();
            return true;
        }
        return false;
    }

    /** 单单独立事务：SHIPPED→DELIVERED（affected=0 → 已签收/确认，幂等跳过）+ MQ order.delivered */
    boolean autoDeliver(Order order, LocalDateTime now) {
        Boolean done = txRunner.inTx(() -> {
            if (orderRepository.casUpdateStatus(order.getId(), OrderStatus.SHIPPED, OrderStatus.DELIVERED,
                    uw -> uw.set(Order::getDeliveredAt, now)) == 0) {
                return false;
            }
            orderEventRecorder.statusChanged(order.getId(), OrderStatus.SHIPPED, OrderStatus.DELIVERED,
                    OrderActorType.SYSTEM, null, "auto delivered after transit window", true);
            order.setStatus(OrderStatus.DELIVERED);
            order.setDeliveredAt(now);
            eventsPublisher.publishOrderDelivered(order, order.getLocaleSnapshot());
            return true;
        });
        if (Boolean.TRUE.equals(done)) {
            meterRegistry.counter(METRIC_AUTOCOMPLETE, "rule", "deliver").increment();
            return true;
        }
        return false;
    }
}
