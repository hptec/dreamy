package com.dreamy.catalog.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import huihao.enums.typeable.StrEnum;
import lombok.Getter;

/**
 * 商品媒体类型（gallery|lifestyle|video|swatch）。
 * L2 TRACE: MAP-CAT-012 / V-CAT-031。
 */
public enum ImageKind implements StrEnum {
    GALLERY("gallery"),
    LIFESTYLE("lifestyle"),
    VIDEO("video"),
    SWATCH("swatch");

    @JsonValue
    @Getter
    private final String key;

    ImageKind(String key) {
        this.key = key;
    }

    public static ImageKind of(String value) {
        for (ImageKind k : values()) {
            if (k.key.equals(value)) {
                return k;
            }
        }
        return null;
    }
}
