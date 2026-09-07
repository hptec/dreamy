package com.dreamy.enums;

import lombok.Getter;

/**
 * 批量 Q&A 可见性操作（对齐 ReviewBatchAction 范式；guard 不满足 → skipped_ids 不报错）。
 * L2 TRACE: question_answer_flow batch_hide/batch_show。
 */
public enum QuestionBatchAction {
    HIDE("hide"),
    SHOW("show");

    @Getter
    private final String key;

    QuestionBatchAction(String key) {
        this.key = key;
    }

    /** 契约字符串 → 枚举；未知值返回 null（调用方映射 422801 fields.action=invalid_enum） */
    public static QuestionBatchAction of(String value) {
        for (QuestionBatchAction a : values()) {
            if (a.key.equals(value)) {
                return a;
            }
        }
        return null;
    }
}
