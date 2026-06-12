package com.dreamy.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 属性可见性三态（visible=必填|optional=可选|hidden=隐藏）。
 * L2 TRACE: MAP-CAT-012 / V-CAT-050（FLD-ATTRIBUTESETS-001/002/003）/ TASK-037 attribute_visibility_cycle。
 */
public enum AttributeVisibility implements StrEnum {
    VISIBLE("visible"),
    OPTIONAL("optional"),
    HIDDEN("hidden");

    @JsonValue
    @Getter
    private final String key;

    AttributeVisibility(String key) {
        this.key = key;
    }

    public static AttributeVisibility of(String value) {
        for (AttributeVisibility v : values()) {
            if (v.key.equals(value)) {
                return v;
            }
        }
        return null;
    }
}
