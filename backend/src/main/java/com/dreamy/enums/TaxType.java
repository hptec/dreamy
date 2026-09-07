package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/** 税种 */
@Enumable
public enum TaxType implements IntEnum, Describable {
    VAT(1, "增值税"),
    GST(2, "商品服务税"),
    SALES_TAX(3, "销售税"),
    DUTY(4, "关税");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    TaxType(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static TaxType of(Integer value) {
        for (TaxType s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
