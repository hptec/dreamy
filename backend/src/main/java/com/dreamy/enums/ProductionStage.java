package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/** 订单制作阶段子状态（仅 status=PAID 时非空；1→2→3→4 单向递进，后台可回退一档纠错） */
@Enumable
public enum ProductionStage implements IntEnum, Describable {
    PENDING_REVIEW(1, "待审核"),
    IN_PRODUCTION(2, "制作中"),
    QUALITY_CHECK(3, "质检中"),
    READY_TO_SHIP(4, "待发货");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    ProductionStage(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static ProductionStage of(Integer value) {
        for (ProductionStage s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
