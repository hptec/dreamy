package com.dreamy.port;

import com.dreamy.domain.showroom.service.DyeLotService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * trading 域 ShowroomDyeLotPort 实现 bean（task-allocation SHR-IMPL-PORT：接口声明归 trading port 包
 * （消费方先例），实现归本域）。trading TradingPortConfig 的 stub（@ConditionalOnMissingBean，
 * 恒空数组）在本 bean 就位后自动让位——cart/quote 的 dye_lot_product_ids 即时生效（FLOW-P04/P05）。
 * 与 Showroom 详情 dye_lot_notice 同源同口径（CV-SHR-011，DyeLotService 承载）；不缓存（CACHE-SHR-001）。
 */
@Component
public class TradingDyeLotPortAdapter implements com.dreamy.port.TradingDyeLotPort {

    private final DyeLotService dyeLotService;

    public TradingDyeLotPortAdapter(DyeLotService dyeLotService) {
        this.dyeLotService = dyeLotService;
    }

    @Override
    public List<Long> hintProductIds(Long customerId, List<Long> productIds) {
        return dyeLotService.hintProductIds(customerId, productIds);
    }
}
