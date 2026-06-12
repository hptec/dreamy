package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 属性定义类型（select|multiselect|text|toggle）。
 * L2 TRACE: MAP-CAT-012 / V-CAT-055。
 */
@Enumable
public enum AttributeType implements IntEnum, Describable {
    SELECT(1, "单选"),
    MULTISELECT(2, "多选"),
    TEXT(3, "文本"),
    TOGGLE(4, "开关");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    AttributeType(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static AttributeType of(Integer value) {
        for (AttributeType t : values()) {
            if (t.key.equals(value)) {
                return t;
            }
        }
        return null;
    }

    /** select/multiselect 才允许 options（V-CAT-056 js_guard） */
    public boolean optionsAllowed() {
        return this == SELECT || this == MULTISELECT;
    }
}
