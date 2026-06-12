package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

@Enumable
public enum SessionStatus implements IntEnum, Describable {
    ACTIVE(1, "活跃"),
    REVOKED(2, "已撤销");

    @Getter private final Integer key;
    @Getter private final String desc;

    SessionStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }
}
