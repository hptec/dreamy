package com.dreamy.error;

import lombok.Getter;

import java.util.Map;

/**
 * review 域业务异常。携带 ReviewErrorCode + 可选 details（422801 字段级 fields 字典，线上装入 R.data）。
 * 约束: error-strategy 分层错误处理（应用层抛领域异常 → 表示层映射）。
 */
@Getter
public class ReviewException extends RuntimeException {

    private final ReviewErrorCode errorCode;
    private final transient Map<String, Object> details;

    public ReviewException(ReviewErrorCode errorCode) {
        this(errorCode, null);
    }

    public ReviewException(ReviewErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.name() + "(" + errorCode.getCode() + ")");
        this.errorCode = errorCode;
        this.details = details;
    }

    /** 422801 字段级校验失败便捷构造：details = { fields: { field: reason_key } } */
    public static ReviewException fieldValidation(Map<String, String> fields) {
        return new ReviewException(ReviewErrorCode.FIELD_VALIDATION_FAILED, Map.of("fields", fields));
    }

    public static ReviewException fieldValidation(String field, String reasonKey) {
        return fieldValidation(Map.of(field, reasonKey));
    }
}
