package com.dreamy.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 消费端 PDP 全量数据（文案字段已按 locale 解析，缺翻译回退 EN——决策 13）。
 * skus.version 暴露供下单 CAS 与购物车展示（MAP-CAT-002，无敏感性）。
 * L2 TRACE: MAP-CAT-002 / openapi StoreProductDetail。
 */
public record StoreProductDetail(
        Long id,
        String slug,
        String name,
        String subtitle,
        Long categoryId,
        String categoryName,
        String productType,
        String description,
        String designerNote,
        BigDecimal price,
        BigDecimal compareAt,
        Map<String, BigDecimal> multiCurrencyPrices,
        Boolean installment,
        Boolean isNew,
        Boolean isBest,
        Integer leadTimeDays,
        Boolean rushAvailable,
        Boolean customSizeAvailable,
        List<StoreAttributeDtos.StoreAttributeDto> attributes,
        String fabricComposition,
        String modelHeight,
        String modelSize,
        String modelBodyType,
        String careInstructions,
        String countryOfOrigin,
        String styleNo,
        String seoTitle,
        String seoDesc,
        List<ProductImageDto> images,
        List<SkuDto> skus,
        List<SizeChartRowDto> sizeChart,
        List<TagRef> tags,
        BigDecimal ratingAvg,
        Integer ratingCount
) {
    /** 商品挂载标签（已按 locale 解析） */
    public record TagRef(Long id, Long dimensionId, String name) {
    }
}
