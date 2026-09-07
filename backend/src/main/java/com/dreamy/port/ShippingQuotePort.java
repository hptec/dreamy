package com.dreamy.port;

import java.math.BigDecimal;
import java.util.List;

/**
 * 多承运商运费报价端口（提供侧权威定义点，shipping-api-detail §10.1；
 * 与 trading-api-detail §0 消费侧声明逐字一致——trading 仅注入本接口，禁止直查本域表，决策 3）。
 * order-flow-complete E：每个启用承运商 × 服务等级（STANDARD/EXPRESS）至多一项。
 */
public interface ShippingQuotePort {

    /**
     * 多承运商运费报价（FLOW-P05 进程内同步直调，只读，无副作用）。
     *
     * @param country     收货国家（ISO 3166-1 alpha-2 码或国家英文名，内部规范化，见 GeoZoneResolver）
     * @param subtotalUsd 购物车小计（USD 基准口径，决策 14）
     * @return 每个 status=enabled 的 Carrier × service_level 至多一项；可能为空列表
     */
    List<ShippingOptionQuote> quoteOptions(String country, BigDecimal subtotalUsd);

    /** 收货国家 → zone 码（后台试算展示用） */
    default String resolveZone(String country) {
        return com.dreamy.domain.shippingrate.service.GeoZoneResolver.resolve(country);
    }
}
