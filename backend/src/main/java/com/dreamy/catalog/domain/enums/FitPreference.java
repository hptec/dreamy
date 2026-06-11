package com.dreamy.catalog.domain.enums;

import lombok.Getter;

/**
 * 尺码推荐松紧偏好（snug 下移一档 / regular 不偏移 / relaxed 上移一档）。
 * L2 TRACE: V-CAT-016 / E-CAT-05 STEP-CAT-05。
 */
public enum FitPreference {
    SNUG("snug", -1),
    REGULAR("regular", 0),
    RELAXED("relaxed", 1);

    @Getter
    private final String key;
    /** 取码偏移档位 */
    @Getter
    private final int shift;

    FitPreference(String key, int shift) {
        this.key = key;
        this.shift = shift;
    }

    public static FitPreference of(String value) {
        for (FitPreference f : values()) {
            if (f.key.equals(value)) {
                return f;
            }
        }
        return null;
    }
}
