package com.dreamy.domain.shippingrate.consts;

import com.dreamy.consts.ShippingCommonDBConst;

/** shipping_rate 表列名常量（CP-015）。L2 TRACE: shipping-data-detail §8.1 DDL-2 */
public interface ShippingRateDBConst extends ShippingCommonDBConst {

    String TABLE = "shipping_rate";

    String ZONE = "zone";
    String FEE_UNDER = "fee_under";
    String FEE_OVER = "fee_over";
    String THRESHOLD = "threshold";
}
