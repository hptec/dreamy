package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 优惠券状态（draft|scheduled|active|expiring|expired，coupon_lifecycle——SCHED-MKT-01 翻转，DEC-MKT-3）。
 * L2 TRACE: MAP-MKT-013 / V-MKT-017/026 / CV-MKT-011。
 */
@Enumable
public enum CouponStatus implements IntEnum, Describable {
    DRAFT(1, "草稿"),
    SCHEDULED(2, "已排期"),
    ACTIVE(3, "生效中"),
    EXPIRING(4, "即将过期"),
    EXPIRED(5, "已过期");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    CouponStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static CouponStatus of(Integer value) {
        for (CouponStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
