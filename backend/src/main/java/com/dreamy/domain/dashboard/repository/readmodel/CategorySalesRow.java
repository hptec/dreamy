package com.dreamy.domain.dashboard.repository.readmodel;

import lombok.Data;

import java.math.BigDecimal;

/**
 * RM-ANA-008 品类销售聚合行（根品类口径，三层树溯根）。
 * rootCategoryId/rootCategoryName 可为 null（商品/品类已删除溯根断链行）→ MAP-ANA-003 落 Other 桶。
 */
@Data
public class CategorySalesRow {

    private Long rootCategoryId;

    private String rootCategoryName;

    /** USD 基准销售额 */
    private BigDecimal amountUsd;
}
