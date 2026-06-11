package com.dreamy.marketing.domain.blog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * blog 阅读数近似计数器（DEC-MKT-6）：详情源站命中时 Redis INCR `marketing:blog:views:{id}`
 * （fire-and-forget，失败仅日志不阻塞读路径）；SCHED-MKT-02 每分钟 GETDEL flush 到 DB。
 * CDN/JetCache 命中不计数——views 为近似值（显式语义）。
 */
@Component
public class BlogViewsCounter {

    public static final String KEY_PREFIX = "marketing:blog:views:";

    private static final Logger log = LoggerFactory.getLogger(BlogViewsCounter.class);

    private final StringRedisTemplate redis;

    public BlogViewsCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** E-MKT-03 STEP-MKT-05：异步累加（fire-and-forget，DEC-MKT-6） */
    public void increment(Long blogPostId) {
        if (blogPostId == null) {
            return;
        }
        try {
            redis.opsForValue().increment(KEY_PREFIX + blogPostId);
        } catch (Exception ex) {
            log.warn("[DEC-MKT-6] views INCR failed blog_post_id={} (read path unaffected)", blogPostId);
        }
    }

    /** SCHED-MKT-02 ④ DB 写失败回投补偿（INCRBY delta） */
    public void compensate(Long blogPostId, long delta) {
        if (blogPostId == null || delta <= 0) {
            return;
        }
        try {
            redis.opsForValue().increment(KEY_PREFIX + blogPostId, delta);
        } catch (Exception ex) {
            log.warn("[SCHED-MKT-02] views compensate failed blog_post_id={} delta={}", blogPostId, delta);
        }
    }
}
