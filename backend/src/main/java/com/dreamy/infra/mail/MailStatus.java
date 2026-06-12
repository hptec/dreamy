package com.dreamy.infra.mail;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * MailRecord 发送状态机（acceptance mail_record 场景簇：pending→sent / pending→failed(retry_count+1)→dead）。
 * - pending：消费落表初始态（event_id 幂等占位）；
 * - sent：MailSender 发送成功（同写 sent_at）；
 * - failed：单次发送失败（retry_count+1，nack → 重试阶梯重入再尝试）；
 * - dead：超重试上限（dreamy.mq.max-retries=3）→ 死信语义，告警人工补发（FLOW-P11）。
 */
@Enumable
public enum MailStatus implements IntEnum, Describable {
    PENDING(1, "待发送"),
    SENT(2, "已发送"),
    FAILED(3, "发送失败"),
    DEAD(4, "死信");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    MailStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }
}
