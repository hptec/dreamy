package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

@Enumable
public enum RoleType implements IntEnum, Describable {
    PRESET(1, "系统预设"),
    CUSTOM(2, "自定义");

    @Getter private final Integer key;
    @Getter private final String desc;

    RoleType(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }
}
