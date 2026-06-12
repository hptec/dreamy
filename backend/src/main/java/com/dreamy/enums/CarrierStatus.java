package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 承运方状态（enabled|disabled）。
 * L2 TRACE: MAP-SHP-004 / CV-SHP-002（VARCHAR + Java enum 双保险，契约字符串取值）；
 * state-machine carrier_status 两迁移（enabled--disable-->disabled / disabled--enable-->enabled）。
 */
@Enumable
public enum CarrierStatus implements IntEnum, Describable {
    ENABLED(1, "启用"),
    DISABLED(2, "禁用");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    CarrierStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /** 契约整数码 → 枚举；未知值返回 null（调用方映射 422901，V-SHP-004/008） */
    public static CarrierStatus of(Integer value) {
        for (CarrierStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
