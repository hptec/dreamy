package com.dreamy.sched;

import com.dreamy.config.MarketingProperties;
import com.dreamy.domain.cache.service.CacheInvalidationPlans;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.domain.coupon.repository.CouponRepository;
import com.dreamy.domain.flashsale.repository.FlashSaleRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCHED-MKT-01 营销定时投放/到期下线（FLOW-P15，ALIGN-008/009，TASK-060；每分钟）。
 * ① huihao-redis(Redisson) 分布式锁 `sched:marketing-promo`（多实例防双跑，拿不到锁直接跳过本 tick）
 * ② TX-MKT-029：RM-MKT-109 coupon 翻转（scheduled→active→expiring→expired，DEC-MKT-3 阈值 72h 配置）
 *    + RM-MKT-126 flash 翻转（scheduled→active；active→ended 自动下线 s-761）任务内单事务
 * ③ flash 翻转与 durable cache task 同事务提交；Banner/Hero 边界由预创建的 cache task 执行。
 * ④ coupon 翻转不创建缓存任务（无消费端缓存面）。
 * 时间谓词幂等：单 tick 失败由下一 tick 补偿推进（TC-MKT-025/075）。
 */
@Component
public class MarketingPromoScheduler {

    public static final String LOCK_KEY = "sched:marketing-promo";

    private static final Logger log = LoggerFactory.getLogger(MarketingPromoScheduler.class);

    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;
    private final CouponRepository couponRepository;
    private final FlashSaleRepository flashSaleRepository;
    private final CacheInvalidationTaskService cacheTasks;
    private final MarketingProperties properties;
    private final Clock clock;

    public MarketingPromoScheduler(RedissonClient redissonClient,
                                   TransactionTemplate transactionTemplate, CouponRepository couponRepository,
                                   FlashSaleRepository flashSaleRepository, CacheInvalidationTaskService cacheTasks,
                                   MarketingProperties properties, Clock clock) {
        this.redissonClient = redissonClient;
        this.transactionTemplate = transactionTemplate;
        this.couponRepository = couponRepository;
        this.flashSaleRepository = flashSaleRepository;
        this.cacheTasks = cacheTasks;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        // ① 分布式锁：拿不到直接跳过本 tick（防双跑，TC-MKT-025）
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            log.debug("[SCHED-MKT-01] lock busy, tick skipped");
            return;
        }
        try {
            runTick(LocalDateTime.now(clock));
        } catch (Exception ex) {
            // 单 tick 异常不阻塞调度：时间谓词幂等，下一 tick 补偿推进（TC-MKT-075）
            log.error("[SCHED-MKT-01] tick failed (next tick will compensate)", ex);
        } finally {
            lock.unlock();
        }
    }

    void runTick(LocalDateTime now) {
        Duration expiringThreshold = Duration.ofHours(properties.getCouponExpiringHours());
        // ② TX-MKT-029：coupon + flash 翻转单事务
        record FlipOutcome(List<Long> couponIds, FlashSaleRepository.FlipResult flashResult) {
        }
        FlipOutcome outcome = transactionTemplate.execute(tx -> {
            List<Long> couponIds = couponRepository.flipStatusByWindow(now, expiringThreshold);
            FlashSaleRepository.FlipResult flashResult = flashSaleRepository.flipStatusByWindow(now);
            if (flashResult.hasChanges()) {
                cacheTasks.enqueue(CacheInvalidationTaskService.MODE_SCHEDULED, "flash_sale.window",
                        "flash_sale", "window:" + now, "闪购时间窗", CacheInvalidationPlans.FLASH,
                        now, Map.of("activated", flashResult.activated(), "ended", flashResult.ended()), "scheduler");
            }
            return new FlipOutcome(couponIds, flashResult);
        });
        if (outcome == null) {
            return;
        }
        // ⑤ coupon 翻转仅审计日志，不发 MQ（DEC-MKT-3）
        if (!outcome.couponIds().isEmpty()) {
            log.info("[SCHED-MKT-01] coupon status flipped ids={} (no MQ, DEC-MKT-3)", outcome.couponIds());
        }
        // ③ task 与状态翻转已同事务提交，此处只记录业务摘要。
        if (outcome.flashResult().hasChanges()) {
            log.info("[SCHED-MKT-01] flash flipped activated={} ended={} (s-761)",
                    outcome.flashResult().activated(), outcome.flashResult().ended());
        }
    }
}
