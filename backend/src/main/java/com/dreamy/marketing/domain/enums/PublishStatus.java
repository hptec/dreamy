package com.dreamy.marketing.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 二态发布状态（draft|published，lookbook_publish / guide_publish / real_wedding_publish）。
 * L2 TRACE: MAP-MKT-013 / V-MKT-061/065/069/073/079/082 / CV-MKT-001。
 */
public enum PublishStatus implements StrEnum {
    DRAFT("draft"),
    PUBLISHED("published");

    @JsonValue
    @Getter
    private final String key;

    PublishStatus(String key) {
        this.key = key;
    }

    public static PublishStatus of(String value) {
        for (PublishStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
