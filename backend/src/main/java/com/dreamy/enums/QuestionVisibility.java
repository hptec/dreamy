package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 提问前台可见性（visible|hidden；UI Toggle 布尔映射枚举，er-diagram INFERRED 注记定稿）。
 * L2 TRACE: MAP-REV-008 / CV-REV-002（bs-511）/ V-REV-037 / TASK-049。
 */
@Enumable
public enum QuestionVisibility implements IntEnum, Describable {
    VISIBLE(1, "显示"),
    HIDDEN(2, "隐藏");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    QuestionVisibility(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    /** 契约整数码 → 枚举；未知值返回 null（调用方映射 422801） */
    public static QuestionVisibility of(Integer value) {
        for (QuestionVisibility v : values()) {
            if (v.key.equals(value)) {
                return v;
            }
        }
        return null;
    }
}
