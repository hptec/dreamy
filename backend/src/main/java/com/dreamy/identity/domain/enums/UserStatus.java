package com.dreamy.identity.domain.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

@Enumable
public enum UserStatus implements IntEnum, Describable {
    ACTIVE(1, "正常"),
    DISABLED(2, "已禁用"),
    DELETED(3, "已删除"),
    ANONYMIZED(4, "已匿名化");

    @Getter private final Integer key;
    @Getter private final String desc;

    UserStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }
}
