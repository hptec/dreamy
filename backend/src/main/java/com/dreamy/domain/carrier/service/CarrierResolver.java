package com.dreamy.domain.carrier.service;

import com.dreamy.domain.carrier.entity.Carrier;
import com.dreamy.domain.carrier.repository.CarrierRepository;
import com.dreamy.infra.ShippingCacheService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 承运商解析（order-flow-complete C：替代 TradingParams.CARRIERS 固定三值，改由 carrier 表校验）。
 * 入参可为 code（FEDEX）或 name（"FedEx International Priority"，兼容旧 Order.carrier / /ship 端点）；
 * 读走 shipping:carriers 缓存（仅 enabled），未命中回源全表点查（disabled 承运商用于历史包裹展示）。
 */
@Service
public class CarrierResolver {

    private final CarrierRepository carrierRepository;
    private final ShippingCacheService cache;

    public CarrierResolver(CarrierRepository carrierRepository, ShippingCacheService cache) {
        this.carrierRepository = carrierRepository;
        this.cache = cache;
    }

    /** code 或 name → Carrier（仅 enabled）；未命中 → null */
    public Carrier resolveEnabled(String codeOrName) {
        if (codeOrName == null || codeOrName.isBlank()) {
            return null;
        }
        String trimmed = codeOrName.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        List<Carrier> carriers = cache.getCarriers(carrierRepository::listEnabled);
        for (Carrier carrier : carriers) {
            if (carrier.getCode() != null && carrier.getCode().equalsIgnoreCase(upper)) {
                return carrier;
            }
        }
        for (Carrier carrier : carriers) {
            if (carrier.getName() != null && carrier.getName().equalsIgnoreCase(trimmed)) {
                return carrier;
            }
        }
        return null;
    }

    /** code 或 name → Carrier（含 disabled，直查 DB）；未命中 → null */
    public Carrier resolveAny(String codeOrName) {
        Carrier enabled = resolveEnabled(codeOrName);
        if (enabled != null) {
            return enabled;
        }
        if (codeOrName == null || codeOrName.isBlank()) {
            return null;
        }
        Carrier byCode = carrierRepository.findByCode(codeOrName.trim().toUpperCase(Locale.ROOT));
        return byCode != null ? byCode : carrierRepository.findByName(codeOrName.trim());
    }

    /** 跟踪链接（模板 {tracking_no} 占位；无模板 → null） */
    public static String trackingUrl(Carrier carrier, String trackingNo) {
        if (carrier == null || carrier.getTrackingUrlTemplate() == null || trackingNo == null) {
            return null;
        }
        String encoded = java.net.URLEncoder.encode(trackingNo, java.nio.charset.StandardCharsets.UTF_8);
        return carrier.getTrackingUrlTemplate().replace("{tracking_no}", encoded);
    }
}
