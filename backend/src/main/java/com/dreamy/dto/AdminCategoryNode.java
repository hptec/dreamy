package com.dreamy.dto;

import com.dreamy.dto.TranslationDtos.CategoryTranslationDto;

import java.util.List;
import java.util.Map;

/**
 * 后台分类树节点（全字段 + product_count 全量口径 + translations 三语 tab + children——MAP-CAT-006；
 * product_count 为 js_guard canDelete 数据源）。
 * L2 TRACE: MAP-CAT-006 / openapi AdminCategoryNode。
 */
public record AdminCategoryNode(
        Long id,
        String name,
        Long parentId,
        Long attributeSetId,
        Map<String, Integer> attrOverrides,
        Integer sort,
        Integer level,
        Integer productCount,
        List<AdminCategoryNode> children,
        List<CategoryTranslationDto> translations
) {
}
