package com.dreamy.infra;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 游客查单频控（order-flow-complete §4.6：Redis 计数 key trading:track:{ip}，10 次/小时；沿用 OtpRateLimiter 模式）。
 * Redis 不可用时 fail-closed（拒绝）：查单为非关键只读功能，宁可短暂不可用也不允许无限枚举订单号。
 */
@Component
public class GuestTrackRateLimiter {

    public static final int LIMIT_PER_HOUR = 10;
    static final String KEY_PREFIX = "trading:track:";

    private final StringRedisTemplate redis;

    public GuestTrackRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 计数 +1；超过上限返回 false（第 11 次起） */
    public boolean tryAcquire(String ip) {
        String key = KEY_PREFIX + (ip == null || ip.isBlank() ? "unknown" : ip);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofHours(1));
            }
            return count == null || count <= LIMIT_PER_HOUR;
        } catch (Exception ex) {
            return false;
        }
    }
}
