package com.dreamy.domain.shipment.consts;

import com.dreamy.consts.TradingCommonDBConst;

/** shipment_event 表列名常量（order-flow-complete §2.2）。 */
public interface ShipmentEventDBConst extends TradingCommonDBConst {

    String TABLE = "shipment_event";

    String SHIPMENT_ID = "shipment_id";
    String OCCURRED_AT = "occurred_at";
    String LOCATION = "location";
    String DESCRIPTION = "description";
    String SOURCE = "source";
    String PROVIDER_EVENT_ID = "provider_event_id";
}
