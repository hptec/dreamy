package com.dreamy.catalog.error;

import lombok.Getter;

import java.util.Map;

/**
 * catalog 域业务异常。携带 CatalogErrorCode + 可选 details（422501 字段级 fields 字典 /
 * 409504 sku_codes / 409502 product_count·reason 等，线上装入 R.data）。
 * 约束: error-strategy 分层错误处理（应用层抛领域异常 → 表示层映射）。
 */
@Getter
public class CatalogException extends RuntimeException {

    private final CatalogErrorCode errorCode;
    private final transient Map<String, Object> details;

    public CatalogException(CatalogErrorCode errorCode) {
        this(errorCode, null);
    }

    public CatalogException(CatalogErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.name() + "(" + errorCode.getCode() + ")");
        this.errorCode = errorCode;
        this.details = details;
    }

    /** 422501 字段级校验失败便捷构造：details = { fields: { field: reason_key } } */
    public static CatalogException fieldValidation(Map<String, String> fields) {
        return new CatalogException(CatalogErrorCode.FIELD_VALIDATION_FAILED, Map.of("fields", fields));
    }

    public static CatalogException fieldValidation(String field, String reasonKey) {
        return fieldValidation(Map.of(field, reasonKey));
    }
}
