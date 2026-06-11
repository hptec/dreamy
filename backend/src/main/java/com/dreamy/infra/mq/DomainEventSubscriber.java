package com.dreamy.infra.mq;

import java.util.List;

/**
 * 领域事件订阅者契约（消费者抽象的 SPI）。
 * real 模式：每个订阅者 bean 绑定一个 RabbitMQ 监听容器（队列=queue()，manual ack，prefetch 按配置）；
 * stub 模式：StubDomainEventPublisher 按 bindingKeys topic 匹配同步直调 onEvent。
 * 业务消费者不直接实现本接口，应继承 AbstractIdempotentEventConsumer（event_id 幂等闸）。
 */
public interface DomainEventSubscriber {

    /** 消费队列名（须在 dreamy.mq.queues 登记，如 q.mail） */
    String queue();

    /** topic binding keys（与拓扑声明一致，如 [order.*, showroom.*, refund.resolved]） */
    List<String> bindingKeys();

    /** 消费入口；抛异常 → real 模式按重试阶梯 ×maxRetries → dreamy.dlq */
    void onEvent(DomainEvent event);
}
