package com.dreamy.port;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 报价项（MAP-SHP-003，字段与 trading shipping_options 组装直接对应）。
 *
 * @param carrier  Carrier.name（= Order.carrier 枚举快照值）
 * @param feeUsd   USD 基准运费，scale=2 HALF_UP（trading 侧负责换算订单币种）
 * @param leadTime Carrier.lead_time 时效描述（可为 null，trading 透传）
 */
public record ShippingOptionQuote(
        String carrier,
        BigDecimal feeUsd,
        String leadTime
) implements Serializable {
}
