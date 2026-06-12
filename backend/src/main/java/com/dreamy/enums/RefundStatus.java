package com.dreamy.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 退款工单状态（refund_lifecycle 三态，TASK-041）。
 * pending→approved/rejected；非 pending 审核 → 409604（js_guard + casApprove/casReject 终防线）。
 * L2 TRACE: MAP-TRD-012 / CV-TRD-001 / TC-TRD-008。
 */
public enum RefundStatus implements StrEnum {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected");

    @JsonValue
    @Getter
    private final String key;

    RefundStatus(String key) {
        this.key = key;
    }

    /** 仅 pending 可审（approve/reject/patch 登记） */
    public boolean canTransitionTo(RefundStatus target) {
        return this == PENDING && (target == APPROVED || target == REJECTED);
    }

    public static RefundStatus of(String value) {
        for (RefundStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
