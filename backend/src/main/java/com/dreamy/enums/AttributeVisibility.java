package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 属性可见性三态（visible=必填|optional=可选|hidden=隐藏）。
 * L2 TRACE: MAP-CAT-012 / V-CAT-050（FLD-ATTRIBUTESETS-001/002/003）/ TASK-037 attribute_visibility_cycle。
 */
@Enumable
public enum AttributeVisibility implements IntEnum, Describable {
    VISIBLE(1, "显示"),
    OPTIONAL(2, "可选"),
    HIDDEN(3, "隐藏");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    AttributeVisibility(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static AttributeVisibility of(Integer value) {
        for (AttributeVisibility v : values()) {
            if (v.key.equals(value)) {
                return v;
            }
        }
        return null;
    }
}
