package com.dreamy.port;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * catalog 领域商品/SKU 快照端口（trading-api-detail §0 跨域端口，决策 3 进程内直调防腐层，禁止跨域直查表）。
 * 用途：①购物车/收藏/浏览历史 ProductBrief 装配（按 locale 解析文案，MAP-TRD-001/009，批量防 N+1）
 * ②商品存在性/published 校验（V-TRD-002/037/042 → 404501 透传）③结算行价/交期快照（FLOW-P05/P06）。
 * catalog 域未提供同名 bean 时由 TradingPortConfig 基于 catalog Mapper 的只读适配实现兜底
 * （@ConditionalOnMissingBean，与 review/marketing 端口同范式）。
 */
public interface TradingCatalogSnapshotPort {

    /** 单查商品简况（locale 解析文案）；不存在返回 null */
    ProductBrief getProductBrief(Long productId, String locale);

    /** 批量装配（一个 ids 集合一次调用，禁止逐 id 循环）；缺失商品不入结果 */
    Map<Long, ProductBrief> getProductBriefs(Collection<Long> productIds, String locale);

    /** 单查 SKU 展示/库存数据；不存在返回 null */
    SkuBrief getSku(Long skuId);

    /** 批量 SKU */
    Map<Long, SkuBrief> getSkus(Collection<Long> skuIds);

    /**
     * 商品简况（契约 ProductBrief schema；price USD 基准，multi_currency_prices 覆盖价，
     * status=draft 标记不可购买；image_url 主图派生 gallery sort=0）。
     */
    record ProductBrief(Long id, String slug, String name, BigDecimal price,
                        BigDecimal compareAt, Map<String, BigDecimal> multiCurrencyPrices, String imageUrl,
                        Integer leadTimeDays, Boolean rushAvailable, Boolean customSizeAvailable,
                        Integer status) implements Serializable {

        public boolean published() {
            return com.dreamy.enums.ProductStatus.PUBLISHED.getKey().equals(status);
        }
    }

    /** SKU 简况（含 version 供乐观锁 CAS 重读，stock 供前端超量提示） */
    record SkuBrief(Long id, Long productId, String skuCode, String color, String size,
                    Integer stock, Long version) implements Serializable {
    }
}
