package com.dreamy.catalog.support;

import com.dreamy.catalog.error.CatalogException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 422501 字段级错误收集器（api-detail §0 横切：details = { fields: { <field>: <reason_key> } }）。
 * store 端 reason_key 由前端 next-intl 字典渲染，admin 端后端直出中文由前端 toast。
 */
public class FieldErrors {

    private final Map<String, String> fields = new LinkedHashMap<>();

    public FieldErrors reject(String field, String reasonKey) {
        fields.putIfAbsent(field, reasonKey);
        return this;
    }

    public boolean hasErrors() {
        return !fields.isEmpty();
    }

    public Map<String, String> fields() {
        return fields;
    }

    /** 有错即抛 422501 */
    public void throwIfAny() {
        if (hasErrors()) {
            throw CatalogException.fieldValidation(fields);
        }
    }
}
