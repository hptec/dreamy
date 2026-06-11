package com.dreamy.analytics.repository.readmodel;

import lombok.Data;

import java.math.BigDecimal;

/** RM-ANA-009 Top 商品聚合行（name/img 取 order_line 快照列，DEC-ANA-8 不回查 product）。 */
@Data
public class TopProductRow {

    private Long productId;

    /** 快照商品名（MAX(product_name)） */
    private String productName;

    /** 快照图（可为 null/空串 → MAP-ANA-004 image_url=null） */
    private String img;

    /** SUM(qty) 已支付销量 */
    private Long sales;

    /** USD 基准销售额 */
    private BigDecimal amountUsd;
}
