package com.dreamy.catalog.dto;

import com.dreamy.catalog.dto.TranslationDtos.ProductTranslationDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 后台商品编辑详情（AdminProductUpsert 全字段回显 + id/created_at/updated_at；
 * translations 三语 tab 原样不回退合并——MAP-CAT-004）。
 * L2 TRACE: MAP-CAT-004 / openapi AdminProductDetail。
 */
public record AdminProductDetail(
        Long id,
        String name,
        String slug,
        String subtitle,
        Long categoryId,
        String productType,
        String description,
        String designerNote,
        BigDecimal price,
        BigDecimal compareAt,
        Boolean installment,
        Map<String, BigDecimal> multiCurrencyPrices,
        String status,
        Boolean isNew,
        Boolean isBest,
        Boolean recommend,
        Integer sort,
        Integer leadTimeDays,
        Boolean rushAvailable,
        Boolean customSizeAvailable,
        String silhouette,
        String neckline,
        String sleeve,
        String backStyle,
        String waistline,
        String train,
        String length,
        String fabric,
        String fabricComposition,
        String support,
        String season,
        List<String> embellishments,
        List<String> occasions,
        List<String> styleTags,
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
        List<Long> tagIds,
        List<ProductTranslationDto> translations,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
