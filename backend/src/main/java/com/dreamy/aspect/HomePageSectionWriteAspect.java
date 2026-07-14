package com.dreamy.aspect;

import com.dreamy.error.SiteBuilderErrorCode;
import com.dreamy.error.SiteBuilderException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Holds one distributed homepage-section write lock around the complete transaction. */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HomePageSectionWriteAspect {

    private static final Logger log = LoggerFactory.getLogger(HomePageSectionWriteAspect.class);

    public static final String LOCK_KEY = "site-builder:home-sections:write";
    static final long LOCK_WAIT_SECONDS = 15L;

    private final RedissonClient redissonClient;

    public HomePageSectionWriteAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(com.dreamy.aspect.HomePageSectionWrite)")
    public Object serialize(ProceedingJoinPoint joinPoint) throws Throwable {
        RLock lock;
        boolean acquired;
        try {
            lock = redissonClient.getLock(LOCK_KEY);
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw lockConflict("interrupted while waiting for homepage write lock");
        } catch (RuntimeException ex) {
            log.error("[HOME-SECTION-LOCK] failed to acquire homepage section write lock", ex);
            throw SiteBuilderException.of(SiteBuilderErrorCode.SITE_BUILDER_INTERNAL_ERROR,
                    Map.of("reason", "homepage write lock unavailable"));
        }
        if (!acquired) {
            throw lockConflict("timed out waiting for homepage write lock");
        }

        try {
            return joinPoint.proceed();
        } finally {
            try {
                lock.unlock();
            } catch (Exception ex) {
                log.error("[HOME-SECTION-LOCK] unlock failed after homepage section write", ex);
            }
        }
    }

    private SiteBuilderException lockConflict(String reason) {
        return SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_SORT_CONFLICT,
                Map.of("reason", reason));
    }
}
