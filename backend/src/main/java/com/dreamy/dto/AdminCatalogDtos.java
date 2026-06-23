package com.dreamy.dto;

import com.dreamy.dto.TranslationDtos.AttributeDefTranslationDto;
import com.dreamy.dto.TranslationDtos.CollectionGroupTranslationDto;
import com.dreamy.dto.TranslationDtos.CollectionTranslationDto;

import java.util.List;

/**
 * 属性集/属性字典/集合分组/集合 后台 DTO 集（MAP-CAT-008/009/010）。
 */
public final class AdminCatalogDtos {

    private AdminCatalogDtos() {
    }

    /** openapi AttributeSetItem（visibility 字符串，V-CAT-050 校验三态） */
    public record AttributeSetItemDto(Long attributeId, Integer visibility) {
    }

    /** openapi AttributeSetUpsert（V-CAT-049~051） */
    public record AttributeSetUpsert(String label, List<AttributeSetItemDto> items) {
    }

    /** openapi AttributeSet（category_count 派生——删除约束判定数据源，MAP-CAT-008） */
    public record AttributeSetDto(Long id, String label, List<AttributeSetItemDto> items, Integer categoryCount) {
    }

    /** openapi AttributeDefUpsert（V-CAT-053~058） */
    public record AttributeDefUpsert(String key, String label, Integer type, List<String> options,
                                     List<AttributeDefTranslationDto> translations) {
    }

    /** openapi AttributeDef（MAP-CAT-009） */
    public record AttributeDefDto(Long id, String key, String label, Integer type, List<String> options,
                                  List<AttributeDefTranslationDto> translations) {
    }

    /** openapi CollectionGroupUpsert（V-CAT-059/060） */
    public record CollectionGroupUpsert(String name, String description,
                                        List<CollectionGroupTranslationDto> translations) {
    }

    /** openapi CollectionGroup（collection_count 派生——删除约束判定） */
    public record CollectionGroupDto(Long id, String name, String description, Integer collectionCount,
                                     List<CollectionGroupTranslationDto> translations) {
    }

    /** openapi CollectionUpsert（V-CAT-063~066） */
    public record CollectionUpsert(Long collectionGroupId, String name, String cover, Integer status,
                                   List<CollectionTranslationDto> translations) {
    }

    /** openapi Collection（product_count 全量口径——MAP-CAT-010；fallback_cover_url 为 cover 缺失时回退到集合内首个商品主图） */
    public record CollectionDto(Long id, Long collectionGroupId, String name, String cover, Integer status,
                                Integer productCount, String fallbackCoverUrl,
                                List<CollectionTranslationDto> translations) {
    }

    /** openapi CollectionProduct（集合内商品项，含主图与 sort） */
    public record CollectionProductDto(Long productId, String name, String slug, Integer status,
                                       String imageUrl, Integer sort) {
    }

    /** openapi CollectionProductsUpsert（按入参顺序写 sort，全量覆盖） */
    public record CollectionProductsUpsert(List<Long> productIds) {
    }
}
