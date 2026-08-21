package com.dreamy.domain.blog.service;

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

    /** 读取 Redis 中尚未 flush 的增量（GET，失败按 0 兜底不影响读路径）。
     *  2026-08-21 新增：配合"详情接口实时叠加"，使消费端与 admin 看到的 views = DB + Redis。 */
    public long getDelta(Long blogPostId) {
        if (blogPostId == null) {
            return 0;
        }
        try {
            String value = redis.opsForValue().get(KEY_PREFIX + blogPostId);
            if (value == null || value.isBlank()) {
                return 0;
            }
            return Long.parseLong(value);
        } catch (Exception ex) {
            log.warn("[DEC-MKT-6] views GET failed blog_post_id={} (fallback to DB value)", blogPostId);
            return 0;
        }
    }

    /** 批量版 getDelta（admin 列表用，单次 MGET 避免 N 次 RTT） */
    public java.util.Map<Long, Long> getDeltas(java.util.Collection<Long> blogPostIds) {
        if (blogPostIds == null || blogPostIds.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.List<Long> ids = blogPostIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        try {
            java.util.List<String> keys = ids.stream().map(id -> KEY_PREFIX + id).toList();
            java.util.List<String> values = redis.opsForValue().multiGet(keys);
            java.util.Map<Long, Long> result = new java.util.HashMap<>();
            for (int i = 0; i < ids.size(); i++) {
                String v = values == null ? null : values.get(i);
                if (v != null && !v.isBlank()) {
                    try {
                        result.put(ids.get(i), Long.parseLong(v));
                    } catch (NumberFormatException ignored) {
                        // 脏数据按 0
                    }
                }
            }
            return result;
        } catch (Exception ex) {
            log.warn("[DEC-MKT-6] views MGET failed (fallback to DB value)");
            return java.util.Map.of();
        }
    }
}
