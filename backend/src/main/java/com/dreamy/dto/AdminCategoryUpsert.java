package com.dreamy.dto;

import com.dreamy.dto.TranslationDtos.CategoryTranslationDto;

import java.util.List;
import java.util.Map;

/**
 * 后台分类提交载荷（parent_id 空=根分类须绑 attribute_set_id——V-CAT-044；
 * attr_overrides 仅子分类 delta——V-CAT-047）。
 * L2 TRACE: openapi AdminCategoryUpsert / V-CAT-043~048。
 */
public record AdminCategoryUpsert(
        String name,
        Long parentId,
        Long attributeSetId,
        Map<String, Integer> attrOverrides,
        Integer sort,
        List<CategoryTranslationDto> translations
) {
}
