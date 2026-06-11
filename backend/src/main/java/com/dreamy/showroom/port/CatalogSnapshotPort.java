package com.dreamy.showroom.port;

import java.util.Collection;
import java.util.Map;

/**
 * catalog 领域商品卡片查询端口（进程内直调，决策 3；showroom-data-detail §8.4）。
 * 用途：①商品存在性/published 校验（V-SHR-013 → 404501 透传）②ShowroomItem.product 内嵌卡片装配
 * （E-SHR-03/08，单次批量防 N+1，NP-SHR-001）③邮件 payload product_name（EVT-SHR-001/002）。
 * name 已按 locale 回退解析输出（决策 13 翻译回退）。
 * catalog 域未提供同名 bean 时由 ShowroomPortConfig 基于 catalog Mapper 的只读适配实现兜底
 * （@ConditionalOnMissingBean，catalog 域后续提供真实 bean 自动让位——review 同范式）。
 */
public interface CatalogSnapshotPort {

    /** 批量装配商品卡片（一个 ids 集合一次调用，禁止逐 id 循环）；缺失商品不入结果 */
    Map<Long, ProductCardBrief> getProductCards(Collection<Long> productIds, String locale);

    /**
     * 商品卡片简况（showroom-api-detail §0 端口契约：
     * {id, slug, name(已按 locale 回退解析), price_usd, image_url, custom_size_available,
     * lead_time_days, status}——status 以 published 布尔承载）。
     */
    record ProductCardBrief(Long id, String slug, String name, java.math.BigDecimal priceUsd,
                            String imageUrl, Boolean customSizeAvailable, Integer leadTimeDays,
                            boolean published) {
    }
}
