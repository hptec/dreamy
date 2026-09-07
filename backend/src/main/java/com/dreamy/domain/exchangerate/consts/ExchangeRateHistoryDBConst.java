package com.dreamy.domain.exchangerate.consts;

import com.dreamy.consts.TradingCommonDBConst;

/** exchange_rate_history 表列名常量（order-flow-complete §2.2）。 */
public interface ExchangeRateHistoryDBConst extends TradingCommonDBConst {

    String TABLE = "exchange_rate_history";

    String BASE_CURRENCY = "base_currency";
    String RATE = "rate";
    String SOURCE = "source";
    String QUOTE_DATE = "quote_date";
    String RECORDED_AT = "recorded_at";
}
