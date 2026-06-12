package com.dreamy.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

import java.util.Map;
import java.util.Set;

/**
 * 支付单状态（payment_lifecycle 五态，TASK-039）。
 * created→processing/succeeded/failed；processing→succeeded/failed；succeeded→refunded；
 * failed→created（retryOrderPayment 重建 PI 凭据，RM-TRD-044）。
 * L2 TRACE: MAP-TRD-012 / CV-TRD-001 / TC-TRD-007。
 */
public enum PaymentStatus implements StrEnum {
    CREATED("created"),
    PROCESSING("processing"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    REFUNDED("refunded");

    @JsonValue
    @Getter
    private final String key;

    PaymentStatus(String key) {
        this.key = key;
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

    public static PaymentStatus of(String value) {
        for (PaymentStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
