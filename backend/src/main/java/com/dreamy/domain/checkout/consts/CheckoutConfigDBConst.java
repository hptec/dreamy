package com.dreamy.domain.checkout.consts;

import com.dreamy.consts.TradingCommonDBConst;

/** checkout_config 表列名常量。L2 TRACE: trading-data-detail §9 DDL-11 */
public interface CheckoutConfigDBConst extends TradingCommonDBConst {

    String TABLE = "checkout_config";

    String GIFT_WRAP_FEE_USD = "gift_wrap_fee_usd";
    String CUSTOM_REFUND_GRACE_HOURS = "custom_refund_grace_hours";
    String AUTO_COMPLETE_DAYS = "auto_complete_days";
    String AUTO_DELIVER_DAYS = "auto_deliver_days";
    String PENDING_TIMEOUT_MINUTES = "pending_timeout_minutes";
    String EXCHANGE_RATE_SPREAD_SCALED = "exchange_rate_spread_scaled";
    String PRODUCTION_DAYS_DEFAULT = "production_days_default";
}
