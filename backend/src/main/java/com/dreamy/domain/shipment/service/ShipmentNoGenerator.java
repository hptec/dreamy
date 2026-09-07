package com.dreamy.domain.shipment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 包裹号发生器（参考 OrderNoGenerator）：SHP-yyyyMMdd-NNNN；Redis INCR trading:shipmentno:{yyyyMMdd} TTL 48h；
 * Redis 不可用降级 SecureRandom（uk_shipment_no 兜底，调用方冲突重取）。
 */
@Component
public class ShipmentNoGenerator {

    public static final String PREFIX = "SHP";

    private static final Logger log = LoggerFactory.getLogger(ShipmentNoGenerator.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public ShipmentNoGenerator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String next() {
        String dateSegment = LocalDate.now().format(DATE);
        long seq;
        try {
            String key = "trading:shipmentno:" + dateSegment;
            Long incremented = redis.opsForValue().increment(key);
            seq = incremented == null ? RANDOM.nextInt(10_000) : incremented;
            if (incremented != null && incremented == 1L) {
                redis.expire(key, Duration.ofHours(48));
            }
        } catch (Exception ex) {
            log.warn("[SHIPMENTNO] redis sequence unavailable, fallback to random", ex);
            seq = RANDOM.nextInt(10_000);
        }
        return PREFIX + "-" + dateSegment + "-" + String.format("%04d", seq % 10_000);
    }
}
