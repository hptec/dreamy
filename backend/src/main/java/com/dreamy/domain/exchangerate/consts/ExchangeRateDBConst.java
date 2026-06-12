package com.dreamy.domain.exchangerate.consts;

import com.dreamy.consts.TradingCommonDBConst;

/** exchange_rate 表列名常量。L2 TRACE: trading-data-detail §9 DDL-10 */
public interface ExchangeRateDBConst extends TradingCommonDBConst {

    String TABLE = "exchange_rate";

    String RATE = "rate";
    String UPDATED_BY = "updated_by";
}
