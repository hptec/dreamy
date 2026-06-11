package com.dreamy.marketing.support;

import java.util.Set;

/**
 * store/admin 端查询参数解析/校验工具（V-MKT-001~006/016~018 共用；非法 → FieldErrors 收集 422704）。
 */
public final class MarketingParams {

    public static final Set<String> LOCALES = Set.of("en", "es", "fr");
    public static final Set<String> TRANSLATION_LOCALES = Set.of("es", "fr");

    private MarketingParams() {
    }

    /** V-MKT-002 locale ∈ {en,es,fr} 缺省 en（枚举外 → fields.locale=invalid_enum） */
    public static String parseLocale(String locale, FieldErrors errors) {
        if (locale == null || locale.isBlank()) {
            return "en";
        }
        if (!LOCALES.contains(locale)) {
            errors.reject("locale", "invalid_enum");
            return "en";
        }
        return locale;
    }

    /** V-MKT-004 page >= 1 缺省 1 */
    public static int parsePage(Integer page, FieldErrors errors) {
        if (page == null) {
            return 1;
        }
        if (page < 1) {
            errors.reject("page", "range_invalid");
            return 1;
        }
        return page;
    }

    /** V-MKT-004 page_size 1..100 缺省 20 */
    public static int parsePageSize(Integer pageSize, FieldErrors errors) {
        if (pageSize == null) {
            return 20;
        }
        if (pageSize < 1 || pageSize > 100) {
            errors.reject("page_size", "range_invalid");
            return 20;
        }
        return pageSize;
    }

    /** trim 后空 → null（V-MKT-003/018 search/category 口径） */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** maxLength 校验（trim 后空视为未提供） */
    public static String checkMaxLength(String value, int maxLength, String field, FieldErrors errors) {
        String v = trimToNull(value);
        if (v == null) {
            return null;
        }
        if (v.length() > maxLength) {
            errors.reject(field, "too_long");
            return null;
        }
        return v;
    }
}
