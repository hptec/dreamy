package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 商品媒体类型（gallery|lifestyle|video|swatch）。
 * L2 TRACE: MAP-CAT-012 / V-CAT-031。
 */
@Enumable
public enum ImageKind implements IntEnum, Describable {
    GALLERY(1, "商品图"),
    LIFESTYLE(2, "场景图"),
    VIDEO(3, "视频"),
    SWATCH(4, "色板");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    ImageKind(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static ImageKind of(Integer value) {
        for (ImageKind k : values()) {
            if (k.key.equals(value)) {
                return k;
            }
        }
        return null;
    }
}
