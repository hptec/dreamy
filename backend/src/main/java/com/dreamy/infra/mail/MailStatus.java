package com.dreamy.infra.mail;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * MailRecord 发送状态机（acceptance mail_record 场景簇：pending→sent / pending→failed(retry_count+1)→dead）。
 * - pending：消费落表初始态（event_id 幂等占位）；
 * - sent：MailSender 发送成功（同写 sent_at）；
 * - failed：单次发送失败（retry_count+1，nack → 重试阶梯重入再尝试）；
 * - dead：超重试上限（dreamy.mq.max-retries=3）→ 死信语义，告警人工补发（FLOW-P11）。
 */
public enum MailStatus implements StrEnum {
    PENDING("pending"),
    SENT("sent"),
    FAILED("failed"),
    DEAD("dead");

    @JsonValue
    @Getter
    private final String key;

    MailStatus(String key) {
        this.key = key;
    }
}
