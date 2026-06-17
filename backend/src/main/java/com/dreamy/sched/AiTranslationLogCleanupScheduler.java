package com.dreamy.sched;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.aitranslation.entity.AiTranslationLog;
import com.dreamy.domain.aitranslation.repository.AiTranslationLogMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * AI 翻译日志清理调度（决策7）。每日 03:00 删除 90 天前的 ai_translation_log。
 * 分批 DELETE ... LIMIT 5000 循环，避免大事务长锁；分布式锁 ai-translation:log-cleanup 防并发。
 */
@Component
public class AiTranslationLogCleanupScheduler {

    public static final String LOCK_KEY = "ai-translation:log-cleanup";
    static final int RETENTION_DAYS = 90;
    static final int BATCH_SIZE = 5000;
    private static final int MAX_BATCHES = 1000; // 安全上限，防异常死循环

    private static final Logger log = LoggerFactory.getLogger(AiTranslationLogCleanupScheduler.class);

    private final RedissonClient redissonClient;
    private final AiTranslationLogMapper logMapper;

    public AiTranslationLogCleanupScheduler(RedissonClient redissonClient,
                                            AiTranslationLogMapper logMapper) {
        this.redissonClient = redissonClient;
        this.logMapper = logMapper;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void run() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            return;
        }
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
            int total = 0;
            for (int batch = 0; batch < MAX_BATCHES; batch++) {
                LambdaQueryWrapper<AiTranslationLog> qw = new LambdaQueryWrapper<>();
                qw.lt(AiTranslationLog::getCreatedAt, cutoff).last("LIMIT " + BATCH_SIZE);
                int deleted = logMapper.delete(qw);
                total += deleted;
                if (deleted < BATCH_SIZE) {
                    break;
                }
            }
            log.info("[SCHED-AI-LOG] ai_translation_log cleanup deleted={} (cutoff={})", total, cutoff);
        } catch (Exception ex) {
            log.error("[SCHED-AI-LOG] cleanup failed", ex);
        } finally {
            lock.unlock();
        }
    }
}
