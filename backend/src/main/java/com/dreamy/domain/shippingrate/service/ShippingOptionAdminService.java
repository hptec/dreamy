package com.dreamy.domain.shippingrate.service;

import com.dreamy.domain.cache.service.CacheInvalidationTarget;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.domain.carrier.entity.Carrier;
import com.dreamy.domain.carrier.repository.CarrierRepository;
import com.dreamy.domain.shippingrate.consts.ShippingOptionDBConst;
import com.dreamy.domain.shippingrate.entity.ShippingOption;
import com.dreamy.domain.shippingrate.repository.ShippingOptionRepository;
import com.dreamy.dto.TradingDtos.ShippingOptionAdminDto;
import com.dreamy.dto.TradingDtos.ShippingOptionUpsert;
import com.dreamy.enums.ShippingServiceLevel;
import com.dreamy.error.ShippingErrorCode;
import com.dreamy.error.ShippingException;
import com.dreamy.infra.ShippingAuditRecorder;
import com.dreamy.support.ShippingValidation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 运费选项后台应用服务（order-flow-complete E：/api/admin/shipping/options CRUD + enabled PATCH）。
 * uk (zone, carrier_code, service_level) 为 409904 权威闸；写后失效 shipping:options（+ countries）。
 */
@Service
public class ShippingOptionAdminService {

    private static final Logger log = LoggerFactory.getLogger(ShippingOptionAdminService.class);
    private static final BigDecimal FEE_MAX = new BigDecimal("9999999999.99");

    private final ShippingOptionRepository optionRepository;
    private final CarrierRepository carrierRepository;
    private final ShippingAuditRecorder audit;
    private final ObjectMapper objectMapper;
    private final CacheInvalidationTaskService cacheTasks;

    public ShippingOptionAdminService(ShippingOptionRepository optionRepository, CarrierRepository carrierRepository,
                                      ShippingAuditRecorder audit, ObjectMapper objectMapper,
                                      CacheInvalidationTaskService cacheTasks) {
        this.optionRepository = optionRepository;
        this.carrierRepository = carrierRepository;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.cacheTasks = cacheTasks;
    }

    public List<ShippingOptionAdminDto> list() {
        Map<String, String> names = carrierNames();
        return optionRepository.listAll().stream().map(o -> toDto(o, names)).toList();
    }

    @Transactional
    public ShippingOptionAdminDto create(ShippingOptionUpsert req) {
        ShippingOption option = new ShippingOption();
        apply(option, validate(req));
        if (optionRepository.existsByKey(option.getZone(), option.getCarrierCode(), option.getServiceLevel(), null)) {
            throw new ShippingException(ShippingErrorCode.SHIPPING_OPTION_EXISTS, keyDetails(option));
        }
        try {
            optionRepository.insert(option);
        } catch (DuplicateKeyException ex) {
            throw new ShippingException(ShippingErrorCode.SHIPPING_OPTION_EXISTS, keyDetails(option));
        }
        ShippingOptionAdminDto after = toDto(option, carrierNames());
        audit.record("创建运费选项", label(option), changesJson(null, after));
        enqueue("shipping_option.create", option);
        return after;
    }

    @Transactional
    public ShippingOptionAdminDto update(String rawId, ShippingOptionUpsert req) {
        Long id = ShippingValidation.parseId(rawId);
        ShippingOption existing = optionRepository.findById(id);
        if (existing == null) {
            throw new ShippingException(ShippingErrorCode.SHIPPING_OPTION_NOT_FOUND);
        }
        Map<String, String> names = carrierNames();
        ShippingOptionAdminDto before = toDto(existing, names);
        apply(existing, validate(req));
        if (optionRepository.existsByKey(existing.getZone(), existing.getCarrierCode(), existing.getServiceLevel(), id)) {
            throw new ShippingException(ShippingErrorCode.SHIPPING_OPTION_EXISTS, keyDetails(existing));
        }
        try {
            optionRepository.updateAll(existing);
        } catch (DuplicateKeyException ex) {
            throw new ShippingException(ShippingErrorCode.SHIPPING_OPTION_EXISTS, keyDetails(existing));
        }
        ShippingOptionAdminDto after = toDto(existing, names);
        audit.record("编辑运费选项", label(existing), changesJson(before, after));
        enqueue("shipping_option.update", existing);
        return after;
    }

    @Transactional
    public ShippingOptionAdminDto setEnabled(String rawId, Boolean enabled) {
        Long id = ShippingValidation.parseId(rawId);
        if (enabled == null) {
            throw ShippingException.fieldValidation("enabled");
        }
        ShippingOption existing = optionRepository.findById(id);
        if (existing == null) {
            throw new ShippingException(ShippingErrorCode.SHIPPING_OPTION_NOT_FOUND);
        }
        if (!enabled.equals(existing.getEnabled())) {
            optionRepository.updateEnabled(id, enabled);
            existing.setEnabled(enabled);
            audit.record("运费选项状态变更", label(existing),
                    changesJson(Map.of("enabled", !enabled), Map.of("enabled", enabled)));
            enqueue("shipping_option.enabled", existing);
        }
        return toDto(existing, carrierNames());
    }

    @Transactional
    public void delete(String rawId) {
        Long id = ShippingValidation.parseId(rawId);
        ShippingOption existing = optionRepository.findById(id);
        if (optionRepository.deleteById(id) == 0) {
            throw new ShippingException(ShippingErrorCode.SHIPPING_OPTION_NOT_FOUND);
        }
        audit.record("删除运费选项", existing == null ? ("ID:" + id) : label(existing),
                changesJson(existing == null ? null : toDto(existing, carrierNames()), null));
        cacheTasks.enqueue(CacheInvalidationTaskService.MODE_BUSINESS_WRITE, "shipping_option.delete",
                "shipping_option", id, existing == null ? null : label(existing),
                List.of(CacheInvalidationTarget.SHIPPING_OPTIONS), null, Map.of(), null);
    }

    // ==================== 校验 / 装配 ====================

    /** 校验后的载荷 */
    record Valid(String zone, String carrierCode, ShippingServiceLevel level, BigDecimal feeUnder,
                 BigDecimal feeOver, BigDecimal threshold, int transitMin, int transitMax, boolean enabled) {
    }

    Valid validate(ShippingOptionUpsert req) {
        if (req == null) {
            throw ShippingException.fieldValidation("_body");
        }
        String zone = ZoneNormalizer.normalizeZoneCode(req.zone());
        if (zone == null) {
            throw ShippingException.fieldValidation("zone");
        }
        String carrierCode = req.carrierCode() == null ? null : req.carrierCode().trim().toUpperCase(Locale.ROOT);
        if (carrierCode == null || carrierCode.isEmpty()) {
            throw ShippingException.fieldValidation("carrier_code");
        }
        if (!ShippingOptionDBConst.CARRIER_ANY.equals(carrierCode) && carrierRepository.findByCode(carrierCode) == null) {
            throw ShippingException.fieldValidation("carrier_code");
        }
        ShippingServiceLevel level = req.serviceLevel() == null ? ShippingServiceLevel.STANDARD
                : ShippingServiceLevel.of(req.serviceLevel());
        if (level == null) {
            throw ShippingException.fieldValidation("service_level");
        }
        validateFee(req.feeUnder(), "fee_under");
        validateFee(req.feeOver(), "fee_over");
        validateFee(req.threshold(), "threshold");
        int min = req.transitDaysMin() == null ? 5 : req.transitDaysMin();
        int max = req.transitDaysMax() == null ? Math.max(min, 10) : req.transitDaysMax();
        if (min < 0 || min > 365) {
            throw ShippingException.fieldValidation("transit_days_min");
        }
        if (max < min || max > 365) {
            throw ShippingException.fieldValidation("transit_days_max");
        }
        return new Valid(zone, carrierCode, level, req.feeUnder(), req.feeOver(), req.threshold(), min, max,
                req.enabled() == null || req.enabled());
    }

    private static void validateFee(BigDecimal value, String field) {
        if (value == null) {
            return;
        }
        if (value.signum() < 0 || value.compareTo(FEE_MAX) > 0 || value.stripTrailingZeros().scale() > 2) {
            throw ShippingException.fieldValidation(field);
        }
    }

    private static void apply(ShippingOption option, Valid valid) {
        option.setZone(valid.zone());
        option.setCarrierCode(valid.carrierCode());
        option.setServiceLevel(valid.level());
        option.setFeeUnder(valid.feeUnder());
        option.setFeeOver(valid.feeOver());
        option.setThreshold(valid.threshold());
        option.setTransitDaysMin(valid.transitMin());
        option.setTransitDaysMax(valid.transitMax());
        option.setEnabled(valid.enabled());
    }

    private Map<String, String> carrierNames() {
        Map<String, String> names = new HashMap<>();
        for (Carrier carrier : carrierRepository.listAll()) {
            if (carrier.getCode() != null) {
                names.put(carrier.getCode(), carrier.getName());
            }
        }
        names.put(ShippingOptionDBConst.CARRIER_ANY, "Any carrier");
        return names;
    }

    static ShippingOptionAdminDto toDto(ShippingOption o, Map<String, String> carrierNames) {
        return new ShippingOptionAdminDto(o.getId(), o.getZone(), o.getCarrierCode(),
                carrierNames.get(o.getCarrierCode()),
                o.getServiceLevel() == null ? null : o.getServiceLevel().getKey(),
                o.getFeeUnder(), o.getFeeOver(), o.getThreshold(), o.getTransitDaysMin(), o.getTransitDaysMax(),
                o.getEnabled(), o.getUpdatedAt());
    }

    private static String label(ShippingOption o) {
        return o.getZone() + " / " + o.getCarrierCode() + " / "
                + (o.getServiceLevel() == null ? "STANDARD" : o.getServiceLevel().name());
    }

    private static Map<String, Object> keyDetails(ShippingOption o) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("zone", o.getZone());
        details.put("carrier_code", o.getCarrierCode());
        details.put("service_level", o.getServiceLevel() == null ? 1 : o.getServiceLevel().getKey());
        return details;
    }

    private void enqueue(String triggerPoint, ShippingOption option) {
        cacheTasks.enqueue(CacheInvalidationTaskService.MODE_BUSINESS_WRITE, triggerPoint,
                "shipping_option", option.getId(), label(option), List.of(CacheInvalidationTarget.SHIPPING_OPTIONS),
                null, Map.of(), null);
    }

    private String changesJson(Object before, Object after) {
        try {
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("before", before);
            changes.put("after", after);
            return objectMapper.writeValueAsString(changes);
        } catch (Exception ex) {
            log.warn("[AUDIT-SHP] changes serialize failed", ex);
            return null;
        }
    }
}
