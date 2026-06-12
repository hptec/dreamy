package com.dreamy.marketing.port;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * catalog 领域查询端口（进程内直调，决策 3；SVC-MKT §7「本域消费的跨域接口」）。
 * 语义：仅返回 published 商品的 ProductRef（缺失/下架/删除商品静默剔除——MAP-MKT-012 / CV-MKT-006）；
 * name 按 locale 解析（es/fr 命中 product_translation 覆盖，缺翻译回退 EN）。
 * 存在性校验（V-MKT-035/062/070）由同接口空集比对承载（existsAll 语义）。
 * catalog 域未提供同名 bean 时由 MarketingPortConfig 基于 catalog Repository 的只读适配实现兜底
 * （@ConditionalOnMissingBean，catalog 域后续提供真实 bean 自动让位）。
 */
public interface CatalogQueryPort {

    /** 批量装配 ProductRef（一个 ids 集合一次调用，禁止逐 id 循环——NP-MKT-002） */
    List<ProductRef> listProductRefs(Collection<Long> productIds, String locale);

    /** 引用存在性校验（V-MKT-035/062/070：存在即可，不要求 published——草稿商品可被运营提前挂载） */
    List<Long> listExistingIds(Collection<Long> productIds);

    /** 内容关联商品卡片（openapi ProductRef：id/slug/name/price/image_url，USD 基准价）；Serializable——JetCache Redis 远端层 Java 序列化要求 */
    record ProductRef(Long id, String slug, String name, BigDecimal price, String imageUrl) implements Serializable {
    }
}
