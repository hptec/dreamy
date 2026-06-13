package com.dreamy.dto;

import com.dreamy.dto.TranslationDtos.ProductTranslationDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 后台商品整单提交载荷（E-CAT-09/11；status/kind 等枚举为字符串，V-CAT 校验器落 422501）。
 * updated_at 为编辑端可选回传（无 SKU 纯定制商品的并发防丢失比对，E-CAT-11 STEP-CAT-03 → 409508）。
 * L2 TRACE: openapi AdminProductUpsert / V-CAT-023~038。
 */
public record AdminProductUpsert(
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
        List<Long> tagIds,
        List<ProductTranslationDto> translations,
        String updatedAt
) {
}
