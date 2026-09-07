package com.dreamy.infra.mq;

/**
 * 领域事件发布端口（七域生产者统一入口：order.paid / order.shipped / order.cancelled /
 * refund.resolved / review.moderated / showroom.invite / showroom.remind）。
 * 发布失败不抛出（error-strategy 降级矩阵：publish 失败本地事务不回滚，失效链靠 JetCache 已失效
 * + CDN TTL 兜底；邮件/回写类记录告警日志补偿）——调用方应在事务提交后发布（EC-SHR-002 等）。
 */
public interface DomainEventPublisher {

    /**
     * 发布领域事件。
     *
     * @param routingKey 事件类型（topic routing key，如 order.paid）
     * @param payload    事件载荷（DTO/Map，序列化为 snake_case JSON）
     * @return 发布器生成的 event_id（UUID，消费幂等键）
     */
    String publish(String routingKey, Object payload);

    /**
     * 发布领域事件，失败时抛出（供事务性发件箱重投判定：outbox relay 依赖异常来标记 retry/DEAD）。
     * 缺省实现等价于 {@link #publish}（stub 模式进程内直调，消费异常已在内部吞掉）。
     */
    default String publishOrThrow(String routingKey, Object payload) {
        return publishOrThrow(routingKey, payload, null);
    }

    /**
     * 以指定 event_id 发布（事务性发件箱重投复用同一 id，消费端按 event_id 幂等）；eventId 为 null 时自动生成。
     */
    default String publishOrThrow(String routingKey, Object payload, String eventId) {
        return publish(routingKey, payload);
    }
}
