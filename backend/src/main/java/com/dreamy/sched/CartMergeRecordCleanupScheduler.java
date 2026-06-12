package com.dreamy.sched;

import com.dreamy.domain.cart.repository.CartMergeRecordRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * SCHED-TRD-003 cart_merge_record 滚动清理（设计派生：匿名 token 合并窗口远小于 30 天）。
 * 每日 04:40；分布式锁 trading:cart-merge-cleanup；删除 created_at < now-30d 记录。
 */
@Component
public class CartMergeRecordCleanupScheduler {

    public static final String LOCK_KEY = "trading:cart-merge-cleanup";
    static final int RETENTION_DAYS = 30;

    private static final Logger log = LoggerFactory.getLogger(CartMergeRecordCleanupScheduler.class);

    private final RedissonClient redissonClient;
    private final CartMergeRecordRepository cartMergeRecordRepository;

    public CartMergeRecordCleanupScheduler(RedissonClient redissonClient,
                                           CartMergeRecordRepository cartMergeRecordRepository) {
        this.redissonClient = redissonClient;
        this.cartMergeRecordRepository = cartMergeRecordRepository;
    }

    @Scheduled(cron = "0 40 4 * * *")
    public void run() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            return;
        }
        try {
            int deleted = cartMergeRecordRepository.deleteBefore(LocalDateTime.now().minusDays(RETENTION_DAYS));
            log.info("[SCHED-TRD-003] cart_merge_record cleanup deleted={}", deleted);
        } catch (Exception ex) {
            log.error("[SCHED-TRD-003] cleanup failed", ex);
        } finally {
            lock.unlock();
        }
    }
}
