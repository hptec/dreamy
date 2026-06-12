package com.dreamy.error;

import lombok.Getter;

/**
 * review 域错误码枚举（10 码，域段 8）。权威来源 error-strategy.md review 段 + review-api.openapi.yml info 码表。
 * 6 位码：HTTP(3) + 域段(1=8) + 序号(2)；identity 复用码（40100/40300/50000/50001）仍走 identity ErrorCode；
 * 商品引用校验透传 catalog 404501（CatalogException，ReviewExceptionHandler 一并映射）。
 */
@Getter
public enum ReviewErrorCode {

    // ===== 403 =====
    REVIEW_NOT_ALLOWED(403801, 403, "error.403801"),

    // ===== 404 =====
    REVIEW_NOT_FOUND(404801, 404, "error.404801"),
    QUESTION_NOT_FOUND(404802, 404, "error.404802"),
    REVIEW_IMAGE_NOT_FOUND(404803, 404, "error.404803"),

    // ===== 409 =====
    ALREADY_REVIEWED(409801, 409, "error.409801"),
    REVIEW_STATE_INVALID(409802, 409, "error.409802"),
    FEATURED_REQUIRES_APPROVED(409803, 409, "error.409803"),
    REPLY_REQUIRES_APPROVED(409804, 409, "error.409804"),

    // ===== 422 =====
    FIELD_VALIDATION_FAILED(422801, 422, "error.422801"),

    // ===== 502 =====
    OBJECT_STORAGE_UNAVAILABLE(502801, 502, "error.502801");

    /** 数字业务码（契约稳定锚点） */
    private final int code;
    /** HTTP 状态码（与码高 3 位一致） */
    private final int httpStatus;
    /** i18n message key（review-messages bundle） */
    private final String messageKey;

    ReviewErrorCode(int code, int httpStatus, String messageKey) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
