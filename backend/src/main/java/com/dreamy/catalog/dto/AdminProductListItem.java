package com.dreamy.catalog.dto;

import java.math.BigDecimal;

/**
 * 后台商品列表行（含派生列 category_name/stock_total/image_url——MAP-CAT-003）。
 * L2 TRACE: MAP-CAT-003 / openapi AdminProductListItem。
 */
public record AdminProductListItem(
        Long id,
        String name,
        String slug,
        String styleNo,
        Long categoryId,
        String categoryName,
        BigDecimal price,
        BigDecimal compareAt,
        String status,
        Boolean isNew,
        Boolean isBest,
        Boolean recommend,
        Integer sort,
        Integer stockTotal,
        String imageUrl
) {
}
