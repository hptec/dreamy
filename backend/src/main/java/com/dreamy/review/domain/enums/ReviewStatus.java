package com.dreamy.review.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 评价审核状态（pending|approved|rejected，review_moderation 状态机）。
 * L2 TRACE: MAP-REV-008 / CV-REV-002（bs-510）/ TASK-047。
 */
public enum ReviewStatus implements StrEnum {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected");

    @JsonValue
    @Getter
    private final String key;

    ReviewStatus(String key) {
        this.key = key;
    }

    /** 契约字符串 → 枚举；未知值返回 null（调用方映射 422801） */
    public static ReviewStatus of(String value) {
        for (ReviewStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
