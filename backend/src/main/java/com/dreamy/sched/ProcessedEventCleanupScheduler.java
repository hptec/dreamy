package com.dreamy.sched;

import com.dreamy.domain.payment.repository.ProcessedEventRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * SCHED-TRD-002 processed_event 90 天清理（error-strategy webhook 安全第 2 条保留策略，强制项）。
 * 每日 04:30；分布式锁 trading:processed-event-cleanup；keyset 分批（每批 1000，CP-017）；删除量入日志。
 */
@Component
public class ProcessedEventCleanupScheduler {

    public static final String LOCK_KEY = "trading:processed-event-cleanup";
    static final int RETENTION_DAYS = 90;
    static final int BATCH_SIZE = 1000;

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventCleanupScheduler.class);

    private final RedissonClient redissonClient;
    private final ProcessedEventRepository processedEventRepository;

    public ProcessedEventCleanupScheduler(RedissonClient redissonClient,
                                          ProcessedEventRepository processedEventRepository) {
        this.redissonClient = redissonClient;
        this.processedEventRepository = processedEventRepository;
    }

    @Scheduled(cron = "0 30 4 * * *")
    public void run() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            return;
        }
        try {
            int deleted = processedEventRepository.deleteBefore(
                    LocalDateTime.now().minusDays(RETENTION_DAYS), BATCH_SIZE);
            log.info("[SCHED-TRD-002] processed_event cleanup deleted={}", deleted);
        } catch (Exception ex) {
            log.error("[SCHED-TRD-002] cleanup failed", ex);
        } finally {
            lock.unlock();
        }
    }
}
