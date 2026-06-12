package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 标签状态（enabled|disabled）。
 * L2 TRACE: MAP-CAT-012 / V-CAT-065 / TASK-035 tag_lifecycle。
 */
@Enumable
public enum TagStatus implements IntEnum, Describable {
    ENABLED(1, "启用"),
    DISABLED(2, "禁用");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    TagStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static TagStatus of(Integer value) {
        for (TagStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
