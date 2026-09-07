package com.dreamy.sched;

import com.dreamy.domain.exchangerate.service.ExchangeRateRefreshService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 汇率每日刷新调度（order-flow-complete G；cron dreamy.exchange-rate.refresh-cron 缺省 03:05；
 * Redisson 锁 trading:exchange-rate-refresh 多实例单飞；manual 模式静默跳过；失败保留现值下一 cron 再试）。
 */
@Component
public class ExchangeRateRefreshScheduler {

    public static final String LOCK_KEY = "trading:exchange-rate-refresh";

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateRefreshScheduler.class);

    private final RedissonClient redissonClient;
    private final ExchangeRateRefreshService refreshService;

    public ExchangeRateRefreshScheduler(RedissonClient redissonClient, ExchangeRateRefreshService refreshService) {
        this.redissonClient = redissonClient;
        this.refreshService = refreshService;
    }

    @Scheduled(cron = "${dreamy.exchange-rate.refresh-cron:0 5 3 * * *}")
    public void run() {
        if (refreshService.isManualMode()) {
            return;
        }
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            return;
        }
        try {
            refreshService.refresh();
        } catch (Exception ex) {
            // 失败语义已在服务层记 [ALERT] + 计数；此处仅防止调度线程异常
            log.warn("[SCHED-EXRATE] refresh skipped: {}", ex.getMessage());
        } finally {
            lock.unlock();
        }
    }
}
