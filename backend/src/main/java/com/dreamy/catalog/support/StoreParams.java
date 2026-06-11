package com.dreamy.catalog.support;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * store 端查询参数解析/校验工具（V-CAT-001~011/017~019 共用；非法 → FieldErrors 收集 422501）。
 */
public final class StoreParams {

    public static final Set<String> LOCALES = Set.of("en", "es", "fr");
    public static final List<String> SORTS = List.of("newest", "price_asc", "price_desc", "recommended");

    private StoreParams() {
    }

    /** V-CAT-001 locale ∈ {en,es,fr} 缺省 en（枚举外 → fields.locale=invalid_enum） */
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

    /** V-CAT-002 page >= 1 缺省 1 */
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

    /** V-CAT-002 page_size 1..100 缺省 20 */
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

    /** V-CAT-003 price_min/max >= 0 且 min <= max（违反 → fields.price_min=range_invalid） */
    public static void validatePriceRange(BigDecimal priceMin, BigDecimal priceMax, FieldErrors errors) {
        if (priceMin != null && priceMin.signum() < 0) {
            errors.reject("price_min", "range_invalid");
        }
        if (priceMax != null && priceMax.signum() < 0) {
            errors.reject("price_max", "range_invalid");
        }
        if (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) > 0) {
            errors.reject("price_min", "range_invalid");
        }
    }

    /** V-CAT-004 sort 枚举缺省 recommended */
    public static String parseSort(String sort, FieldErrors errors) {
        if (sort == null || sort.isBlank()) {
            return "recommended";
        }
        if (!SORTS.contains(sort)) {
            errors.reject("sort", "invalid_enum");
            return "recommended";
        }
        return sort;
    }

    /** V-CAT-005/019 正整数 int64（非法 → fields.{name}=invalid_type/range_invalid） */
    public static Long parsePositiveId(Long id, String field, FieldErrors errors) {
        if (id == null) {
            return null;
        }
        if (id <= 0) {
            errors.reject(field, "range_invalid");
            return null;
        }
        return id;
    }

    /** maxLength 校验（V-CAT-005 color/size 等） */
    public static String checkMaxLength(String value, int maxLength, String field, FieldErrors errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > maxLength) {
            errors.reject(field, "too_long");
            return null;
        }
        return value;
    }
}
