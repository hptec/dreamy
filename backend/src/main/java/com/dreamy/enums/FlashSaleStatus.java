package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 闪购状态（draft|scheduled|active|ended，flash_sale_lifecycle——SCHED-MKT-01 翻转，s-761 自动下线）。
 * L2 TRACE: MAP-MKT-013 / V-MKT-030/034 / CV-MKT-011。
 */
@Enumable
public enum FlashSaleStatus implements IntEnum, Describable {
    DRAFT(1, "草稿"),
    SCHEDULED(2, "已排期"),
    ACTIVE(3, "进行中"),
    ENDED(4, "已结束");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    FlashSaleStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static FlashSaleStatus of(Integer value) {
        for (FlashSaleStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
