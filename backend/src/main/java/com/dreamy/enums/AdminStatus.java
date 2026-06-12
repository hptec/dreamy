package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

@Enumable
public enum AdminStatus implements IntEnum, Describable {
    ACTIVE(1, "正常"),
    DISABLED(2, "已禁用");

    @Getter private final Integer key;
    @Getter private final String desc;

    AdminStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }
}
