package com.dreamy.error;

import lombok.Getter;

/**
 * catalog 域错误码枚举（17 码，域段 5）。权威来源 error-strategy.md catalog 段 + catalog-api.openapi.yml info 码表。
 * 6 位码：HTTP(3) + 域段(1=5) + 序号(2)；identity 复用码（40100/40300/50000/50001）仍走 identity ErrorCode。
 */
@Getter
public enum CatalogErrorCode {

    // ===== 404 =====
    PRODUCT_NOT_FOUND(404501, 404, "error.404501"),
    CATEGORY_NOT_FOUND(404502, 404, "error.404502"),
    ATTRIBUTE_SET_NOT_FOUND(404503, 404, "error.404503"),
    ATTRIBUTE_DEF_NOT_FOUND(404504, 404, "error.404504"),
    TAG_NOT_FOUND(404505, 404, "error.404505"),

    // ===== 409 =====
    SLUG_EXISTS(409501, 409, "error.409501"),
    CATEGORY_HAS_PRODUCTS(409502, 409, "error.409502"),
    ATTRIBUTE_SET_IN_USE(409503, 409, "error.409503"),
    SKU_CODE_EXISTS(409504, 409, "error.409504"),
    CATEGORY_LEVEL_EXCEEDED(409505, 409, "error.409505"),
    TAG_DIMENSION_IN_USE(409506, 409, "error.409506"),
    ATTRIBUTE_DEF_IN_USE(409507, 409, "error.409507"),
    PRODUCT_VERSION_CONFLICT(409508, 409, "error.409508"),
    PRODUCT_NOT_DELETABLE(409509, 409, "error.409509"),

    // ===== 422 =====
    FIELD_VALIDATION_FAILED(422501, 422, "error.422501"),
    SIZE_INPUT_OUT_OF_RANGE(422502, 422, "error.422502"),
    FABRIC_PERCENTAGE_INVALID(422510, 422, "error.422510"),  // 面料成分百分比总和不等于100%
    CARE_CODE_EXISTS(422511, 422, "error.422511"),           // 护理标签 code 已存在
    CARE_NOT_FOUND(422512, 404, "error.422512"),             // 护理标签不存在

    // ===== 502 =====
    OBJECT_STORAGE_UNAVAILABLE(502501, 502, "error.502501");

    /** 数字业务码（契约稳定锚点） */
    private final int code;
    /** HTTP 状态码（与码高 3 位一致） */
    private final int httpStatus;
    /** i18n message key（catalog-messages bundle） */
    private final String messageKey;

    CatalogErrorCode(int code, int httpStatus, String messageKey) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
