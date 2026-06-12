package com.dreamy.domain.checkout.consts;

import com.dreamy.consts.TradingCommonDBConst;

/** checkout_config 表列名常量。L2 TRACE: trading-data-detail §9 DDL-11 */
public interface CheckoutConfigDBConst extends TradingCommonDBConst {

    String TABLE = "checkout_config";

    String GIFT_WRAP_FEE_USD = "gift_wrap_fee_usd";
    String CUSTOM_REFUND_GRACE_HOURS = "custom_refund_grace_hours";
}
