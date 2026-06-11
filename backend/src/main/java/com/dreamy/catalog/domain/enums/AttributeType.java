package com.dreamy.catalog.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 属性定义类型（select|multiselect|text|toggle）。
 * L2 TRACE: MAP-CAT-012 / V-CAT-055。
 */
public enum AttributeType implements StrEnum {
    SELECT("select"),
    MULTISELECT("multiselect"),
    TEXT("text"),
    TOGGLE("toggle");

    @JsonValue
    @Getter
    private final String key;

    AttributeType(String key) {
        this.key = key;
    }

    public static AttributeType of(String value) {
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
