package com.dreamy.infra.mq;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis SETNX 幂等闸（showroom data-detail §8.2：`mq:consumed:{queue}:{event_id}` TTL 7d，
 * 已存在 → ack 空操作；消费失败 → 释放键 + 重投重入）。
 */
@Component
public class RedisEventIdempotencyGuard implements EventIdempotencyGuard {

    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    public RedisEventIdempotencyGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String queue, String eventId) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(queue, eventId), "1", TTL);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void release(String queue, String eventId) {
        redisTemplate.delete(key(queue, eventId));
    }

    private String key(String queue, String eventId) {
        return "mq:consumed:" + queue + ":" + eventId;
    }
}
