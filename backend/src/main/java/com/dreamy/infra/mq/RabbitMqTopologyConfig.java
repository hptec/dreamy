package com.dreamy.infra.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * RabbitMQ 拓扑声明（dreamy.mq.mode=real；data-flow「MQ 事件拓扑」+ error-strategy L2 要求 3）：
 * - topic exchange `dreamy.events`（durable）；
 * - 业务队列（durable，x-dead-letter-exchange=dreamy.dlx）+ topic bindings：
 *   q.mail ← order.* / showroom.* / refund.resolved；q.showroom ← order.paid；
 *   q.catalog.sales ← order.paid；q.catalog.rating ← review.moderated；
 * - 重试队列 `dreamy.retry.{queue}`（durable，per-message TTL 阶梯，到期经 default exchange 回投主队列）；
 * - DLX `dreamy.dlx`（fanout）→ `dreamy.dlq`（告警 + 人工重放）。
 * 队列/绑定均由 dreamy.mq.queues 配置驱动，新增消费队列零代码改动。
 */
@Configuration
@ConditionalOnProperty(name = "dreamy.mq.mode", havingValue = "real")
public class RabbitMqTopologyConfig {

    @Bean
    public Declarables dreamyMqTopology(MqProperties props) {
        List<Declarable> declarables = new ArrayList<>();
        TopicExchange events = new TopicExchange(props.getExchange(), true, false);
        FanoutExchange dlx = new FanoutExchange(props.getDeadLetterExchange(), true, false);
        Queue dlq = QueueBuilder.durable(props.getDeadLetterQueue()).build();
        declarables.add(events);
        declarables.add(dlx);
        declarables.add(dlq);
        declarables.add(BindingBuilder.bind(dlq).to(dlx));

        props.getQueues().forEach((name, spec) -> {
            Queue queue = QueueBuilder.durable(name)
                    .withArgument("x-dead-letter-exchange", props.getDeadLetterExchange())
                    .build();
            declarables.add(queue);
            for (String bindingKey : spec.getBindingKeys()) {
                Binding binding = BindingBuilder.bind(queue).to(events).with(bindingKey);
                declarables.add(binding);
            }
            // 重试队列：per-message TTL（发布时按阶梯设置 expiration），到期回投主队列
            Queue retryQueue = QueueBuilder.durable(props.retryQueueName(name))
                    .withArgument("x-dead-letter-exchange", "")
                    .withArgument("x-dead-letter-routing-key", name)
                    .build();
            declarables.add(retryQueue);
        });
        return new Declarables(declarables);
    }

    @Bean
    public RabbitEventConsumerBootstrap rabbitEventConsumerBootstrap(ConnectionFactory connectionFactory,
                                                                     RabbitTemplate rabbitTemplate,
                                                                     ObjectMapper objectMapper,
                                                                     MqProperties props,
                                                                     ObjectProvider<DomainEventSubscriber> subscribers) {
        return new RabbitEventConsumerBootstrap(connectionFactory, rabbitTemplate, objectMapper, props,
                subscribers.stream().toList());
    }
}
