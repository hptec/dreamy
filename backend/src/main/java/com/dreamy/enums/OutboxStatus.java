package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/** 事务性发件箱状态 */
@Enumable
public enum OutboxStatus implements IntEnum, Describable {
    PENDING(1, "待投递"),
    SENT(2, "已投递"),
    DEAD(3, "投递失败终止");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    OutboxStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static OutboxStatus of(Integer value) {
        for (OutboxStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
