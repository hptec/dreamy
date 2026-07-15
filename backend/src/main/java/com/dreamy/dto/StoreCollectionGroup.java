package com.dreamy.dto;

import java.util.List;

/**
 * 消费端集合导航分组（按分组聚合，仅 status=enabled；name 已按 locale 解析——MAP-CAT-007）。
 * L2 TRACE: MAP-CAT-007 / openapi StoreCollectionGroup。
 */
public record StoreCollectionGroup(
        Long id,
        String name,
        String description,
        List<StoreCollectionItem> collections
) {
    public record StoreCollectionItem(Long id, String name, Integer productCount, String imageUrl) {
    }
}
