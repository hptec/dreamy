package com.dreamy.domain.shippingrate.consts;

import com.dreamy.consts.ShippingCommonDBConst;

/** shipping_option 表列名常量（order-flow-complete §2.3：zone × carrier_code × service_level 运费选项）。 */
public interface ShippingOptionDBConst extends ShippingCommonDBConst {

    String TABLE = "shipping_option";

    String ZONE = "zone";
    String CARRIER_CODE = "carrier_code";
    String SERVICE_LEVEL = "service_level";
    String FEE_UNDER = "fee_under";
    String FEE_OVER = "fee_over";
    String THRESHOLD = "threshold";
    String TRANSIT_DAYS_MIN = "transit_days_min";
    String TRANSIT_DAYS_MAX = "transit_days_max";
    String ENABLED = "enabled";

    /** carrier_code 通配值：适用于任意启用承运商（旧 shipping_rate 无后缀兜底行迁移产物） */
    String CARRIER_ANY = "ANY";
}
