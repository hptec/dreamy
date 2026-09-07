package com.dreamy.domain.order.consts;

import com.dreamy.consts.TradingCommonDBConst;

/** orders 表列名常量（表名取 orders 规避 MySQL 保留字 ORDER）。L2 TRACE: trading-data-detail §9 DDL-4 */
public interface OrderDBConst extends TradingCommonDBConst {

    String TABLE = "orders";

    String ORDER_NO = "order_no";
    /** 下单时语言环境快照 en/es/fr（决策13 / FUNC-020，邮件三语用，V20260616 增量加列） */
    String LOCALE_SNAPSHOT = "locale_snapshot";
    String EXCHANGE_RATE = "exchange_rate";
    String WEDDING_DATE = "wedding_date";
    String SUBTOTAL = "subtotal";
    String SHIPPING_FEE = "shipping_fee";
    String GIFT_WRAP = "gift_wrap";
    String GIFT_WRAP_FEE = "gift_wrap_fee";
    String DISCOUNT_AMOUNT = "discount_amount";
    String TOTAL_AMOUNT = "total_amount";
    String COUPON_ID = "coupon_id";
    String PAYMENT_METHOD = "payment_method";
    String ADDRESS_SNAPSHOT = "address_snapshot";
    String CARRIER = "carrier";
    String TRACKING_NO = "tracking_no";
    String IDEMPOTENCY_KEY = "idempotency_key";
    String EXPIRES_AT = "expires_at";
    String SHIPPED_AT = "shipped_at";
    String COMPLETED_AT = "completed_at";
    String DELIVERED_AT = "delivered_at";
    String TAX_AMOUNT = "tax_amount";
    String TAX_BREAKDOWN = "tax_breakdown";
    String INCOTERM = "incoterm";
    String REFUNDED_AMOUNT = "refunded_amount";
    String PRODUCTION_STAGE = "production_stage";
    String SHIPPING_SERVICE_LEVEL = "shipping_service_level";
    String ESTIMATED_DELIVERY_FROM = "estimated_delivery_from";
    String ESTIMATED_DELIVERY_TO = "estimated_delivery_to";
    String AMOUNT_VERSION = "amount_version";
}
