package com.dreamy.marketing.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 三态内容状态（draft|published|archived，banner_lifecycle / blog_post_lifecycle）。
 * L2 TRACE: MAP-MKT-013 / V-MKT-042/047/053/057 / CV-MKT-001。
 */
public enum ContentStatus implements StrEnum {
    DRAFT("draft"),
    PUBLISHED("published"),
    ARCHIVED("archived");

    @JsonValue
    @Getter
    private final String key;

    ContentStatus(String key) {
        this.key = key;
    }

    public static ContentStatus of(String value) {
        for (ContentStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
