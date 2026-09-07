package com.dreamy.port;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * catalog 领域商品快照查询端口（进程内直调，决策 3；review-data-detail §8.3）。
 * 用途：①商品存在性/published 校验（V-REV-004/010 → 404501 透传）②admin 列表 product_name 派生
 * （NP-REV-001 批量防 N+1，商品已删除容忍 null）③失效事件 slug 取得（EVT-REV-002）。
 * catalog 域未提供同名 bean 时由 ReviewPortConfig 基于 catalog ProductRepository 的只读适配实现兜底
 * （@ConditionalOnMissingBean，catalog 域后续提供真实 bean 自动让位）。
 */
public interface ReviewCatalogSnapshotPort {

    /** 单查商品简况；不存在返回 null */
    ProductBrief getProductBrief(Long productId);

    /** 批量装配（一个 ids 集合一次调用，禁止逐 id 循环——NP-REV-001）；缺失商品不入结果 */
    Map<Long, ProductBrief> getProductBriefs(Collection<Long> productIds);

    /**
     * 按关键词模糊匹配商品名，返回商品 id 集合（admin 评价搜索扩展用；
     * 上限保护由实现负责，空关键词返回空集）。
     */
    Set<Long> searchProductIdsByKeyword(String keyword);

    /** 商品简况 {id, slug, name, published}（review-api-detail §0 端口契约） */
    record ProductBrief(Long id, String slug, String name, boolean published) {
    }
}
