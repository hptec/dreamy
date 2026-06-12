package com.dreamy.support;

import com.dreamy.error.ShowroomException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 422101 字段级错误收集器（showroom-api-detail §0 横切：details = { fields: { <field>: <reason_key> } }）。
 * store 端 reason_key 由前端 next-intl 字典渲染（决策 27）。
 */
public class ShowroomFieldErrors {

    private final Map<String, String> fields = new LinkedHashMap<>();

    public ShowroomFieldErrors reject(String field, String reasonKey) {
        fields.putIfAbsent(field, reasonKey);
        return this;
    }

    public boolean hasErrors() {
        return !fields.isEmpty();
    }

    public Map<String, String> fields() {
        return fields;
    }

    /** 有错即抛 422101 */
    public void throwIfAny() {
        if (hasErrors()) {
            throw ShowroomException.fieldValidation(fields);
        }
    }
}
