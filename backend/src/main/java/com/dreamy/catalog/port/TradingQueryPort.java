package com.dreamy.catalog.port;

import java.time.LocalDateTime;
import java.util.List;

/**
 * trading 领域查询端口（进程内直调，决策 3；EVT-CAT-001/003 销量重算数据源）。
 * trading 域未实现前由 StubTradingQueryPort 兜底（@ConditionalOnMissingBean，CatalogPortConfig）；
 * trading 域落地后提供同名 bean 自动替换——依赖标注见 traceability/catalog-backend.yml。
 */
public interface TradingQueryPort {

    /** 商品近 since 起的已支付销量（EVT-CAT-001 ③ 30 天滚动窗口重算） */
    int sumPaidQty(Long productId, LocalDateTime since);

    /** 近 since 起有已支付订单的商品 id 去重集合（EVT-CAT-003 每日窗口刷新候选） */
    List<Long> listPaidProductIds(LocalDateTime since);
}
