package com.dreamy.domain.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 单号发生器（createOrder.STEP-TRD-04 / applyStoreRefund.STEP-TRD-05）：
 * `<PREFIX>-yyyyMMdd-NNNN`（CV-TRD-005 模式 ^DRM-\d{8}-\d{4}$ / ^RFD-\d{8}-\d{4}$）。
 * 序号源：Redis INCR `trading:orderno:{yyyyMMdd}` / `trading:refundno:{yyyyMMdd}` TTL 48h（CACHE-TRD-002）；
 * Redis 不可用降级 SecureRandom 4 位（TC-TRD-083：uk_order_no/uk_refund_no 唯一索引兜底，
 * 调用方冲突重取 ×3，不产生重号单据）。
 */
@Component
public class OrderNoGenerator {

    public static final String PREFIX_ORDER = "DRM";
    public static final String PREFIX_REFUND = "RFD";

    private static final Logger log = LoggerFactory.getLogger(OrderNoGenerator.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public OrderNoGenerator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 订单号 DRM-yyyyMMdd-NNNN */
    public String nextOrderNo() {
        return next(PREFIX_ORDER, "trading:orderno:");
    }

    /** 退款工单号 RFD-yyyyMMdd-NNNN */
    public String nextRefundNo() {
        return next(PREFIX_REFUND, "trading:refundno:");
    }

    private String next(String prefix, String keyspace) {
        LocalDate today = LocalDate.now();
        String dateSegment = today.format(DATE);
        long seq;
        try {
            String key = keyspace + dateSegment;
            Long incremented = redis.opsForValue().increment(key);
            seq = incremented == null ? randomSeq() : incremented;
            if (incremented != null && incremented == 1L) {
                redis.expire(key, Duration.ofHours(48));
            }
        } catch (Exception ex) {
            // 降级：随机序号 + 唯一索引兜底（TC-TRD-083）
            log.warn("[ORDERNO] redis sequence unavailable, fallback to random (uk 兜底重试)", ex);
            seq = randomSeq();
        }
        return format(prefix, dateSegment, seq);
    }

    private long randomSeq() {
        return RANDOM.nextInt(10_000);
    }

    /** 格式化（包级可见供单测断言模式，TC-TRD-012） */
    static String format(String prefix, String dateSegment, long seq) {
        return prefix + "-" + dateSegment + "-" + String.format("%04d", seq % 10_000);
    }
}
