package com.dreamy.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 面料材质枚举（Cotton|Polyester|Lace|Satin|Chiffon|Tulle|Silk|Organza|Spandex|Nylon）。
 * L2 TRACE: catalog-fabric-care-data-detail §1.3 Material枚举 / MAP-FC-007 IntEnum整数契约。
 */
@Enumable
public enum FabricMaterial implements IntEnum, Describable {
    COTTON(1, "棉"),
    POLYESTER(2, "聚酯纤维"),
    LACE(3, "蕾丝"),
    SATIN(4, "缎面"),
    CHIFFON(5, "雪纺"),
    TULLE(6, "薄纱"),
    SILK(7, "丝绸"),
    ORGANZA(8, "欧根纱"),
    SPANDEX(9, "氨纶"),
    NYLON(10, "尼龙");

    @Getter
    @EnumValue
    private final Integer key;

    @Getter
    private final String desc;

    FabricMaterial(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /** 契约整数码 → 枚举；未知值返回 null（调用方映射 422501） */
    public static FabricMaterial of(Integer value) {
        if (value == null) {
            return null;
        }
        for (FabricMaterial material : values()) {
            if (material.key.equals(value)) {
                return material;
            }
        }
        return null;
    }
}
