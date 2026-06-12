package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

@Enumable
public enum OtpStatus implements IntEnum, Describable {
    PENDING(1, "待验证"),
    CONSUMED(2, "已消耗"),
    EXPIRED(3, "已过期"),
    LOCKED(4, "已锁定");

    @Getter private final Integer key;
    @Getter private final String desc;

    OtpStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }
}
