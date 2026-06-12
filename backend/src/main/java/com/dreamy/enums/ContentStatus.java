package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 三态内容状态（draft|published|archived，banner_lifecycle / blog_post_lifecycle）。
 * L2 TRACE: MAP-MKT-013 / V-MKT-042/047/053/057 / CV-MKT-001。
 */
@Enumable
public enum ContentStatus implements IntEnum, Describable {
    DRAFT(1, "草稿"),
    PUBLISHED(2, "已发布"),
    ARCHIVED(3, "已归档");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    ContentStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static ContentStatus of(Integer value) {
        for (ContentStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
