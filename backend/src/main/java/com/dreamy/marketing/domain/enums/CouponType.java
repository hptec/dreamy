package com.dreamy.marketing.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 优惠券类型（discount|fixed_amount|free_shipping）。
 * L2 TRACE: MAP-MKT-013 / V-MKT-021 / DEC-MKT-4（value 按 type 可解析 pattern）。
 */
public enum CouponType implements StrEnum {
    DISCOUNT("discount"),
    FIXED_AMOUNT("fixed_amount"),
    FREE_SHIPPING("free_shipping");

    @JsonValue
    @Getter
    private final String key;

    CouponType(String key) {
        this.key = key;
    }

    public static CouponType of(String value) {
        for (CouponType t : values()) {
            if (t.key.equals(value)) {
                return t;
            }
        }
        return null;
    }
}
