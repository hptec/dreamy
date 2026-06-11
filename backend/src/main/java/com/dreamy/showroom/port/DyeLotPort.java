package com.dreamy.showroom.port;

import java.util.Collection;
import java.util.List;

/**
 * dye lot 同染色批次提示端口（决策 20.4；本域提供，trading 域 cart/quote 消费——
 * FLOW-P04/P05 dye_lot_product_ids，trading-api-detail §0 已声明消费意向）。
 * 语义：买家参与的全部 Showroom（自有 + 被绑定）内，productIds 中 24h 窗口（配置
 * dreamy.showroom.dye-lot-window-hours）存在已付订单回写 last_ordered_at 的款式集合；
 * 无参与房/无命中 → 空数组（trading STEP「空结果返回空数组」对齐）。不缓存（CACHE-SHR-001 连带口径）。
 * 接口声明本归 trading port 包（task-allocation SHR-IMPL-PORT 注记）；trading 域并行实现中，
 * 本域先行声明同形接口承载实现（DyeLotService），trading 侧以 @ConditionalOnMissingBean 适配对接。
 * L2 TRACE: showroom-data-detail §8.3 / RM-SHR-010/025 / CV-SHR-011。
 */
public interface DyeLotPort {

    /** 24h 窗口内同房同款式已付订单命中的 product_id 集合 */
    List<Long> hintProductIds(Long customerId, Collection<Long> productIds);
}
