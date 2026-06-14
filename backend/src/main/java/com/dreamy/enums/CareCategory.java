package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 护理类别枚举（washing|bleaching|drying|ironing|dry_cleaning）。
 * L2 TRACE: catalog-fabric-care-data-detail §1.3 CareCategory枚举 / MAP-FC-007 IntEnum整数契约。
 */
@Enumable
public enum CareCategory implements IntEnum, Describable {
    WASHING(1, "水洗"),
    BLEACHING(2, "漂白"),
    DRYING(3, "干燥"),
    IRONING(4, "熨烫"),
    DRY_CLEANING(5, "干洗");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    CareCategory(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /** 契约整数码 → 枚举；未知值返回 null（调用方映射 422501） */
    public static CareCategory of(Integer value) {
        if (value == null) {
            return null;
        }
        for (CareCategory category : values()) {
            if (category.key.equals(value)) {
                return category;
            }
        }
        return null;
    }
}
