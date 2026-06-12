package com.dreamy.sched;

import com.dreamy.domain.blog.repository.BlogPostRepository;
import com.dreamy.domain.blog.service.BlogViewsCounter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * SCHED-MKT-02 blog views flush（DEC-MKT-6；每分钟）。
 * ① 分布式锁 `sched:blog-views`（防双跑）② SCAN `marketing:blog:views:*` → 逐 key GETDEL 取 delta
 * ③ TX-MKT-030：逐 id `incrementViews` 独立短事务（单 key 失败不影响其余）
 * ④ DB 写失败 INCRBY 回投补偿；不失效内容缓存（views 非缓存敏感字段，列表 TTL 自然收敛）。
 */
@Component
public class BlogViewsFlusher {

    public static final String LOCK_KEY = "sched:blog-views";

    private static final Logger log = LoggerFactory.getLogger(BlogViewsFlusher.class);

    private final RedissonClient redissonClient;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transactionTemplate;
    private final BlogPostRepository blogPostRepository;
    private final BlogViewsCounter viewsCounter;

    public BlogViewsFlusher(RedissonClient redissonClient, StringRedisTemplate redis,
                            TransactionTemplate transactionTemplate, BlogPostRepository blogPostRepository,
                            BlogViewsCounter viewsCounter) {
        this.redissonClient = redissonClient;
        this.redis = redis;
        this.transactionTemplate = transactionTemplate;
        this.blogPostRepository = blogPostRepository;
        this.viewsCounter = viewsCounter;
    }

    @Scheduled(cron = "30 * * * * *")
    public void flush() {
        // ① 分布式锁：拿不到直接跳过
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            return;
        }
        try {
            runFlush();
        } catch (Exception ex) {
            log.error("[SCHED-MKT-02] flush failed", ex);
        } finally {
            lock.unlock();
        }
    }

    void runFlush() {
        // ② SCAN（非 KEYS，生产安全游标遍历）
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions()
                .match(BlogViewsCounter.KEY_PREFIX + "*").count(200).build())) {
            cursor.forEachRemaining(keys::add);
        }
        for (String key : keys) {
            Long blogPostId = parseId(key);
            if (blogPostId == null) {
                continue;
            }
            // GETDEL：先取后清（取走的 delta 仅本实例持有）
            String raw;
            try {
                raw = redis.opsForValue().getAndDelete(key);
            } catch (Exception ex) {
                log.warn("[SCHED-MKT-02] GETDEL failed key={} (kept in Redis, next tick retries)", key);
                continue;
            }
            long delta = parseDelta(raw);
            if (delta <= 0) {
                continue;
            }
            // ③ TX-MKT-030 独立短事务；④ 失败回投补偿
            try {
                transactionTemplate.executeWithoutResult(tx ->
                        blogPostRepository.incrementViews(blogPostId, (int) Math.min(delta, Integer.MAX_VALUE)));
            } catch (Exception ex) {
                viewsCounter.compensate(blogPostId, delta);
                log.warn("[SCHED-MKT-02] DB flush failed blog_post_id={} delta={} (compensated to Redis)",
                        blogPostId, delta);
            }
        }
    }

    private Long parseId(String key) {
        try {
            return Long.parseLong(key.substring(BlogViewsCounter.KEY_PREFIX.length()));
        } catch (Exception ex) {
            return null;
        }
    }

    private long parseDelta(String raw) {
        if (raw == null) {
            return 0;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
