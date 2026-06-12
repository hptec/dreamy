package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 退款工单状态（refund_lifecycle 三态，TASK-041）。
 * pending→approved/rejected；非 pending 审核 → 409604（js_guard + casApprove/casReject 终防线）。
 * L2 TRACE: MAP-TRD-012 / CV-TRD-001 / TC-TRD-008。
 */
@Enumable
public enum RefundStatus implements IntEnum, Describable {
    PENDING(1, "待处理"),
    APPROVED(2, "已同意"),
    REJECTED(3, "已拒绝");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    RefundStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /** 仅 pending 可审（approve/reject/patch 登记） */
    public boolean canTransitionTo(RefundStatus target) {
        return this == PENDING && (target == APPROVED || target == REJECTED);
    }

    public static RefundStatus of(Integer value) {
        for (RefundStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
