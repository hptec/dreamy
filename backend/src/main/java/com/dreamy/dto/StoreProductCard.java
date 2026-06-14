package com.dreamy.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 消费端商品卡片（列表/推荐位复用；name/sellingPoints 已按 locale 解析）。
 * 不暴露 sort/recommend/sales_30d/status（MAP-CAT-001）。
 * L2 TRACE: MAP-CAT-001 / openapi StoreProductCard。
 */
public record StoreProductCard(
        Long id,
        String slug,
        String name,
        BigDecimal price,
        BigDecimal compareAt,
        Map<String, BigDecimal> multiCurrencyPrices,
        Boolean installment,
        Boolean isNew,
        Boolean isBest,
        String imageUrl,
        List<Swatch> swatches,
        BigDecimal ratingAvg,
        Integer ratingCount,
        List<String> sellingPoints
) {
    /** 色样（kind=swatch 图片派生） */
    public record Swatch(String colorName, String url) {
    }
}
