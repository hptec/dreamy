package com.dreamy.catalog.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 标签状态（enabled|disabled）。
 * L2 TRACE: MAP-CAT-012 / V-CAT-065 / TASK-035 tag_lifecycle。
 */
public enum TagStatus implements StrEnum {
    ENABLED("enabled"),
    DISABLED("disabled");

    @JsonValue
    @Getter
    private final String key;

    TagStatus(String key) {
        this.key = key;
    }

    public static TagStatus of(String value) {
        for (TagStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
