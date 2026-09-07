package com.dreamy.port;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 报价项（MAP-SHP-003，字段与 trading shipping_options 组装直接对应；order-flow-complete E 尾部追加
 * carrier_code / service_level / transit_days）。
 *
 * @param carrier        Carrier.name（= Order.carrier 快照值）
 * @param feeUsd         USD 基准运费，scale=2 HALF_UP（trading 侧负责换算订单币种）
 * @param leadTime       Carrier.lead_time 时效描述（可为 null，trading 透传）
 * @param carrierCode    Carrier.code（FEDEX/UPS/DHL/USPS）
 * @param serviceLevel   1=STANDARD 2=EXPRESS
 * @param transitDaysMin 运输天数下限
 * @param transitDaysMax 运输天数上限
 */
public record ShippingOptionQuote(
        String carrier,
        BigDecimal feeUsd,
        String leadTime,
        String carrierCode,
        Integer serviceLevel,
        Integer transitDaysMin,
        Integer transitDaysMax
) implements Serializable {

    /** 兼容旧三参构造（STANDARD、无运输天数） */
    public ShippingOptionQuote(String carrier, BigDecimal feeUsd, String leadTime) {
        this(carrier, feeUsd, leadTime, null, 1, null, null);
    }
}
