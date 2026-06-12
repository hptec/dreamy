package com.dreamy.dto;

import java.util.List;

/**
 * 消费端分类树节点（name 已按 locale 解析；product_count 仅 published 口径；
 * 不暴露 attribute_set_id/attr_overrides——MAP-CAT-005）。
 * L2 TRACE: MAP-CAT-005 / openapi StoreCategoryNode。
 */
public record StoreCategoryNode(
        Long id,
        String name,
        Long parentId,
        Integer level,
        Integer sort,
        Integer productCount,
        List<StoreCategoryNode> children
) {
}
