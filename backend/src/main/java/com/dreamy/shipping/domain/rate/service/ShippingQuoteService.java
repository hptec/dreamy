package com.dreamy.shipping.domain.rate.service;

import com.dreamy.shipping.api.ShippingOptionQuote;
import com.dreamy.shipping.api.ShippingQuotePort;
import com.dreamy.shipping.domain.carrier.entity.Carrier;
import com.dreamy.shipping.domain.carrier.repository.CarrierRepository;
import com.dreamy.shipping.domain.rate.entity.ShippingRate;
import com.dreamy.shipping.domain.rate.repository.ShippingRateRepository;
import com.dreamy.shipping.infra.ShippingCacheService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SVC-SHP-01 多承运商报价领域服务（ShippingQuotePort 提供侧权威实现，shipping-api-detail §10）。
 * FLOW-P05：trading 进程内同步直调；只读、无事务；两级缓存命中时零 DB 访问（CACHE-SHP-001/002）。
 * 失败传播：DB 异常 → 50001 由 trading 报价端点统一透出；本服务不吞错、不降级（运费是结算强依赖）。
 * 空结果由消费侧处理（trading shipping_options >=1 依赖 Rest of World 兜底行——CV-SHP-006 + 种子保证）。
 */
@Service
public class ShippingQuoteService implements ShippingQuotePort {

    private final CarrierRepository carrierRepository;
    private final ShippingRateRepository rateRepository;
    private final ShippingCacheService cache;

    public ShippingQuoteService(CarrierRepository carrierRepository, ShippingRateRepository rateRepository,
                                ShippingCacheService cache) {
        this.carrierRepository = carrierRepository;
        this.rateRepository = rateRepository;
        this.cache = cache;
    }

    @Override
    public List<ShippingOptionQuote> quoteOptions(String country, BigDecimal subtotalUsd) {
        // 1. 收货国家 → 地理区域（§10.2）
        String region = GeoZoneResolver.resolve(country);
        // 2/3. 缓存读（未命中回源 + 回填 TTL 600s）
        List<Carrier> carriers = cache.getCarriers(carrierRepository::listEnabled);
        List<ShippingRate> rates = cache.getRates(rateRepository::listAll);
        // 规范化 zone（忽略大小写）内存索引
        Map<String, ShippingRate> ratesIdx = new HashMap<>();
        for (ShippingRate rate : rates) {
            String key = indexKey(rate.getZone());
            if (key != null) {
                ratesIdx.putIfAbsent(key, rate);
            }
        }
        // 4. 仅 enabled 承运商（契约规则 3/4），顺序 = enabled 承运商 id ASC（稳定可测）
        List<ShippingOptionQuote> options = new ArrayList<>();
        BigDecimal subtotal = subtotalUsd == null ? BigDecimal.ZERO : subtotalUsd;
        for (Carrier carrier : carriers) {
            ShippingRate line = matchLine(ratesIdx, region, carrier.getName());
            if (line == null) {
                // DEC-SHP-5 ④ 该承运商无报价项，跳过（不抛错）
                continue;
            }
            BigDecimal fee = computeFee(line, subtotal);
            options.add(new ShippingOptionQuote(carrier.getName(), fee, carrier.getLeadTime()));
        }
        return options;
    }

    /**
     * DEC-SHP-5 报价匹配优先级：
     * ① 「{region} / {carrier}」精确行 → ② 「{region}」无后缀兜底行
     * → ③ region != Rest of World 时回退「Rest of World / {carrier}」→「Rest of World」
     * → ④ 仍无 → null（跳过）。
     */
    private ShippingRate matchLine(Map<String, ShippingRate> ratesIdx, String region, String carrierName) {
        ShippingRate line = ratesIdx.get(indexKey(region + " / " + carrierName));
        if (line == null) {
            line = ratesIdx.get(indexKey(region));
        }
        if (line == null && !GeoZoneResolver.REST_OF_WORLD.equalsIgnoreCase(region)) {
            line = ratesIdx.get(indexKey(GeoZoneResolver.REST_OF_WORLD + " / " + carrierName));
            if (line == null) {
                line = ratesIdx.get(indexKey(GeoZoneResolver.REST_OF_WORLD));
            }
        }
        return line;
    }

    /**
     * 单行计费（§10.3 步骤 4 + DEC-SHP-3 NULL 语义）：
     * threshold NULL 或 subtotal < threshold → fee_under（NULL 计 0.00）；
     * subtotal >= threshold（边界等于取满额）→ fee_over（NULL 计 0.00；0.00 即满额包邮）。
     * 出参 scale=2 HALF_UP（MAP-SHP-003）。
     */
    private BigDecimal computeFee(ShippingRate line, BigDecimal subtotal) {
        BigDecimal fee;
        if (line.getThreshold() == null || subtotal.compareTo(line.getThreshold()) < 0) {
            fee = nvl(line.getFeeUnder());
        } else {
            fee = nvl(line.getFeeOver());
        }
        return fee.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** DEC-SHP-1：规范化 + 小写化索引 key（忽略大小写匹配） */
    private String indexKey(String zone) {
        String normalized = ZoneNormalizer.normalize(zone);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
}
