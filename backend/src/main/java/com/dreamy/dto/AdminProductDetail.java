package com.dreamy.dto;

import com.dreamy.dto.TranslationDtos.ProductTranslationDto;

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
        Long categoryId,
        String productType,
        String description,
        String designerNote,
        List<String> sellingPoints,
        BigDecimal price,
        BigDecimal compareAt,
        Boolean installment,
        Map<String, BigDecimal> multiCurrencyPrices,
        Integer status,
        Boolean isNew,
        Boolean isBest,
        Boolean recommend,
        Integer sort,
        Integer leadTimeDays,
        Boolean rushAvailable,
        Boolean customSizeAvailable,
        List<AttributeValueDto> attributes,
        String styleNo,
        String seoTitle,
        String seoDesc,
        List<ProductImageDto> images,
        List<SkuDto> skus,
        List<SizeChartRowDto> sizeChart,
        List<Long> collectionIds,
        List<ProductTranslationDto> translations,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        // 面料护理内联（取代专用表）
        List<com.dreamy.domain.product.entity.vo.FabricComposition> fabricCompositions,
        List<com.dreamy.domain.product.entity.vo.CareItem> care,
        String fabricCareNote
) {
}
