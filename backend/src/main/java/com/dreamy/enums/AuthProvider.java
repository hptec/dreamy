package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

@Enumable
public enum AuthProvider implements IntEnum, Describable {
    EMAIL(1, "邮箱"),
    GOOGLE(2, "Google"),
    APPLE(3, "Apple");

    @Getter private final Integer key;
    @Getter private final String desc;

    AuthProvider(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }
}
