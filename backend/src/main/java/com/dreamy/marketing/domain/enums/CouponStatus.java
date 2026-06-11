package com.dreamy.marketing.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 优惠券状态（draft|scheduled|active|expiring|expired，coupon_lifecycle——SCHED-MKT-01 翻转，DEC-MKT-3）。
 * L2 TRACE: MAP-MKT-013 / V-MKT-017/026 / CV-MKT-011。
 */
public enum CouponStatus implements StrEnum {
    DRAFT("draft"),
    SCHEDULED("scheduled"),
    ACTIVE("active"),
    EXPIRING("expiring"),
    EXPIRED("expired");

    @JsonValue
    @Getter
    private final String key;

    CouponStatus(String key) {
        this.key = key;
    }

    public static CouponStatus of(String value) {
        for (CouponStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
