package com.dreamy.infra.mail;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 交易/Showroom 邮件类型（决策 16 三类订单邮件 + 决策 20.5 扩展枚举，FLOW-P11）。
 * 事件 routing key → 邮件类型映射（showroom-data-detail §8.1 / 161 定稿，向 q.mail 分册登记）：
 * - order.paid → order_confirmed；order.shipped → order_shipped；refund.resolved → refund_resolved；
 * - showroom.invite → showroom_invite；showroom.remind → showroom_assign（指派提醒语义，定稿映射）；
 * - showroom_remind 为决策 20.5 扩展枚举位（独立提醒类型预留，当前无事件映射）。
 * L3 修复轮 TRACE: FUNC-016/FUNC-019 / TC-TRD-070。
 */
public enum MailType implements StrEnum {
    ORDER_CONFIRMED("order_confirmed"),
    ORDER_SHIPPED("order_shipped"),
    REFUND_RESOLVED("refund_resolved"),
    SHOWROOM_INVITE("showroom_invite"),
    SHOWROOM_ASSIGN("showroom_assign"),
    SHOWROOM_REMIND("showroom_remind");

    @JsonValue
    @Getter
    private final String key;

    MailType(String key) {
        this.key = key;
    }

    /** 事件 type（routing key）→ 邮件类型；不属于邮件语义的事件（如 order.cancelled）返回 null（消费侧 ack 跳过） */
    public static MailType fromEventType(String eventType) {
        if (eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "order.paid" -> ORDER_CONFIRMED;
            case "order.shipped" -> ORDER_SHIPPED;
            case "refund.resolved" -> REFUND_RESOLVED;
            case "showroom.invite" -> SHOWROOM_INVITE;
            // showroom-data-detail 161：`showroom.remind` → MailRecord.type=showroom_assign（域定稿）
            case "showroom.remind" -> SHOWROOM_ASSIGN;
            default -> null;
        };
    }

    /** showroom 系列类型：recipient 取 payload.email（订单系列取 customer_id 经 CustomerEmailPort 解析） */
    public boolean isShowroom() {
        return this == SHOWROOM_INVITE || this == SHOWROOM_ASSIGN || this == SHOWROOM_REMIND;
    }
}
