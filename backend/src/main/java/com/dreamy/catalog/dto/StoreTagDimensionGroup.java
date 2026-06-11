package com.dreamy.catalog.dto;

import java.util.List;

/**
 * 消费端标签导航分组（按维度分组，仅 status=enabled；name 已按 locale 解析——MAP-CAT-007）。
 * L2 TRACE: MAP-CAT-007 / openapi StoreTagDimensionGroup。
 */
public record StoreTagDimensionGroup(
        Long id,
        String name,
        String description,
        List<StoreTagItem> tags
) {
    public record StoreTagItem(Long id, String name, String cover, Integer productCount) {
    }
}
