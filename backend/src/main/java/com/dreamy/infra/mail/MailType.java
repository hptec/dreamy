package com.dreamy.infra.mail;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 交易/Showroom 邮件类型（决策 16 三类订单邮件 + 决策 20.5 扩展枚举，FLOW-P11）。
 * 事件 routing key → 邮件类型映射（showroom-data-detail §8.1 / 161 定稿，向 q.mail 分册登记）：
 * - order.paid → order_confirmed；order.shipped → order_shipped；refund.resolved → refund_resolved；
 * - showroom.invite → showroom_invite；showroom.remind → showroom_assign（指派提醒语义，定稿映射）；
 * - showroom_remind 为决策 20.5 扩展枚举位（独立提醒类型预留，当前无事件映射）。
 * - order-flow-complete：order.cancelled → order_cancelled；order.delivered → order_delivered；
 *   order.production → order_production；refund.requested → refund_requested。
 * L3 修复轮 TRACE: FUNC-016/FUNC-019 / TC-TRD-070。
 */
@Enumable
public enum MailType implements IntEnum, Describable {
    ORDER_CONFIRMED(1, "订单确认"),
    ORDER_SHIPPED(2, "订单发货"),
    REFUND_RESOLVED(3, "退款处理结果"),
    SHOWROOM_INVITE(4, "样品间邀请"),
    SHOWROOM_ASSIGN(5, "样品间指派"),
    SHOWROOM_REMIND(6, "样品间提醒"),
    // order-flow-complete §1 C：四类新增交易邮件
    ORDER_CANCELLED(7, "订单取消"),
    ORDER_DELIVERED(8, "订单签收"),
    ORDER_PRODUCTION(9, "订单进入制作"),
    REFUND_REQUESTED(10, "退款受理");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    MailType(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /** 事件 type（routing key）→ 邮件类型；不属于邮件语义的事件返回 null（消费侧 ack 跳过） */
    public static MailType fromEventType(String eventType) {
        if (eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "order.paid" -> ORDER_CONFIRMED;
            case "order.shipped" -> ORDER_SHIPPED;
            case "refund.resolved" -> REFUND_RESOLVED;
            case "order.cancelled" -> ORDER_CANCELLED;
            case "order.delivered" -> ORDER_DELIVERED;
            case "order.production" -> ORDER_PRODUCTION;
            case "refund.requested" -> REFUND_REQUESTED;
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
