package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 护理标签状态枚举（active|disabled）。
 * L2 TRACE: catalog-fabric-care-data-detail §1.3 CareStatus枚举 / MAP-FC-007 IntEnum整数契约。
 */
@Enumable
public enum CareStatus implements IntEnum, Describable {
    ACTIVE(1, "启用"),
    DISABLED(2, "禁用");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    CareStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /** 契约整数码 → 枚举；未知值返回 null（调用方映射 422501） */
    public static CareStatus of(Integer value) {
        if (value == null) {
            return null;
        }
        for (CareStatus status : values()) {
            if (status.key.equals(value)) {
                return status;
            }
        }
        return null;
    }
}
