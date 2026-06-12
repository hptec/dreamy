package com.dreamy.error;

import lombok.Getter;

import java.util.Map;

/**
 * showroom 域业务异常。携带 ShowroomErrorCode + 可选 details
 * （422101 字段级 fields 字典 / 409103 reason 二分，线上装入 R.data）。
 * 约束: error-strategy 分层错误处理（应用层抛领域异常 → 表示层映射）。
 */
@Getter
public class ShowroomException extends RuntimeException {

    private final ShowroomErrorCode errorCode;
    private final transient Map<String, Object> details;

    public ShowroomException(ShowroomErrorCode errorCode) {
        this(errorCode, null);
    }

    public ShowroomException(ShowroomErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.name() + "(" + errorCode.getCode() + ")");
        this.errorCode = errorCode;
        this.details = details;
    }

    /** 422101 字段级校验失败便捷构造：details = { fields: { field: reason_key } } */
    public static ShowroomException fieldValidation(Map<String, String> fields) {
        return new ShowroomException(ShowroomErrorCode.FIELD_VALIDATION_FAILED, Map.of("fields", fields));
    }

    public static ShowroomException fieldValidation(String field, String reasonKey) {
        return fieldValidation(Map.of(field, reasonKey));
    }
}
