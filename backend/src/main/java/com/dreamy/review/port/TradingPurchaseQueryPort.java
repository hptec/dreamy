package com.dreamy.review.port;

/**
 * trading 领域购买资格查询端口（进程内直调，决策 3；禁止跨域直查 orders 表）。
 * 403801 越权防护唯一数据源（s-756/s-762，E-REV-02 STEP-REV-01）。
 * 落地 SQL（trading 域内实现）：EXISTS(SELECT 1 FROM orders o JOIN order_line ol ON ol.order_id=o.id
 * WHERE o.customer_id=? AND o.status='completed' AND ol.product_id=?)。
 * trading 域未提供 bean 时由 ReviewPortConfig stub 兜底（@ConditionalOnMissingBean，
 * fail-closed 恒 false——403801 为越权防护语义，端口缺席必须拒绝而非放行）。
 */
public interface TradingPurchaseQueryPort {

    /** 当前用户是否存在包含该商品的 completed 订单 */
    boolean hasCompletedOrderContaining(Long customerId, Long productId);
}
