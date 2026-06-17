package com.dreamy.domain.flashsale.consts;

import com.dreamy.consts.MarketingCommonDBConst;

/** flash_sale 表列名常量（CP-015）。L2 TRACE: marketing-data-detail §11 DDL-15 */
public interface FlashSaleDBConst extends MarketingCommonDBConst {

    String TABLE = "flash_sale";

    String DISCOUNT = "discount";
    public static final String DELETED_AT = "deleted_at";
}
