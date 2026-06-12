package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * Banner 位置（hero|featured|topbar）。
 * L2 TRACE: MAP-MKT-013（VARCHAR + Java enum 双保险）/ V-MKT-001/040 / CV-MKT-001。
 */
@Enumable
public enum BannerPosition implements IntEnum, Describable {
    HERO(1, "首屏大图"),
    FEATURED(2, "精选位"),
    TOPBAR(3, "顶栏");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    BannerPosition(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /** 契约整数码 → 枚举；未知值返回 null（调用方映射 422704） */
    public static BannerPosition of(Integer value) {
        for (BannerPosition p : values()) {
            if (p.key.equals(value)) {
                return p;
            }
        }
        return null;
    }
}
