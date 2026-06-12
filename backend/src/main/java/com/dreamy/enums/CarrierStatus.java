package com.dreamy.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 承运方状态（enabled|disabled）。
 * L2 TRACE: MAP-SHP-004 / CV-SHP-002（VARCHAR + Java enum 双保险，契约字符串取值）；
 * state-machine carrier_status 两迁移（enabled--disable-->disabled / disabled--enable-->enabled）。
 */
public enum CarrierStatus implements StrEnum {
    ENABLED("enabled"),
    DISABLED("disabled");

    @JsonValue
    @Getter
    private final String key;

    CarrierStatus(String key) {
        this.key = key;
    }

    /** 契约字符串 → 枚举；未知值返回 null（调用方映射 422901，V-SHP-004/008） */
    public static CarrierStatus of(String value) {
        for (CarrierStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
