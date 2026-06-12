package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

@Enumable
public enum UserTier implements IntEnum, Describable {
    REGULAR(1, "常规"),
    VIP(2, "VIP");

    @Getter private final Integer key;
    @Getter private final String desc;

    UserTier(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }
}
