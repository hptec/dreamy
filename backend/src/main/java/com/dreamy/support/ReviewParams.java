package com.dreamy.support;

import com.dreamy.enums.ReviewSort;

/**
 * review 域查询参数解析/校验工具（V-REV-001~003/008~009/014~019/031~033 共用；
 * 非法 → ReviewFieldErrors 收集 422801，与 catalog StoreParams 同口径）。
 */
public final class ReviewParams {

    private ReviewParams() {
    }

    /** V-REV-001/008 product_id 必填正整数 int64（缺/非法 → fields.product_id=required|invalid） */
    public static Long parseRequiredProductId(Long productId, ReviewFieldErrors errors) {
        if (productId == null) {
            errors.reject("product_id", "required");
            return null;
        }
        if (productId <= 0) {
            errors.reject("product_id", "invalid");
            return null;
        }
        return productId;
    }

    /** V-REV-018/032 可选正整数 int64 */
    public static Long parsePositiveId(Long id, String field, ReviewFieldErrors errors) {
        if (id == null) {
            return null;
        }
        if (id <= 0) {
            errors.reject(field, "range_invalid");
            return null;
        }
        return id;
    }

    /** V-REV-003 page >= 1 缺省 1 */
    public static int parsePage(Integer page, ReviewFieldErrors errors) {
        if (page == null) {
            return 1;
        }
        if (page < 1) {
            errors.reject("page", "range_invalid");
            return 1;
        }
        return page;
    }

    /** V-REV-003 page_size 1..100 缺省 20 */
    public static int parsePageSize(Integer pageSize, ReviewFieldErrors errors) {
        if (pageSize == null) {
            return 20;
        }
        if (pageSize < 1 || pageSize > 100) {
            errors.reject("page_size", "range_invalid");
            return 20;
        }
        return pageSize;
    }

    /** V-REV-002 sort 枚举缺省 featured_first（枚举外 → fields.sort=invalid_enum） */
    public static ReviewSort parseSort(String sort, ReviewFieldErrors errors) {
        if (sort == null || sort.isBlank()) {
            return ReviewSort.FEATURED_FIRST;
        }
        ReviewSort parsed = ReviewSort.of(sort);
        if (parsed == null) {
            errors.reject("sort", "invalid_enum");
            return ReviewSort.FEATURED_FIRST;
        }
        return parsed;
    }

    /** V-REV-019 search ≤80（trim 后空视为未提供） */
    public static String parseSearch(String search, ReviewFieldErrors errors) {
        if (search == null) {
            return null;
        }
        String trimmed = search.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 80) {
            errors.reject("search", "too_long");
            return null;
        }
        return trimmed;
    }
}
