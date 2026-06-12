package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 二态发布状态（draft|published，lookbook_publish / guide_publish / real_wedding_publish）。
 * L2 TRACE: MAP-MKT-013 / V-MKT-061/065/069/073/079/082 / CV-MKT-001。
 */
@Enumable
public enum PublishStatus implements IntEnum, Describable {
    DRAFT(1, "草稿"),
    PUBLISHED(2, "已发布");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    PublishStatus(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static PublishStatus of(Integer value) {
        for (PublishStatus s : values()) {
            if (s.key.equals(value)) {
                return s;
            }
        }
        return null;
    }
}
