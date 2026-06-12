package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 款式投票值（like|dislike，重复投票覆盖原票——PUT 幂等语义）。
 * L2 TRACE: showroom-data-detail §1.1 / CV-SHR-002（vote 二枚举，bs-541/370）。
 */
@Enumable
public enum VoteValue implements IntEnum, Describable {
    LIKE(1, "赞"),
    DISLIKE(2, "踩");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    VoteValue(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /** 契约整数码 → 枚举；未知值返回 null（调用方映射 422101 fields.vote=invalid_enum） */
    public static VoteValue of(Integer value) {
        for (VoteValue v : values()) {
            if (v.key.equals(value)) {
                return v;
            }
        }
        return null;
    }
}
