package com.dreamy.shipping.error;

import lombok.Getter;

/**
 * shipping 域错误码枚举（5 码，域段 9，admin-only 固定中文文案）。
 * 权威来源 error-strategy.md shipping 段 + shipping-api.openapi.yml info 码表。
 * identity 复用码（40100/40300/50000/50001）仍走 identity ErrorCode。
 */
@Getter
public enum ShippingErrorCode {

    // ===== 404 =====
    CARRIER_NOT_FOUND(404901, 404, "承运方不存在"),
    SHIPPING_RATE_NOT_FOUND(404902, 404, "运费规则不存在"),

    // ===== 409 =====
    ZONE_EXISTS(409901, 409, "同名规则行已存在"),
    LAST_ENABLED_CARRIER(409902, 409, "至少保留一个启用的承运方"),

    // ===== 422 =====
    FIELD_VALIDATION_FAILED(422901, 422, "字段校验失败");

    /** 数字业务码（契约稳定锚点） */
    private final int code;
    /** HTTP 状态码（与码高 3 位一致） */
    private final int httpStatus;
    /** admin 端固定中文文案（本域无消费端，无三语 bundle） */
    private final String messageZh;

    ShippingErrorCode(int code, int httpStatus, String messageZh) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageZh = messageZh;
    }
}
