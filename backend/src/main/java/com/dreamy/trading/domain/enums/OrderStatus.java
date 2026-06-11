package com.dreamy.trading.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
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
public enum OrderStatus implements StrEnum {
    PENDING("pending"),
    PAID("paid"),
    SHIPPED("shipped"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    REFUNDING("refunding"),
    REFUNDED("refunded");

    @JsonValue
    @Getter
    private final String key;

    OrderStatus(String key) {
        this.key = key;
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

    /** 契约字符串 → 枚举；未知值返回 null（调用方映射 422601） */
    public static OrderStatus of(String value) {
        for (OrderStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
