package com.dreamy.sched;

import com.dreamy.config.MarketingProperties;
import com.dreamy.domain.banner.entity.Banner;
import com.dreamy.domain.banner.repository.BannerRepository;
import com.dreamy.domain.coupon.repository.CouponRepository;
import com.dreamy.domain.flashsale.repository.FlashSaleRepository;
import com.dreamy.infra.MarketingCacheService;
import com.dreamy.infra.MarketingCacheService.Family;
import com.dreamy.mq.MarketingContentInvalidatedPublisher;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SCHED-MKT-01 营销定时投放/到期下线（FLOW-P15，ALIGN-008/009，TASK-060；每分钟）。
 * ① huihao-redis(Redisson) 分布式锁 `sched:marketing-promo`（多实例防双跑，拿不到锁直接跳过本 tick）
 * ② TX-MKT-029：RM-MKT-109 coupon 翻转（scheduled→active→expiring→expired，DEC-MKT-3 阈值 72h 配置）
 *    + RM-MKT-126 flash 翻转（scheduled→active；active→ended 自动下线 s-761）任务内单事务
 * ③ 提交后：flash 任一翻转 → 失效 `marketing:flash:*` + MQ content.invalidated{type:flash_sale_changed}
 * ④ RM-MKT-008 banner 窗口穿越检测（lastTick 持久于 Redis `sched:marketing-promo:last-tick`）→
 *    任一穿越 → 失效 `marketing:banners:*` + MQ {type:banner_changed}（状态不翻转——DEC-MKT-2）
 * ⑤ coupon 翻转不发 MQ（无消费端缓存面）。
 * 时间谓词幂等：单 tick 失败由下一 tick 补偿推进（TC-MKT-025/075）。
 */
@Component
public class MarketingPromoScheduler {

    public static final String LOCK_KEY = "sched:marketing-promo";
    public static final String LAST_TICK_KEY = "sched:marketing-promo:last-tick";

    private static final Logger log = LoggerFactory.getLogger(MarketingPromoScheduler.class);

    private final RedissonClient redissonClient;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transactionTemplate;
    private final CouponRepository couponRepository;
    private final FlashSaleRepository flashSaleRepository;
    private final BannerRepository bannerRepository;
    private final MarketingCacheService cache;
    private final MarketingContentInvalidatedPublisher publisher;
    private final MarketingProperties properties;

    public MarketingPromoScheduler(RedissonClient redissonClient, StringRedisTemplate redis,
                                   TransactionTemplate transactionTemplate, CouponRepository couponRepository,
                                   FlashSaleRepository flashSaleRepository, BannerRepository bannerRepository,
                                   MarketingCacheService cache, MarketingContentInvalidatedPublisher publisher,
                                   MarketingProperties properties) {
        this.redissonClient = redissonClient;
        this.redis = redis;
        this.transactionTemplate = transactionTemplate;
        this.couponRepository = couponRepository;
        this.flashSaleRepository = flashSaleRepository;
        this.bannerRepository = bannerRepository;
        this.cache = cache;
        this.publisher = publisher;
        this.properties = properties;
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
            runTick(LocalDateTime.now());
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
        FlipOutcome outcome = transactionTemplate.execute(tx -> new FlipOutcome(
                couponRepository.flipStatusByWindow(now, expiringThreshold),
                flashSaleRepository.flipStatusByWindow(now)));
        if (outcome == null) {
            return;
        }
        // ⑤ coupon 翻转仅审计日志，不发 MQ（DEC-MKT-3）
        if (!outcome.couponIds().isEmpty()) {
            log.info("[SCHED-MKT-01] coupon status flipped ids={} (no MQ, DEC-MKT-3)", outcome.couponIds());
        }
        // ③ 提交后：flash 任一翻转 → 失效 + MQ
        if (outcome.flashResult().hasChanges()) {
            cache.invalidateFamily(Family.FLASH);
            publisher.publish(MarketingContentInvalidatedPublisher.TYPE_FLASH_SALE_CHANGED);
            log.info("[SCHED-MKT-01] flash flipped activated={} ended={} (s-761)",
                    outcome.flashResult().activated(), outcome.flashResult().ended());
        }
        // ④ banner 窗口穿越检测（状态不翻转——DEC-MKT-2）
        LocalDateTime lastTick = readLastTick(now);
        List<Banner> crossed = bannerRepository.listCrossedWindow(lastTick, now);
        if (!crossed.isEmpty()) {
            cache.invalidateFamily(Family.BANNERS);
            publisher.publish(MarketingContentInvalidatedPublisher.TYPE_BANNER_CHANGED);
            log.info("[SCHED-MKT-01] banner window crossed ids={} (status unchanged, DEC-MKT-2)",
                    crossed.stream().map(Banner::getId).toList());
        }
        writeLastTick(now);
    }

    private LocalDateTime readLastTick(LocalDateTime now) {
        try {
            String raw = redis.opsForValue().get(LAST_TICK_KEY);
            if (raw != null) {
                return LocalDateTime.parse(raw);
            }
        } catch (Exception ex) {
            log.warn("[SCHED-MKT-01] last-tick read failed, fallback now-1m");
        }
        return now.minusMinutes(1);
    }

    private void writeLastTick(LocalDateTime now) {
        try {
            redis.opsForValue().set(LAST_TICK_KEY, now.toString());
        } catch (Exception ex) {
            log.warn("[SCHED-MKT-01] last-tick write failed (next tick window widens, idempotent)");
        }
    }
}
