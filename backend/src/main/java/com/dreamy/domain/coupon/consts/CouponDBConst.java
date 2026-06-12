package com.dreamy.domain.coupon.consts;

import com.dreamy.consts.MarketingCommonDBConst;

/** coupon 表列名常量（CP-015）。L2 TRACE: marketing-data-detail §11 DDL-13 */
public interface CouponDBConst extends MarketingCommonDBConst {

    String TABLE = "coupon";

    String CODE = "code";
    String TYPE = "type";
    String VALUE = "value";
    String MIN_AMOUNT = "min_amount";
    String TOTAL_LIMIT = "total_limit";
    String USED_COUNT = "used_count";
}
