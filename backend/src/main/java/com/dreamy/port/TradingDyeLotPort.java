package com.dreamy.port;

import java.util.List;

/**
 * showroom 领域 dye lot 提示端口（trading-api-detail §0，决策 20.4：所属 Showroom 内 24h 窗口
 * 存在同款式已付订单的商品 id 集合）。
 * showroom 域未提供 bean 时由 TradingPortConfig stub 兜底（@ConditionalOnMissingBean，恒返回空数组——
 * getCart.STEP-TRD-03「showroom 域空结果返回空数组」口径），showroom 域落地后自动让位。
 */
public interface TradingDyeLotPort {

    /** 命中 dye lot 提示的商品 id（空集 = 无提示） */
    List<Long> hintProductIds(Long customerId, List<Long> productIds);
}
