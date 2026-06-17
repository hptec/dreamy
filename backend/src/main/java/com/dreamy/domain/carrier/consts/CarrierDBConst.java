package com.dreamy.domain.carrier.consts;

import com.dreamy.consts.ShippingCommonDBConst;

/** carrier 表列名常量（CP-015）。L2 TRACE: shipping-data-detail §8.1 DDL-1 */
public interface CarrierDBConst extends ShippingCommonDBConst {

    String TABLE = "carrier";

    String NAME = "name";
    String ZONES = "zones";
    String LEAD_TIME = "lead_time";
    public static final String DELETED_AT = "deleted_at";
    // STATUS 继承自 ShippingCommonDBConst
}
