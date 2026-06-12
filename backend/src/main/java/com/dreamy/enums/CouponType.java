package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 优惠券类型（discount|fixed_amount|free_shipping）。
 * L2 TRACE: MAP-MKT-013 / V-MKT-021 / DEC-MKT-4（value 按 type 可解析 pattern）。
 */
@Enumable
public enum CouponType implements IntEnum, Describable {
    DISCOUNT(1, "折扣"),
    FIXED_AMOUNT(2, "固定金额"),
    FREE_SHIPPING(3, "免运费");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    CouponType(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static CouponType of(Integer value) {
        for (CouponType t : values()) {
            if (t.key.equals(value)) {
                return t;
            }
        }
        return null;
    }
}
