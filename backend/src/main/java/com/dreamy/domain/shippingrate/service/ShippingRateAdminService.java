package com.dreamy.domain.shippingrate.service;

import com.dreamy.domain.shippingrate.entity.ShippingRate;
import com.dreamy.domain.shippingrate.repository.ShippingRateRepository;
import com.dreamy.dto.ShippingDtos.ShippingRateDto;
import com.dreamy.dto.ShippingDtos.ShippingRateUpsert;
import com.dreamy.error.ShippingErrorCode;
import com.dreamy.error.ShippingException;
import com.dreamy.infra.ShippingAfterCommitRunner;
import com.dreamy.infra.ShippingAuditRecorder;
import com.dreamy.infra.ShippingCacheService;
import com.dreamy.support.ShippingValidation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运费规则后台应用服务（E-SHP-06~09；TX-SHP-005~007）。
 * rate 写路径无计数不变量，不加分布式锁（uk_shipping_rate_zone 唯一索引足够，EC-SHP-001 注记）。
 */
@Service
public class ShippingRateAdminService {

    private static final Logger log = LoggerFactory.getLogger(ShippingRateAdminService.class);

    private final ShippingRateRepository rateRepository;
    private final ShippingAuditRecorder audit;
    private final ShippingAfterCommitRunner afterCommit;
    private final ShippingCacheService cache;
    private final ObjectMapper objectMapper;

    public ShippingRateAdminService(ShippingRateRepository rateRepository, ShippingAuditRecorder audit,
                                    ShippingAfterCommitRunner afterCommit, ShippingCacheService cache,
                                    ObjectMapper objectMapper) {
        this.rateRepository = rateRepository;
        this.audit = audit;
        this.afterCommit = afterCommit;
        this.cache = cache;
        this.objectMapper = objectMapper;
    }

    /** E-SHP-06 listAdminShippingRates：STEP-SHP-01 全量 ORDER BY id ASC（RM-SHP-010，不分页不缓存） */
    public List<ShippingRateDto> list() {
        return rateRepository.listAll().stream().map(ShippingRateAdminService::toDto).toList();
    }

    /** E-SHP-07 createAdminShippingRate（TX-SHP-005） */
    @Transactional
    public ShippingRateDto create(ShippingRateUpsert req) {
        ShippingValidation.ValidRate valid = ShippingValidation.validateRate(req);
        // STEP-SHP-01 判重（RM-SHP-012，忽略大小写）→ 409901
        if (rateRepository.existsByZoneNormalized(valid.zoneNorm(), null)) {
            throw new ShippingException(ShippingErrorCode.ZONE_EXISTS, Map.of("zone", valid.zoneNorm()));
        }
        // STEP-SHP-02 INSERT（zone=规范化文本；uk 冲突并发竞态捕获转 409901）
        ShippingRate rate = new ShippingRate();
        rate.setZone(valid.zoneNorm());
        rate.setFeeUnder(valid.feeUnder());
        rate.setFeeOver(valid.feeOver());
        rate.setThreshold(valid.threshold());
        try {
            rateRepository.insert(rate);
        } catch (DuplicateKeyException ex) {
            throw new ShippingException(ShippingErrorCode.ZONE_EXISTS, Map.of("zone", valid.zoneNorm()));
        }
        ShippingRateDto after = toDto(rate);
        // STEP-SHP-03 审计（action=创建运费规则）
        audit.record("创建运费规则", rate.getZone(), changesJson(null, after));
        // STEP-SHP-04 提交后失效 shipping:rates（CACHE-SHP-002，结算报价 600s TTL 内即时生效）
        afterCommit.run(cache::invalidateRates);
        return after;
    }

    /** E-SHP-08 updateAdminShippingRate（TX-SHP-006） */
    @Transactional
    public ShippingRateDto update(String rawId, ShippingRateUpsert req) {
        Long id = ShippingValidation.parseId(rawId);
        ShippingValidation.ValidRate valid = ShippingValidation.validateRate(req);
        // STEP-SHP-01 findById（RM-SHP-011）→ 404902
        ShippingRate existing = rateRepository.findById(id);
        if (existing == null) {
            throw new ShippingException(ShippingErrorCode.SHIPPING_RATE_NOT_FOUND);
        }
        ShippingRateDto before = toDto(existing);
        // STEP-SHP-02 zone 变更时判重（排除自身行；自身等值大小写变体不误判）
        if (rateRepository.existsByZoneNormalized(valid.zoneNorm(), id)) {
            throw new ShippingException(ShippingErrorCode.ZONE_EXISTS, Map.of("zone", valid.zoneNorm()));
        }
        // STEP-SHP-03 整单覆盖（RM-SHP-014；提交 null 即清空费用字段，DEC-SHP-3 语义生效）
        existing.setZone(valid.zoneNorm());
        existing.setFeeUnder(valid.feeUnder());
        existing.setFeeOver(valid.feeOver());
        existing.setThreshold(valid.threshold());
        try {
            rateRepository.updateAll(existing);
        } catch (DuplicateKeyException ex) {
            throw new ShippingException(ShippingErrorCode.ZONE_EXISTS, Map.of("zone", valid.zoneNorm()));
        }
        ShippingRateDto after = toDto(existing);
        // STEP-SHP-04 审计（action=编辑运费规则）
        audit.record("编辑运费规则", existing.getZone(), changesJson(before, after));
        // STEP-SHP-05 提交后失效（已创建订单 shipping_fee 为快照不变）
        afterCommit.run(cache::invalidateRates);
        return after;
    }

    /** E-SHP-09 deleteAdminShippingRate（TX-SHP-007） */
    @Transactional
    public void delete(String rawId) {
        Long id = ShippingValidation.parseId(rawId);
        ShippingRate existing = rateRepository.findById(id);
        // STEP-SHP-01 DELETE（RM-SHP-015）→ affected=0 → 404902
        int affected = rateRepository.deleteById(id);
        if (affected == 0) {
            throw new ShippingException(ShippingErrorCode.SHIPPING_RATE_NOT_FOUND);
        }
        // STEP-SHP-02 审计（action=删除运费规则）。删除 Rest of World 兜底行的警示由前端二次确认承载（CV-SHP-006）
        audit.record("删除运费规则", existing == null ? ("ID:" + id) : existing.getZone(),
                changesJson(existing == null ? null : toDto(existing), null));
        // STEP-SHP-03 提交后失效
        afterCommit.run(cache::invalidateRates);
    }

    /** MAP-SHP-002（DECIMAL NULL → JSON null 原样透出，不补 0） */
    static ShippingRateDto toDto(ShippingRate rate) {
        return new ShippingRateDto(rate.getId(), rate.getZone(), rate.getFeeUnder(), rate.getFeeOver(),
                rate.getThreshold());
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
