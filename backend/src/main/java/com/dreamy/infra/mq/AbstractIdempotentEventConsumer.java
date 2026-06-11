package com.dreamy.infra.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消费者抽象基类（BE-DIM-4 消费幂等 + nack 重试约定）：
 * - onEvent 模板：event_id 幂等闸（tryAcquire 失败 → 空操作 ack，幂等跳过）；
 * - handle 抛异常 → 释放幂等键（允许重投重入）并向上抛 → real 模式监听器按
 *   `dreamy.retry.{queue}` TTL 阶梯重试 ×maxRetries → 超限路由 dreamy.dlx → dreamy.dlq；
 * - 回写类消费操作应天然可重入（UPSERT/覆盖写，data-flow 消费幂等规范）。
 */
public abstract class AbstractIdempotentEventConsumer implements DomainEventSubscriber {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final EventIdempotencyGuard idempotencyGuard;

    protected AbstractIdempotentEventConsumer(EventIdempotencyGuard idempotencyGuard) {
        this.idempotencyGuard = idempotencyGuard;
    }

    @Override
    public final void onEvent(DomainEvent event) {
        if (event == null || event.eventId() == null) {
            log.warn("[MQ] queue={} dropped event without event_id", queue());
            return;
        }
        if (!idempotencyGuard.tryAcquire(queue(), event.eventId())) {
            log.info("[MQ] queue={} duplicate event_id={} skipped (idempotent no-op)", queue(), event.eventId());
            return;
        }
        try {
            handle(event);
        } catch (RuntimeException ex) {
            // 释放幂等键允许重放（TC-SHR-054：SETNX 键释放允许重放）
            idempotencyGuard.release(queue(), event.eventId());
            throw ex;
        }
    }

    /** 业务消费逻辑（幂等闸通过后调用一次） */
    protected abstract void handle(DomainEvent event);
}
