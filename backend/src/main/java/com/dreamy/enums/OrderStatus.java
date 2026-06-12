package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

import java.util.Map;
import java.util.Set;

/**
 * 订单状态（order_lifecycle 七态，state-machine.yml）。
 * 9 合法转换 + 其余拒绝（TASK-038；guard 终防线为条件更新 CAS RM-TRD-026，本枚举为前置 js_guard）：
 * pending→paid/cancelled；paid→shipped/refunding；shipped→completed/refunding；
 * refunding→refunded/paid/shipped。
 * L2 TRACE: MAP-TRD-012 / CV-TRD-001 / TC-TRD-006。
 */
@Enumable
public enum OrderStatus implements IntEnum, Describable {
    PENDING(1, "待支付"),
    PAID(2, "已支付"),
    SHIPPED(3, "已发货"),
    COMPLETED(4, "已完成"),
    CANCELLED(5, "已取消"),
    REFUNDING(6, "退款中"),
    REFUNDED(7, "已退款");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    OrderStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /** order_lifecycle 9 转换矩阵（TASK-038） */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            PENDING, Set.of(PAID, CANCELLED),
            PAID, Set.of(SHIPPED, REFUNDING),
            SHIPPED, Set.of(COMPLETED, REFUNDING),
            REFUNDING, Set.of(REFUNDED, PAID, SHIPPED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of(),
            REFUNDED, Set.of()
    );

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
