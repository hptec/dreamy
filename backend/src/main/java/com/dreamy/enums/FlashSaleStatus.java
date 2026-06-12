package com.dreamy.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 闪购状态（draft|scheduled|active|ended，flash_sale_lifecycle——SCHED-MKT-01 翻转，s-761 自动下线）。
 * L2 TRACE: MAP-MKT-013 / V-MKT-030/034 / CV-MKT-011。
 */
public enum FlashSaleStatus implements StrEnum {
    DRAFT("draft"),
    SCHEDULED("scheduled"),
    ACTIVE("active"),
    ENDED("ended");

    @JsonValue
    @Getter
    private final String key;

    FlashSaleStatus(String key) {
        this.key = key;
    }

    public static FlashSaleStatus of(String value) {
        for (FlashSaleStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
