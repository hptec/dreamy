package com.dreamy.review.domain.enums;

import lombok.Getter;

/**
 * 消费端评价列表排序（V-REV-002，缺省 featured_first）。
 * 排序落地（E-REV-01 STEP-REV-02）：featured_first=featured DESC,submitted_at DESC；
 * newest=submitted_at DESC；rating_desc=rating DESC,submitted_at DESC；rating_asc=rating ASC,submitted_at DESC。
 */
public enum ReviewSort {
    NEWEST("newest"),
    RATING_DESC("rating_desc"),
    RATING_ASC("rating_asc"),
    FEATURED_FIRST("featured_first");

    @Getter
    private final String key;

    ReviewSort(String key) {
        this.key = key;
    }

    /** 契约字符串 → 枚举；未知值返回 null（调用方映射 422801 fields.sort=invalid_enum） */
    public static ReviewSort of(String value) {
        for (ReviewSort s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
