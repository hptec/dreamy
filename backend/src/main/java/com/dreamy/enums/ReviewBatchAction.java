package com.dreamy.enums;

import lombok.Getter;

/**
 * 批量评价操作（V-REV-025；E-REV-09 batchSet：guard 不满足跳过 → skipped_ids，409803 批量语义=跳过不报错）。
 * L2 TRACE: state-machine review_moderation batch_approve/batch_reject/set_featured/unset_featured。
 */
public enum ReviewBatchAction {
    APPROVE("approve"),
    REJECT("reject"),
    FEATURE("feature"),
    UNFEATURE("unfeature");

    @Getter
    private final String key;

    ReviewBatchAction(String key) {
        this.key = key;
    }

    /** 契约字符串 → 枚举；未知值返回 null（调用方映射 422801 fields.action=invalid_enum） */
    public static ReviewBatchAction of(String value) {
        for (ReviewBatchAction a : values()) {
            if (a.key.equals(value)) {
                return a;
            }
        }
        return null;
    }
}
