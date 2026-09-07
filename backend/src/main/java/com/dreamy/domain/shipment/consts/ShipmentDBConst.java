package com.dreamy.domain.shipment.consts;

import com.dreamy.consts.TradingCommonDBConst;

/** shipment 表列名常量（order-flow-complete §2.2）。 */
public interface ShipmentDBConst extends TradingCommonDBConst {

    String TABLE = "shipment";

    String SHIPMENT_NO = "shipment_no";
    String CARRIER_CODE = "carrier_code";
    String CARRIER_NAME = "carrier_name";
    String TRACKING_NO = "tracking_no";
    String TRACKING_URL = "tracking_url";
    String SHIPPED_AT = "shipped_at";
    String DELIVERED_AT = "delivered_at";
    String LAST_EVENT_AT = "last_event_at";
    String LAST_EVENT_DESC = "last_event_desc";
    String PROVIDER_REF = "provider_ref";
    String IDEMPOTENCY_KEY = "idempotency_key";
    String SYNCED_AT = "synced_at";
    String SYNC_FAILURES = "sync_failures";
}
