package com.dreamy.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 商品状态（draft|published）。
 * L2 TRACE: MAP-CAT-012（VARCHAR + Java enum 双保险，契约字符串取值）/ CV-CAT-001。
 */
public enum ProductStatus implements StrEnum {
    DRAFT("draft"),
    PUBLISHED("published");

    @JsonValue
    @Getter
    private final String key;

    ProductStatus(String key) {
        this.key = key;
    }

    /** 契约字符串 → 枚举；未知值返回 null（调用方映射 422501） */
    public static ProductStatus of(String value) {
        for (ProductStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
