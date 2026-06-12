package com.dreamy.domain.payment.consts;

import com.dreamy.consts.TradingCommonDBConst;

/** processed_event 表列名常量。L2 TRACE: trading-data-detail §9 DDL-12 */
public interface ProcessedEventDBConst extends TradingCommonDBConst {

    String TABLE = "processed_event";

    String EVENT_ID = "event_id";
    String EVENT_TYPE = "event_type";
    String RECEIVED_AT = "received_at";
}
