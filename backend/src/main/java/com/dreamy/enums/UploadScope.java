package com.dreamy.enums;

import lombok.Getter;

/**
 * 预签名上传对象 key 前缀归类（product|category|banner|content）。
 * L2 TRACE: V-CAT-071 / E-CAT-38 STEP-CAT-01（媒体基建由 catalog 域代管，四 scope 共用）。
 */
public enum UploadScope {
    PRODUCT("product"),
    CATEGORY("category"),
    BANNER("banner"),
    CONTENT("content");

    @Getter
    private final String key;

    UploadScope(String key) {
        this.key = key;
    }

    public static UploadScope of(String value) {
        for (UploadScope s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
