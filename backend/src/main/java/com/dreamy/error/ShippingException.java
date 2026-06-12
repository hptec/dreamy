package com.dreamy.error;

import lombok.Getter;

import java.util.Map;

/**
 * shipping 域业务异常。携带 ShippingErrorCode + 可选 details
 * （422901 {field} / 409901 {zone} / 409902 {enabled_count}，线上装入 R.data）。
 * 约束: error-strategy 分层错误处理（应用层抛领域异常 → 表示层映射）。
 */
@Getter
public class ShippingException extends RuntimeException {

    private final ShippingErrorCode errorCode;
    private final transient Map<String, Object> details;

    public ShippingException(ShippingErrorCode errorCode) {
        this(errorCode, null);
    }

    public ShippingException(ShippingErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.name() + "(" + errorCode.getCode() + ")");
        this.errorCode = errorCode;
        this.details = details;
    }

    /** 422901 字段级校验失败便捷构造：details = { field: <字段名> }（shipping-api-detail V-SHP 口径） */
    public static ShippingException fieldValidation(String field) {
        return new ShippingException(ShippingErrorCode.FIELD_VALIDATION_FAILED, Map.of("field", field));
    }
}
