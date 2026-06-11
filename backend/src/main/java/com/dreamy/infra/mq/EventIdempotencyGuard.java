package com.dreamy.infra.mq;

/**
 * 消费幂等闸端口（data-flow「消费幂等规范：全部消费者按 event_id 去重（processed_event 表 / Redis SETNX）」）。
 * 缺省实现 RedisEventIdempotencyGuard（SETNX TTL 7d，showroom data-detail §8.2 / trading §6 口径）；
 * webhook 专用 processed_event 表幂等由 trading 域在事务内自行落表（不经本闸）。
 */
public interface EventIdempotencyGuard {

    /** 首次消费返回 true 并占位；重复 event_id 返回 false（消费方 ack 空操作） */
    boolean tryAcquire(String queue, String eventId);

    /** 消费失败释放占位（允许重投重入，TC-SHR-054 口径） */
    void release(String queue, String eventId);
}
