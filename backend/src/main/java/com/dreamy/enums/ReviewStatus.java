package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 评价审核状态（pending|approved|rejected，review_moderation 状态机）。
 * L2 TRACE: MAP-REV-008 / CV-REV-002（bs-510）/ TASK-047。
 */
@Enumable
public enum ReviewStatus implements IntEnum, Describable {
    PENDING(1, "待审核"),
    APPROVED(2, "已通过"),
    REJECTED(3, "已拒绝");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    ReviewStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /** 契约整数码 → 枚举；未知值返回 null（调用方映射 422801） */
    public static ReviewStatus of(Integer value) {
        for (ReviewStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
