package com.dreamy.trading.domain.payment.consts;

import com.dreamy.trading.domain.consts.TradingCommonDBConst;

/** processed_event 表列名常量。L2 TRACE: trading-data-detail §9 DDL-12 */
public interface ProcessedEventDBConst extends TradingCommonDBConst {

    String TABLE = "processed_event";

    String EVENT_ID = "event_id";
    String EVENT_TYPE = "event_type";
    String RECEIVED_AT = "received_at";
}
