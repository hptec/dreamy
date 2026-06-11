package com.dreamy.review.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 提问前台可见性（visible|hidden；UI Toggle 布尔映射枚举，er-diagram INFERRED 注记定稿）。
 * L2 TRACE: MAP-REV-008 / CV-REV-002（bs-511）/ V-REV-037 / TASK-049。
 */
public enum QuestionVisibility implements StrEnum {
    VISIBLE("visible"),
    HIDDEN("hidden");

    @JsonValue
    @Getter
    private final String key;

    QuestionVisibility(String key) {
        this.key = key;
    }

    /** 契约字符串 → 枚举；未知值返回 null（调用方映射 422801） */
    public static QuestionVisibility of(String value) {
        for (QuestionVisibility v : values()) {
            if (v.key.equals(value)) {
                return v;
            }
        }
        return null;
    }
}
