package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/** 订单事件操作者类型 */
@Enumable
public enum OrderActorType implements IntEnum, Describable {
    SYSTEM(1, "系统"),
    CUSTOMER(2, "顾客"),
    ADMIN(3, "管理员");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    OrderActorType(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static OrderActorType of(Integer value) {
        for (OrderActorType s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
