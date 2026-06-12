package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

import java.util.Map;
import java.util.Set;

/**
 * 支付单状态（payment_lifecycle 五态，TASK-039）。
 * created→processing/succeeded/failed；processing→succeeded/failed；succeeded→refunded；
 * failed→created（retryOrderPayment 重建 PI 凭据，RM-TRD-044）。
 * L2 TRACE: MAP-TRD-012 / CV-TRD-001 / TC-TRD-007。
 */
@Enumable
public enum PaymentStatus implements IntEnum, Describable {
    CREATED(1, "已创建"),
    PROCESSING(2, "处理中"),
    SUCCEEDED(3, "支付成功"),
    FAILED(4, "支付失败"),
    REFUNDED(5, "已退款");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    PaymentStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.of(
            CREATED, Set.of(PROCESSING, SUCCEEDED, FAILED),
            PROCESSING, Set.of(SUCCEEDED, FAILED),
            SUCCEEDED, Set.of(REFUNDED),
            FAILED, Set.of(CREATED),
            REFUNDED, Set.of()
    );

    public boolean canTransitionTo(PaymentStatus target) {
        return target != null && TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public static PaymentStatus of(Integer value) {
        for (PaymentStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
