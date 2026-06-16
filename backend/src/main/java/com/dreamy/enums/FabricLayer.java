package com.dreamy.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 面料层次枚举（Shell|Lining|Overlay|Trim）。
 * L2 TRACE: catalog-fabric-care-data-detail §1.3 Layer枚举 / MAP-FC-007 IntEnum整数契约。
 */
@Enumable
public enum FabricLayer implements IntEnum, Describable {
    SHELL(1, "主料"),
    LINING(2, "内衬"),
    OVERLAY(3, "装饰层"),
    TRIM(4, "边饰");

    @Getter
    @EnumValue
    private final Integer key;

    @Getter
    private final String desc;

    FabricLayer(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /** 契约整数码 → 枚举；未知值返回 null（调用方映射 422501） */
    public static FabricLayer of(Integer value) {
        if (value == null) {
            return null;
        }
        for (FabricLayer layer : values()) {
            if (layer.key.equals(value)) {
                return layer;
            }
        }
        return null;
    }
}
