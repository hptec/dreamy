package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/** 汇率来源 */
@Enumable
public enum ExchangeRateSource implements IntEnum, Describable {
    MANUAL(1, "手工维护"),
    PROVIDER(2, "行情供应商");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    ExchangeRateSource(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static ExchangeRateSource of(Integer value) {
        for (ExchangeRateSource s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
