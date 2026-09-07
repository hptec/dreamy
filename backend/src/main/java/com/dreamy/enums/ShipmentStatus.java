package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/** 包裹状态（PENDING→IN_TRANSIT→OUT_FOR_DELIVERY→DELIVERED 单向；EXCEPTION 可从任意非终态进入并恢复；CANCELLED 仅无供应商事件且未签收时） */
@Enumable
public enum ShipmentStatus implements IntEnum, Describable {
    PENDING(1, "待揽收"),
    IN_TRANSIT(2, "运输中"),
    OUT_FOR_DELIVERY(3, "派送中"),
    DELIVERED(4, "已签收"),
    EXCEPTION(5, "异常"),
    CANCELLED(6, "已作废");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    ShipmentStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static ShipmentStatus of(Integer value) {
        for (ShipmentStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
