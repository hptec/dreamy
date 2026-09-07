package com.dreamy.domain.shipment.consts;

import com.dreamy.consts.TradingCommonDBConst;

/** shipment_line 表列名常量（order-flow-complete §2.2）。 */
public interface ShipmentLineDBConst extends TradingCommonDBConst {

    String TABLE = "shipment_line";

    String SHIPMENT_ID = "shipment_id";
    String ORDER_LINE_ID = "order_line_id";
}
