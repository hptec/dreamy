package com.dreamy.aspect;

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

import java.util.concurrent.TimeUnit;

/**
 * Global Catalog admin write lock.
 *
 * <p>Catalog relations intentionally use logical foreign keys. Serializing their admin writers makes
 * a reference check and the following write one indivisible operation across application instances.
 * The highest precedence is intentional: the lock must wrap Spring's transaction interceptor so it is
 * released only after commit/rollback, never while another transaction can still observe old data.</p>
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CatalogAdminWriteAspect {

    private static final Logger log = LoggerFactory.getLogger(CatalogAdminWriteAspect.class);

    public static final String LOCK_KEY = "catalog:admin-write";
    static final long LOCK_WAIT_SECONDS = 15L;

    private final RedissonClient redissonClient;

    public CatalogAdminWriteAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(com.dreamy.aspect.CatalogAdminWrite)")
    public Object serialize(ProceedingJoinPoint joinPoint) throws Throwable {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean acquired;
        try {
            // No explicit lease: the watchdog keeps long transactions alive; finite wait avoids request-pool pile-up.
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        }
        if (!acquired) {
            throw new IllegalStateException("Timed out waiting for Catalog admin write lock");
        }
        try {
            return joinPoint.proceed();
        } finally {
            try {
                // Do not probe ownership first: that is another Redis round trip. If the probe fails,
                // Redisson never gets an unlock attempt and its watchdog can keep renewing the lock.
                // Calling unlock directly also lets Redisson cancel the renewal task on completion.
                lock.unlock();
            } catch (Exception ex) {
                // The transaction may already be committed. Never turn a successful write into an apparent failure,
                // otherwise clients can retry and duplicate a non-idempotent operation.
                log.error("[CATALOG-LOCK] unlock failed after Catalog write", ex);
            }
        }
    }
}
