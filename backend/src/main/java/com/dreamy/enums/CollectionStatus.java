package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 集合状态（enabled|disabled）。
 * L2 TRACE: MAP-CAT-012 / V-CAT-065 / TASK-035 collection_lifecycle。
 */
@Enumable
public enum CollectionStatus implements IntEnum, Describable {
    ENABLED(1, "启用"),
    DISABLED(2, "禁用");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    CollectionStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static CollectionStatus of(Integer value) {
        for (CollectionStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
