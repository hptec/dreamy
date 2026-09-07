package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/** 配送服务等级 */
@Enumable
public enum ShippingServiceLevel implements IntEnum, Describable {
    STANDARD(1, "标准"),
    EXPRESS(2, "加急");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    ShippingServiceLevel(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static ShippingServiceLevel of(Integer value) {
        for (ShippingServiceLevel s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
