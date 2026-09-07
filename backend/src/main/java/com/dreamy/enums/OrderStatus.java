package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

import java.util.Map;
import java.util.Set;

/**
 * 订单状态（order_lifecycle 八态；order-flow-complete §2.4）。
 * 合法转换（guard 终防线为条件更新 CAS RM-TRD-026，本枚举为前置 js_guard）：
 * pending→paid/cancelled；paid→shipped/refunding/cancelled(后台+全额退款)；shipped→delivered/completed/refunding；
 * delivered→completed/refunding；refunding→refunded/paid/shipped/delivered（还原 from_status）。
 */
@Enumable
public enum OrderStatus implements IntEnum, Describable {
    PENDING(1, "待支付"),
    PAID(2, "已支付"),
    SHIPPED(3, "已发货"),
    COMPLETED(4, "已完成"),
    CANCELLED(5, "已取消"),
    REFUNDING(6, "退款中"),
    REFUNDED(7, "已退款"),
    DELIVERED(8, "已送达");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    OrderStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /** order_lifecycle 转换矩阵 */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            PENDING, Set.of(PAID, CANCELLED),
            PAID, Set.of(SHIPPED, REFUNDING, CANCELLED),
            SHIPPED, Set.of(DELIVERED, COMPLETED, REFUNDING),
            DELIVERED, Set.of(COMPLETED, REFUNDING),
            REFUNDING, Set.of(REFUNDED, PAID, SHIPPED, DELIVERED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of(),
            REFUNDED, Set.of()
    );

    /** 终态 */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == REFUNDED;
    }

    /** 已支付且尚未进入售后/终态（可申请退款、可发货推进） */
    public boolean isPostPaymentActive() {
        return this == PAID || this == SHIPPED || this == DELIVERED;
    }

    /** 状态机 guard（非法转换 → 调用方映射 409602） */
    public boolean canTransitionTo(OrderStatus target) {
        return target != null && TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** 契约整数码 → 枚举；未知值返回 null（调用方映射 422601） */
    public static OrderStatus of(Integer value) {
        for (OrderStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
