package com.dreamy.trading.domain.payment.consts;

import com.dreamy.trading.domain.consts.TradingCommonDBConst;

/** payment 表列名常量。L2 TRACE: trading-data-detail §9 DDL-6 */
public interface PaymentDBConst extends TradingCommonDBConst {

    String TABLE = "payment";

    String PROVIDER = "provider";
    String PAYMENT_INTENT_ID = "payment_intent_id";
    String CARD_SUMMARY = "card_summary";
}
