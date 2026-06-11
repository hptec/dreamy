package com.dreamy.trading.domain.order.consts;

import com.dreamy.trading.domain.consts.TradingCommonDBConst;

/** order_line 表列名常量。L2 TRACE: trading-data-detail §9 DDL-5 */
public interface OrderLineDBConst extends TradingCommonDBConst {

    String TABLE = "order_line";

    String PRODUCT_NAME = "product_name";
    String SKU_CODE = "sku_code";
    String COLOR = "color";
    String SIZE = "size";
    String UNIT_PRICE = "unit_price";
    String IMG = "img";
}
