package com.dreamy.trading.domain.consts;

/**
 * trading 域公共数据库列名常量（CP-015 范式根接口）。
 * 各实体 DBConst extends 本接口；跨表共享语义列统一在此定义，消除硬编码列名。
 * L2 TRACE: trading-data-detail §9 DDL 约定。
 */
public interface TradingCommonDBConst {

    String ID = "id";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";

    /** 消费者逻辑外键 → user.id（BE-DIM-6 隔离基准列） */
    String CUSTOMER_ID = "customer_id";

    /** 商品逻辑外键 → product.id（CP-010） */
    String PRODUCT_ID = "product_id";

    /** SKU 逻辑外键 → sku.id（定制行 NULL，决策 6） */
    String SKU_ID = "sku_id";

    /** 订单逻辑外键 → orders.id */
    String ORDER_ID = "order_id";

    /** 状态列（order/payment/refund 各自枚举语义） */
    String STATUS = "status";

    /** 币种列（USD/EUR/CAD/AUD/GBP，决策 14） */
    String CURRENCY = "currency";

    /** 金额列（订单币种 DECIMAL(12,2)） */
    String AMOUNT = "amount";

    /** 数量列 */
    String QTY = "qty";

    /** 定制尺寸 JSON 列（决策 20.6/24） */
    String CUSTOM_SIZE_DATA = "custom_size_data";

    /** 支付时间 */
    String PAID_AT = "paid_at";
}
