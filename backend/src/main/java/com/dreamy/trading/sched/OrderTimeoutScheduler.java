package com.dreamy.trading.sched;

import com.dreamy.trading.domain.order.entity.Order;
import com.dreamy.trading.domain.order.repository.OrderRepository;
import com.dreamy.trading.domain.order.service.OrderCancelService;
import com.dreamy.trading.mq.TradingEventsPublisher;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCHED-TRD-001 待支付订单超时取消回补（FLOW-P08，BE-DIM-4；TASK-052）。
 * 每分钟扫描；分布式锁 trading:order-timeout（huihao-redis Redisson，多实例单飞）；
 * RM-TRD-027 分页批量（limit=200）→ 逐单 TX-TRD-005 单单独立事务（一单失败不影响其余）。
 */
@Component
public class OrderTimeoutScheduler {

    public static final String LOCK_KEY = "trading:order-timeout";
    static final int BATCH_LIMIT = 200;

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);

    private final RedissonClient redissonClient;
    private final OrderRepository orderRepository;
    private final OrderCancelService orderCancelService;

    public OrderTimeoutScheduler(RedissonClient redissonClient, OrderRepository orderRepository,
                                 OrderCancelService orderCancelService) {
        this.redissonClient = redissonClient;
        this.orderRepository = orderRepository;
        this.orderCancelService = orderCancelService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void run() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            return;
        }
        try {
            cancelExpiredOrders();
        } catch (Exception ex) {
            log.error("[SCHED-TRD-001] order timeout sweep failed", ex);
        } finally {
            lock.unlock();
        }
    }

    /** 扫描 + 逐单取消（包级可见供单测/手动触发） */
    void cancelExpiredOrders() {
        List<Order> expired = orderRepository.listExpiredPending(LocalDateTime.now(), BATCH_LIMIT);
        if (expired.isEmpty()) {
            return;
        }
        int cancelled = 0;
        for (Order order : expired) {
            try {
                if (orderCancelService.cancelPending(order, TradingEventsPublisher.CANCEL_REASON_TIMEOUT)) {
                    cancelled++;
                }
            } catch (Exception ex) {
                // 单单事务：一单失败不阻塞批次（TC-TRD-032）
                log.error("[SCHED-TRD-001] cancel failed order_no={}", order.getOrderNo(), ex);
            }
        }
        log.info("[SCHED-TRD-001] expired pending swept: scanned={} cancelled={}", expired.size(), cancelled);
    }
}
