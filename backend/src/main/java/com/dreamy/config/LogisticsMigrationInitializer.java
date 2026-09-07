package com.dreamy.config;

import com.dreamy.domain.carrier.entity.Carrier;
import com.dreamy.domain.carrier.repository.CarrierRepository;
import com.dreamy.domain.shippingrate.consts.ShippingOptionDBConst;
import com.dreamy.domain.shippingrate.entity.ShippingOption;
import com.dreamy.domain.shippingrate.entity.ShippingRate;
import com.dreamy.domain.shippingrate.repository.ShippingOptionRepository;
import com.dreamy.domain.shippingrate.repository.ShippingRateRepository;
import com.dreamy.domain.shippingrate.service.GeoZoneResolver;
import com.dreamy.enums.ShippingServiceLevel;
import com.dreamy.infra.ShippingCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 物流 expand 迁移器（order-flow-complete §2.3 / §7；所有步骤幂等，生产与 dev 同跑）：
 * ① carrier.code / tracking_url_template 回填（按名称关键字识别 FEDEX/UPS/DHL/USPS；无法识别者按 name 派生大写编码）；
 * ② shipping_option 为空时按 shipping_rate 每行生成 STANDARD 选项（zone 前缀 → 新 zone 码；承运商名后缀 → code；
 *    无后缀兜底行 → carrier_code=ANY；无法映射者 ANY），并为已迁移 zone 补一条 EXPRESS 示例选项（fee×1.6，天数减半）；
 * ③ shipping_rate 原样保留（回滚可读）。
 * 顺序：ShippingSeedInitializer(30) 之后（demo 种子先落）。
 */
@Component
@Order(35)
public class LogisticsMigrationInitializer {

    private static final Logger log = LoggerFactory.getLogger(LogisticsMigrationInitializer.class);

    /** 名称关键字 → (code, tracking_url_template) */
    static final Map<String, String[]> KNOWN_CARRIERS = new LinkedHashMap<>();

    static {
        KNOWN_CARRIERS.put("FEDEX", new String[]{"FEDEX", "https://www.fedex.com/fedextrack/?trknbr={tracking_no}"});
        KNOWN_CARRIERS.put("UPS", new String[]{"UPS", "https://www.ups.com/track?tracknum={tracking_no}"});
        KNOWN_CARRIERS.put("DHL", new String[]{"DHL", "https://www.dhl.com/global-en/home/tracking.html?tracking-id={tracking_no}"});
        KNOWN_CARRIERS.put("USPS", new String[]{"USPS", "https://tools.usps.com/go/TrackConfirmAction?tLabels={tracking_no}"});
    }

    private final CarrierRepository carrierRepository;
    private final ShippingRateRepository rateRepository;
    private final ShippingOptionRepository optionRepository;
    private final ShippingCacheService cache;

    public LogisticsMigrationInitializer(CarrierRepository carrierRepository, ShippingRateRepository rateRepository,
                                         ShippingOptionRepository optionRepository, ShippingCacheService cache) {
        this.carrierRepository = carrierRepository;
        this.rateRepository = rateRepository;
        this.optionRepository = optionRepository;
        this.cache = cache;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        int codes = backfillCarrierCodes();
        int options = migrateShippingOptions();
        if (codes > 0 || options > 0) {
            try {
                cache.invalidateCarriersStrict();
                cache.invalidateOptionsStrict();
            } catch (Exception ex) {
                log.warn("[LOGISTICS-MIGRATE] cache invalidate failed (TTL 兜底)", ex);
            }
        }
    }

    /** ① carrier code 回填（仅 code 为空的行） */
    int backfillCarrierCodes() {
        int updated = 0;
        Set<String> used = new HashSet<>();
        List<Carrier> carriers = carrierRepository.listAll();
        for (Carrier carrier : carriers) {
            if (carrier.getCode() != null) {
                used.add(carrier.getCode().toUpperCase(Locale.ROOT));
            }
        }
        for (Carrier carrier : carriers) {
            if (carrier.getCode() != null) {
                continue;
            }
            String[] known = recognize(carrier.getName());
            String code = known != null ? known[0] : deriveCode(carrier.getName());
            String template = known != null ? known[1] : null;
            String candidate = code;
            int suffix = 2;
            while (used.contains(candidate)) {
                candidate = code + suffix++;
            }
            if (carrierRepository.backfillCode(carrier.getId(), candidate, template) > 0) {
                used.add(candidate);
                updated++;
                log.info("[LOGISTICS-MIGRATE] carrier id={} name='{}' → code={}", carrier.getId(), carrier.getName(), candidate);
            }
        }
        return updated;
    }

    static String[] recognize(String name) {
        if (name == null) {
            return null;
        }
        String upper = name.toUpperCase(Locale.ROOT);
        for (Map.Entry<String, String[]> e : KNOWN_CARRIERS.entrySet()) {
            if (upper.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    static String deriveCode(String name) {
        String derived = name == null ? "" : name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (derived.isEmpty()) {
            derived = "CARRIER";
        }
        if (derived.length() > 32) {
            derived = derived.substring(0, 32);
        }
        return derived;
    }

    /** ② shipping_rate → shipping_option（仅当 option 表为空） */
    int migrateShippingOptions() {
        if (optionRepository.count() > 0) {
            return 0;
        }
        List<ShippingRate> rates = rateRepository.listAll();
        if (rates.isEmpty()) {
            return 0;
        }
        Map<String, Carrier> byName = new LinkedHashMap<>();
        for (Carrier carrier : carrierRepository.listAll()) {
            if (carrier.getName() != null) {
                byName.put(carrier.getName().trim().toLowerCase(Locale.ROOT), carrier);
            }
        }
        Set<String> keys = new HashSet<>();
        int inserted = 0;
        for (ShippingRate rate : rates) {
            String zone = GeoZoneResolver.canonicalZone(rate.getZone());
            if (zone == null) {
                zone = GeoZoneResolver.REST;
            }
            String suffix = GeoZoneResolver.legacyCarrierSuffix(rate.getZone());
            String carrierCode = ShippingOptionDBConst.CARRIER_ANY;
            if (suffix != null) {
                Carrier carrier = byName.get(suffix.toLowerCase(Locale.ROOT));
                if (carrier != null && carrier.getCode() != null) {
                    carrierCode = carrier.getCode();
                } else {
                    String[] known = recognize(suffix);
                    if (known != null) {
                        carrierCode = known[0];
                    }
                }
            }
            // 旧 "Europe" 区域同时覆盖 GB（新 8 区把 UK 拆出），迁移时复制一份到 UK 保持价格不变
            List<String> zones = GeoZoneResolver.EUROPE.equals(zone)
                    ? List.of(GeoZoneResolver.EUROPE, GeoZoneResolver.UK) : List.of(zone);
            for (String z : zones) {
                int[] transit = defaultTransit(z);
                if (keys.add(z + "|" + carrierCode + "|1")) {
                    insert(z, carrierCode, ShippingServiceLevel.STANDARD, rate.getFeeUnder(), rate.getFeeOver(),
                            rate.getThreshold(), transit[0], transit[1]);
                    inserted++;
                }
                // EXPRESS 示例选项：fee×1.6（无满额档），天数减半（运营可在后台调整/禁用）
                if (keys.add(z + "|" + carrierCode + "|2")) {
                    java.math.BigDecimal base = rate.getFeeUnder() == null ? java.math.BigDecimal.ZERO : rate.getFeeUnder();
                    java.math.BigDecimal express = base.multiply(new java.math.BigDecimal("1.6"))
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                    insert(z, carrierCode, ShippingServiceLevel.EXPRESS, express, express, null,
                            Math.max(1, transit[0] / 2), Math.max(2, transit[1] / 2));
                    inserted++;
                }
            }
        }
        log.info("[LOGISTICS-MIGRATE] shipping_option migrated from shipping_rate: rows={} (source={})",
                inserted, rates.size());
        return inserted;
    }

    /** 迁移缺省运输天数（zone 维度） */
    static int[] defaultTransit(String zone) {
        return switch (zone) {
            case GeoZoneResolver.NORTH_AMERICA -> new int[]{3, 6};
            case GeoZoneResolver.UK, GeoZoneResolver.EUROPE -> new int[]{5, 8};
            case GeoZoneResolver.OCEANIA, GeoZoneResolver.ASIA -> new int[]{6, 10};
            default -> new int[]{8, 14};
        };
    }

    private void insert(String zone, String carrierCode, ShippingServiceLevel level, java.math.BigDecimal feeUnder,
                        java.math.BigDecimal feeOver, java.math.BigDecimal threshold, int min, int max) {
        ShippingOption option = new ShippingOption();
        option.setZone(zone);
        option.setCarrierCode(carrierCode);
        option.setServiceLevel(level);
        option.setFeeUnder(feeUnder);
        option.setFeeOver(feeOver);
        option.setThreshold(threshold);
        option.setTransitDaysMin(min);
        option.setTransitDaysMax(max);
        option.setEnabled(true);
        optionRepository.insert(option);
    }
}
