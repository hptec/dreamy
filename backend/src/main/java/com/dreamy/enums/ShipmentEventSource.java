package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/** 包裹轨迹事件来源 */
@Enumable
public enum ShipmentEventSource implements IntEnum, Describable {
    MANUAL(1, "手工录入"),
    PROVIDER(2, "供应商同步"),
    SYSTEM(3, "系统");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    ShipmentEventSource(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static ShipmentEventSource of(Integer value) {
        for (ShipmentEventSource s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
