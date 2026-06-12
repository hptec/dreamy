package com.dreamy.error;

import lombok.Getter;

/**
 * marketing 域错误码枚举（10 码，域段 7）。权威来源 error-strategy.md marketing 段 + marketing-api.openapi.yml info 码表。
 * 6 位码：HTTP(3) + 域段(1=7) + 序号(2)；identity 复用码（40100/40300/50000/50001）仍走 identity ErrorCode。
 * 注：422701/422702/422703 在 E-MKT-10 校验端点以 200+valid=false+reason_code 出现，
 * 仅下单事务内核销（SVC-MKT-01 redeem）失败时以 422 异常形态抛出（双形态，TC-MKT-043）。
 */
@Getter
public enum MarketingErrorCode {

    // ===== 404 =====
    CONTENT_NOT_FOUND(404701, 404, "error.404701"),
    COUPON_NOT_FOUND(404702, 404, "error.404702"),
    FLASH_SALE_NOT_FOUND(404703, 404, "error.404703"),

    // ===== 409 =====
    COUPON_CODE_EXISTS(409701, 409, "error.409701"),
    SLUG_EXISTS(409702, 409, "error.409702"),
    CONTENT_STATE_INVALID(409703, 409, "error.409703"),

    // ===== 422 =====
    COUPON_INVALID(422701, 422, "error.422701"),
    COUPON_MIN_AMOUNT_NOT_MET(422702, 422, "error.422702"),
    COUPON_EXHAUSTED(422703, 422, "error.422703"),
    FIELD_VALIDATION_FAILED(422704, 422, "error.422704");

    /** 数字业务码（契约稳定锚点） */
    private final int code;
    /** HTTP 状态码（与码高 3 位一致） */
    private final int httpStatus;
    /** i18n message key（marketing-messages bundle） */
    private final String messageKey;

    MarketingErrorCode(int code, int httpStatus, String messageKey) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
