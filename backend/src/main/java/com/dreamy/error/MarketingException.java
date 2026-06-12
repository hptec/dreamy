package com.dreamy.error;

import lombok.Getter;

import java.util.Map;

/**
 * marketing 域业务异常。携带 MarketingErrorCode + 可选 details（422704 字段级 fields 字典 /
 * 409703 reason 等，线上装入 R.data）。
 * 约束: error-strategy 分层错误处理（应用层抛领域异常 → 表示层映射）。
 */
@Getter
public class MarketingException extends RuntimeException {

    private final MarketingErrorCode errorCode;
    private final transient Map<String, Object> details;

    public MarketingException(MarketingErrorCode errorCode) {
        this(errorCode, null);
    }

    public MarketingException(MarketingErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.name() + "(" + errorCode.getCode() + ")");
        this.errorCode = errorCode;
        this.details = details;
    }

    /** 422704 字段级校验失败便捷构造：details = { fields: { field: reason_key } } */
    public static MarketingException fieldValidation(Map<String, String> fields) {
        return new MarketingException(MarketingErrorCode.FIELD_VALIDATION_FAILED, Map.of("fields", fields));
    }

    public static MarketingException fieldValidation(String field, String reasonKey) {
        return fieldValidation(Map.of(field, reasonKey));
    }

    /** 409703 状态机 guard 便捷构造：details = { reason } */
    public static MarketingException stateInvalid(String reason) {
        return new MarketingException(MarketingErrorCode.CONTENT_STATE_INVALID, Map.of("reason", reason));
    }
}
