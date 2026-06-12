package com.dreamy.error;

import lombok.Getter;

import java.util.Map;

/**
 * trading 域业务异常。携带 TradingErrorCode + 可选 details（422601 fields 字典 / 409603 order_id /
 * 422602 grace_deadline / 409601 sku_id 等，线上装入 R.data）。
 * 约束: error-strategy 分层错误处理（应用层抛领域异常 → 表示层映射）。
 */
@Getter
public class TradingException extends RuntimeException {

    private final TradingErrorCode errorCode;
    private final transient Map<String, Object> details;

    public TradingException(TradingErrorCode errorCode) {
        this(errorCode, null);
    }

    public TradingException(TradingErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.name() + "(" + errorCode.getCode() + ")");
        this.errorCode = errorCode;
        this.details = details;
    }

    /** 422601 字段级校验失败便捷构造：details = { fields: { field: reason_key } } */
    public static TradingException fieldValidation(Map<String, String> fields) {
        return new TradingException(TradingErrorCode.FIELD_VALIDATION_FAILED, Map.of("fields", fields));
    }

    public static TradingException fieldValidation(String field, String reasonKey) {
        return fieldValidation(Map.of(field, reasonKey));
    }

    /** 409602 状态机 guard 便捷构造 */
    public static TradingException orderStateInvalid() {
        return new TradingException(TradingErrorCode.ORDER_STATE_INVALID);
    }
}
