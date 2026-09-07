package com.dreamy.domain.shippingrate.service;

import com.dreamy.domain.carrier.entity.Carrier;
import com.dreamy.domain.carrier.repository.CarrierRepository;
import com.dreamy.domain.shippingrate.consts.ShippingOptionDBConst;
import com.dreamy.domain.shippingrate.entity.ShippingOption;
import com.dreamy.domain.shippingrate.repository.ShippingOptionRepository;
import com.dreamy.enums.ShippingServiceLevel;
import com.dreamy.infra.ShippingCacheService;
import com.dreamy.port.ShippingOptionQuote;
import com.dreamy.port.ShippingQuotePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SVC-SHP-01 多承运商报价领域服务（ShippingQuotePort 提供侧权威实现；order-flow-complete E 改读 shipping_option）。
 * FLOW-P05：trading 进程内同步直调；只读、无事务；两级缓存命中时零 DB 访问（shipping:carriers / shipping:options）。
 * 匹配优先级（每个 enabled 承运商 × 服务等级）：
 * ① (zone, carrier.code, level) → ② (zone, ANY, level) → ③ zone≠REST 时 (REST, carrier.code, level) → ④ (REST, ANY, level)
 * → ⑤ 仍无 → 该承运商该等级跳过（不抛错）。
 * 失败传播：DB 异常 → 50001 由 trading 报价端点统一透出；本服务不吞错、不降级（运费是结算强依赖）。
 */
@Service
public class ShippingQuoteService implements ShippingQuotePort {

    private final CarrierRepository carrierRepository;
    private final ShippingOptionRepository optionRepository;
    private final ShippingCacheService cache;

    public ShippingQuoteService(CarrierRepository carrierRepository, ShippingOptionRepository optionRepository,
                                ShippingCacheService cache) {
        this.carrierRepository = carrierRepository;
        this.optionRepository = optionRepository;
        this.cache = cache;
    }

    @Override
    public List<ShippingOptionQuote> quoteOptions(String country, BigDecimal subtotalUsd) {
        String zone = GeoZoneResolver.resolve(country);
        return quoteByZone(zone, subtotalUsd);
    }

    /** zone 码直接报价（后台试算 / 已解析 country_code 的调用方） */
    public List<ShippingOptionQuote> quoteByZone(String zone, BigDecimal subtotalUsd) {
        List<Carrier> carriers = cache.getCarriers(carrierRepository::listEnabled);
        List<ShippingOption> options = cache.getOptions(optionRepository::listEnabled);
        Map<String, ShippingOption> index = new HashMap<>();
        for (ShippingOption option : options) {
            if (!Boolean.FALSE.equals(option.getEnabled())) {
                index.putIfAbsent(key(option.getZone(), option.getCarrierCode(), option.getServiceLevel()), option);
            }
        }
        BigDecimal subtotal = subtotalUsd == null ? BigDecimal.ZERO : subtotalUsd;
        List<ShippingOptionQuote> quotes = new ArrayList<>();
        // 顺序：承运商 id ASC × 等级 STANDARD→EXPRESS（稳定可测）
        for (Carrier carrier : carriers) {
            for (ShippingServiceLevel level : ShippingServiceLevel.values()) {
                ShippingOption option = match(index, zone, carrier.getCode(), level);
                if (option == null) {
                    continue;
                }
                BigDecimal fee = computeFee(option, subtotal);
                quotes.add(new ShippingOptionQuote(carrier.getName(), fee, carrier.getLeadTime(),
                        carrier.getCode(), level.getKey(), option.getTransitDaysMin(), option.getTransitDaysMax()));
            }
        }
        return quotes;
    }

    /** 是否存在覆盖该 zone 的启用选项（含 REST 兜底）——countries.supported 派生 */
    public boolean zoneSupported(String zone) {
        List<ShippingOption> options = cache.getOptions(optionRepository::listEnabled);
        for (ShippingOption option : options) {
            if (Boolean.FALSE.equals(option.getEnabled())) {
                continue;
            }
            if (zone.equalsIgnoreCase(option.getZone()) || GeoZoneResolver.REST.equalsIgnoreCase(option.getZone())) {
                return true;
            }
        }
        return false;
    }

    private ShippingOption match(Map<String, ShippingOption> index, String zone, String carrierCode,
                                 ShippingServiceLevel level) {
        ShippingOption option = null;
        if (carrierCode != null) {
            option = index.get(key(zone, carrierCode, level));
        }
        if (option == null) {
            option = index.get(key(zone, ShippingOptionDBConst.CARRIER_ANY, level));
        }
        if (option == null && !GeoZoneResolver.REST.equalsIgnoreCase(zone)) {
            if (carrierCode != null) {
                option = index.get(key(GeoZoneResolver.REST, carrierCode, level));
            }
            if (option == null) {
                option = index.get(key(GeoZoneResolver.REST, ShippingOptionDBConst.CARRIER_ANY, level));
            }
        }
        return option;
    }

    /**
     * 单行计费（DEC-SHP-3 NULL 语义）：threshold NULL 或 subtotal < threshold → fee_under（NULL 计 0.00）；
     * subtotal >= threshold → fee_over（NULL 计 0.00）。出参 scale=2 HALF_UP。
     */
    static BigDecimal computeFee(ShippingOption line, BigDecimal subtotal) {
        BigDecimal fee;
        if (line.getThreshold() == null || subtotal.compareTo(line.getThreshold()) < 0) {
            fee = nvl(line.getFeeUnder());
        } else {
            fee = nvl(line.getFeeOver());
        }
        return fee.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String key(String zone, String carrierCode, ShippingServiceLevel level) {
        return (zone == null ? "" : zone.toUpperCase(Locale.ROOT)) + "|"
                + (carrierCode == null ? "" : carrierCode.toUpperCase(Locale.ROOT)) + "|"
                + (level == null ? 1 : level.getKey());
    }
}
