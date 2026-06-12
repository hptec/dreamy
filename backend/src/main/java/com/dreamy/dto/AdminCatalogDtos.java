package com.dreamy.dto;

import com.dreamy.dto.TranslationDtos.AttributeDefTranslationDto;
import com.dreamy.dto.TranslationDtos.TagDimensionTranslationDto;
import com.dreamy.dto.TranslationDtos.TagTranslationDto;

import java.util.List;

/**
 * 属性集/属性字典/标签维度/标签 后台 DTO 集（MAP-CAT-008/009/010）。
 */
public final class AdminCatalogDtos {

    private AdminCatalogDtos() {
    }

    /** openapi AttributeSetItem（visibility 字符串，V-CAT-050 校验三态） */
    public record AttributeSetItemDto(Long attributeId, String visibility) {
    }

    /** openapi AttributeSetUpsert（V-CAT-049~051） */
    public record AttributeSetUpsert(String label, List<AttributeSetItemDto> items) {
    }

    /** openapi AttributeSet（category_count 派生——删除约束判定数据源，MAP-CAT-008） */
    public record AttributeSetDto(Long id, String label, List<AttributeSetItemDto> items, Integer categoryCount) {
    }

    /** openapi AttributeDefUpsert（V-CAT-053~058） */
    public record AttributeDefUpsert(String key, String label, String type, List<String> options,
                                     List<AttributeDefTranslationDto> translations) {
    }

    /** openapi AttributeDef（MAP-CAT-009） */
    public record AttributeDefDto(Long id, String key, String label, String type, List<String> options,
                                  List<AttributeDefTranslationDto> translations) {
    }

    /** openapi TagDimensionUpsert（V-CAT-059/060） */
    public record TagDimensionUpsert(String name, String description,
                                     List<TagDimensionTranslationDto> translations) {
    }

    /** openapi TagDimension（tag_count 派生——删除约束判定） */
    public record TagDimensionDto(Long id, String name, String description, Integer tagCount,
                                  List<TagDimensionTranslationDto> translations) {
    }

    /** openapi TagUpsert（V-CAT-063~066） */
    public record TagUpsert(Long dimensionId, String name, String cover, String status,
                            List<TagTranslationDto> translations) {
    }

    /** openapi Tag（product_count 全量口径——MAP-CAT-010） */
    public record TagDto(Long id, Long dimensionId, String name, String cover, String status,
                         Integer productCount, List<TagTranslationDto> translations) {
    }
}
