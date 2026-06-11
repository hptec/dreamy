package com.dreamy.marketing.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * Newsletter 订阅来源（footer|modal|exit_intent）。
 * L2 TRACE: MAP-MKT-013 / V-MKT-010 / CV-MKT-001。
 */
public enum NewsletterSource implements StrEnum {
    FOOTER("footer"),
    MODAL("modal"),
    EXIT_INTENT("exit_intent");

    @JsonValue
    @Getter
    private final String key;

    NewsletterSource(String key) {
        this.key = key;
    }

    public static NewsletterSource of(String value) {
        for (NewsletterSource s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
