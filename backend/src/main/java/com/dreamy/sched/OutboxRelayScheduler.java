package com.dreamy.sched;

import com.dreamy.domain.outbox.entity.EventOutbox;
import com.dreamy.domain.outbox.repository.EventOutboxRepository;
import com.dreamy.mq.TradingEventsPublisher;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发件箱重投调度（order-flow-complete §4.4）：每分钟；Redisson 锁 trading:outbox-relay 多实例单飞；
 * 批 200 扫描 PENDING 且 next_attempt_at ≤ now 的行逐条重投（TradingEventsPublisher.deliver 负责
 * SENT / 退避 / DEAD 判定）；单条异常不阻塞批次。
 */
@Component
public class OutboxRelayScheduler {

    public static final String LOCK_KEY = "trading:outbox-relay";
    static final int BATCH_LIMIT = 200;

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final RedissonClient redissonClient;
    private final EventOutboxRepository outboxRepository;
    private final TradingEventsPublisher eventsPublisher;

    public OutboxRelayScheduler(RedissonClient redissonClient, EventOutboxRepository outboxRepository,
                                TradingEventsPublisher eventsPublisher) {
        this.redissonClient = redissonClient;
        this.outboxRepository = outboxRepository;
        this.eventsPublisher = eventsPublisher;
    }

    @Scheduled(cron = "30 * * * * *")
    public void run() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            return;
        }
        try {
            relayDue();
        } catch (Exception ex) {
            log.error("[OUTBOX-RELAY] sweep failed", ex);
        } finally {
            lock.unlock();
        }
    }

    /** 扫描 + 逐条重投（包级可见供单测/手动触发）；返回本轮成功投递数 */
    int relayDue() {
        List<EventOutbox> due = outboxRepository.listDue(LocalDateTime.now(), BATCH_LIMIT);
        if (due.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (EventOutbox row : due) {
            try {
                if (eventsPublisher.deliver(row)) {
                    sent++;
                }
            } catch (Exception ex) {
                log.error("[OUTBOX-RELAY] deliver crashed id={} key={}", row.getId(), row.getRoutingKey(), ex);
            }
        }
        log.info("[OUTBOX-RELAY] due={} sent={}", due.size(), sent);
        return sent;
    }
}
