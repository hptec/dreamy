package com.dreamy.port;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    /**
     * 商品累计销量批量聚合（admin-prototype-alignment RM-CAT-01：
     * sales_total = SUM(order_line.qty) WHERE order.status IN (paid, shipped, completed, refunding, refunded)；
     * cancelled/pending 不计）。一次 IN 聚合防 N+1（RM-CAT-01b）；缺失 product_id 由调用方补 0（RM-CAT-01c）。
     */
    Map<Long, Integer> sumSalesTotalByProductIds(Collection<Long> productIds);
}
