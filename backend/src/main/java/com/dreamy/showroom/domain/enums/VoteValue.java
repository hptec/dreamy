package com.dreamy.showroom.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 款式投票值（like|dislike，重复投票覆盖原票——PUT 幂等语义）。
 * L2 TRACE: showroom-data-detail §1.1 / CV-SHR-002（vote 二枚举，bs-541/370）。
 */
public enum VoteValue implements StrEnum {
    LIKE("like"),
    DISLIKE("dislike");

    @JsonValue
    @Getter
    private final String key;

    VoteValue(String key) {
        this.key = key;
    }

    /** 契约字符串 → 枚举；未知值返回 null（调用方映射 422101 fields.vote=invalid_enum） */
    public static VoteValue of(String value) {
        for (VoteValue v : values()) {
            if (v.key.equals(value)) {
                return v;
            }
        }
        return null;
    }
}
