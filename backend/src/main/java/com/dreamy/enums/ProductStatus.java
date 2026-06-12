package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 商品状态（draft|published）。
 * L2 TRACE: MAP-CAT-012（VARCHAR + Java enum 双保险，契约字符串取值）/ CV-CAT-001。
 */
@Enumable
public enum ProductStatus implements IntEnum, Describable {
    DRAFT(1, "草稿"),
    PUBLISHED(2, "已上架");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    ProductStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /** 契约整数码 → 枚举；未知值返回 null（调用方映射 422501） */
    public static ProductStatus of(Integer value) {
        for (ProductStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
