package com.dreamy.domain.outbox.consts;

import com.dreamy.consts.TradingCommonDBConst;

/** event_outbox 表列名常量（事务性发件箱；order-flow-complete §2.2/§4.4）。 */
public interface EventOutboxDBConst extends TradingCommonDBConst {

    String TABLE = "event_outbox";

    String EVENT_TYPE = "event_type";
    String EVENT_ID = "event_id";
    String ROUTING_KEY = "routing_key";
    String PAYLOAD = "payload";
    String ATTEMPTS = "attempts";
    String NEXT_ATTEMPT_AT = "next_attempt_at";
    String LAST_ERROR = "last_error";
    String SENT_AT = "sent_at";
}
