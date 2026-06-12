package com.dreamy.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * Banner 位置（hero|featured|topbar）。
 * L2 TRACE: MAP-MKT-013（VARCHAR + Java enum 双保险）/ V-MKT-001/040 / CV-MKT-001。
 */
public enum BannerPosition implements StrEnum {
    HERO("hero"),
    FEATURED("featured"),
    TOPBAR("topbar");

    @JsonValue
    @Getter
    private final String key;

    BannerPosition(String key) {
        this.key = key;
    }

    /** 契约字符串 → 枚举；未知值返回 null（调用方映射 422704） */
    public static BannerPosition of(String value) {
        for (BannerPosition p : values()) {
            if (p.key.equals(value)) {
                return p;
            }
        }
        return null;
    }
}
