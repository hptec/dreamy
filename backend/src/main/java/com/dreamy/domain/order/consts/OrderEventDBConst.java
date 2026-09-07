package com.dreamy.domain.order.consts;

import com.dreamy.consts.TradingCommonDBConst;

/** order_event 表列名常量（订单时间线；order-flow-complete §2.2）。 */
public interface OrderEventDBConst extends TradingCommonDBConst {

    String TABLE = "order_event";

    String TYPE = "type";
    String ACTOR_TYPE = "actor_type";
    String ACTOR_ID = "actor_id";
    String TITLE = "title";
    String DETAIL = "detail";
    String PAYLOAD = "payload";
    String CUSTOMER_VISIBLE = "customer_visible";
}
