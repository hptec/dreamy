package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * AI 翻译日志状态（成功 / 失败 / 超时 / 空结果 / 限流）。
 * L2 TRACE: i18n-backend-data-detail.md AiTranslationLog.status / 决策10 / EDGE-016/017。
 */
@Enumable
public enum AiTranslationStatus implements IntEnum, Describable {
    SUCCESS(1, "成功"),
    FAILED(2, "失败"),
    TIMEOUT(3, "超时"),
    EMPTY_RESULT(4, "空结果"),
    RATE_LIMITED(5, "限流");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    AiTranslationStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static AiTranslationStatus of(Integer value) {
        for (AiTranslationStatus t : values()) {
            if (t.key.equals(value)) {
                return t;
            }
        }
        return null;
    }
}
