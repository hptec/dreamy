package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

@Enumable
public enum LoginOutcome implements IntEnum, Describable {
    SUCCESS(1, "成功"),
    FAILED(2, "失败");

    @Getter private final Integer key;
    @Getter private final String desc;

    LoginOutcome(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }
}
