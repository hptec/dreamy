package com.dreamy.domain.tax.service;

import com.dreamy.domain.cache.service.CacheInvalidationTarget;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.domain.tax.entity.TaxDestinationPolicy;
import com.dreamy.domain.tax.entity.TaxRule;
import com.dreamy.domain.tax.repository.TaxDestinationPolicyRepository;
import com.dreamy.domain.tax.repository.TaxRuleRepository;
import com.dreamy.dto.TradingDtos.TaxDestinationPolicyDto;
import com.dreamy.dto.TradingDtos.TaxDestinationPolicyUpsert;
import com.dreamy.dto.TradingDtos.TaxRuleDto;
import com.dreamy.dto.TradingDtos.TaxRuleUpsert;
import com.dreamy.enums.Incoterm;
import com.dreamy.enums.TaxType;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingAuditRecorder;
import com.dreamy.infra.TradingTxRunner;
import com.dreamy.support.CountryCatalog;
import com.dreamy.support.TradingFieldErrors;
import com.dreamy.support.TradingParams;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 税率规则 / 目的国政策后台服务（order-flow-complete F：/api/admin/tax-rules、/api/admin/tax-destination-policies）。
 * region 空串规范化（国家级）；uk (country_code, region, tax_type)；同 key 生效窗口重叠 → 422907；
 * 写后失效 tax:rules/tax:policies 缓存（经缓存任务，提交后执行）。
 */
@Service
public class TaxRuleService {

    public static final String ACTION_TAX_RULE = "税率规则变更";
    public static final String ACTION_TAX_POLICY = "目的国税费政策变更";

    private final TaxRuleRepository ruleRepository;
    private final TaxDestinationPolicyRepository policyRepository;
    private final TradingTxRunner txRunner;
    private final TradingAuditRecorder audit;
    private final CacheInvalidationTaskService cacheTasks;

    public TaxRuleService(TaxRuleRepository ruleRepository, TaxDestinationPolicyRepository policyRepository,
                          TradingTxRunner txRunner, TradingAuditRecorder audit,
                          CacheInvalidationTaskService cacheTasks) {
        this.ruleRepository = ruleRepository;
        this.policyRepository = policyRepository;
        this.txRunner = txRunner;
        this.audit = audit;
        this.cacheTasks = cacheTasks;
    }

    // ==================== 税率规则 ====================

    public List<TaxRuleDto> list(String countryCode) {
        List<TaxRule> rules = countryCode == null || countryCode.isBlank()
                ? ruleRepository.listAll() : ruleRepository.listByCountry(countryCode.trim().toUpperCase(Locale.ROOT));
        return rules.stream().map(TaxRuleService::toDto).toList();
    }

    public TaxRuleDto create(TaxRuleUpsert req) {
        TaxRule rule = new TaxRule();
        apply(rule, validate(req));
        assertNoOverlap(rule, null);
        txRunner.inTx(() -> {
            try {
                ruleRepository.insert(rule);
            } catch (DuplicateKeyException ex) {
                // uk 冲突 = 同 key 已存在一条（窗口重叠语义）
                throw new TradingException(TradingErrorCode.TAX_RULE_OVERLAP, keyDetails(rule));
            }
            audit.record(ACTION_TAX_RULE, key(rule), "{\"op\":\"create\",\"rate_scaled\":" + rule.getRateScaled() + "}");
            enqueue("tax_rule.create", rule.getId(), key(rule));
        });
        return toDto(rule);
    }

    public TaxRuleDto update(Long id, TaxRuleUpsert req) {
        TaxRule existing = ruleRepository.findById(id);
        if (existing == null) {
            throw new TradingException(TradingErrorCode.TAX_RULE_NOT_FOUND);
        }
        Integer before = existing.getRateScaled();
        apply(existing, validate(req));
        assertNoOverlap(existing, id);
        txRunner.inTx(() -> {
            try {
                ruleRepository.updateAll(existing);
            } catch (DuplicateKeyException ex) {
                throw new TradingException(TradingErrorCode.TAX_RULE_OVERLAP, keyDetails(existing));
            }
            audit.record(ACTION_TAX_RULE, key(existing),
                    "{\"op\":\"update\",\"before\":" + before + ",\"after\":" + existing.getRateScaled() + "}");
            enqueue("tax_rule.update", id, key(existing));
        });
        return toDto(existing);
    }

    public TaxRuleDto setEnabled(Long id, Boolean enabled) {
        if (enabled == null) {
            throw TradingException.fieldValidation("enabled", "required");
        }
        TaxRule existing = ruleRepository.findById(id);
        if (existing == null) {
            throw new TradingException(TradingErrorCode.TAX_RULE_NOT_FOUND);
        }
        if (!enabled.equals(existing.getEnabled())) {
            txRunner.inTx(() -> {
                ruleRepository.updateEnabled(id, enabled);
                audit.record(ACTION_TAX_RULE, key(existing), "{\"op\":\"enabled\",\"enabled\":" + enabled + "}");
                enqueue("tax_rule.enabled", id, key(existing));
            });
            existing.setEnabled(enabled);
        }
        return toDto(existing);
    }

    public void delete(Long id) {
        TaxRule existing = ruleRepository.findById(id);
        if (existing == null) {
            throw new TradingException(TradingErrorCode.TAX_RULE_NOT_FOUND);
        }
        txRunner.inTx(() -> {
            if (ruleRepository.deleteById(id) == 0) {
                throw new TradingException(TradingErrorCode.TAX_RULE_NOT_FOUND);
            }
            audit.record(ACTION_TAX_RULE, key(existing), "{\"op\":\"delete\"}");
            enqueue("tax_rule.delete", id, key(existing));
        });
    }

    /** 校验后的载荷 */
    record ValidRule(String countryCode, String region, TaxType taxType, int rateScaled, boolean appliesToShipping,
                     java.math.BigDecimal thresholdUsd, LocalDate from, LocalDate to, boolean enabled, String label) {
    }

    ValidRule validate(TaxRuleUpsert req) {
        TradingFieldErrors errors = new TradingFieldErrors();
        if (req == null) {
            errors.reject("_body", "required");
            errors.throwIfAny();
        }
        String cc = TradingParams.trimToNull(req.countryCode());
        if (cc == null) {
            errors.reject("country_code", "required");
        } else {
            cc = cc.toUpperCase(Locale.ROOT);
            if (!CountryCatalog.isKnownCode(cc)) {
                errors.reject("country_code", "invalid_enum");
            }
        }
        // region 空串规范化（国家级）；US/CA/AU 允许州码（规范化到标准码），其余国家仅接受 ≤8 字符大写
        String region = TradingParams.trimToNull(req.region());
        if (region == null) {
            region = "";
        } else if (cc != null && CountryCatalog.hasRegions(cc)) {
            String normalized = CountryCatalog.resolveRegionCode(cc, region);
            if (normalized == null) {
                errors.reject("region", "invalid_enum");
            } else {
                region = normalized;
            }
        } else {
            region = region.toUpperCase(Locale.ROOT);
            if (region.length() > 8) {
                errors.reject("region", "too_long");
            }
        }
        TaxType taxType = TaxType.of(req.taxType());
        if (taxType == null) {
            errors.reject("tax_type", req.taxType() == null ? "required" : "invalid_enum");
        }
        Integer rate = req.rateScaled();
        if (rate == null) {
            errors.reject("rate_scaled", "required");
        } else if (rate < 0 || rate > 10000) {
            errors.reject("rate_scaled", "range_invalid");
        }
        if (req.thresholdUsd() != null && req.thresholdUsd().signum() < 0) {
            errors.reject("threshold_usd", "range_invalid");
        }
        if (req.effectiveFrom() != null && req.effectiveTo() != null && req.effectiveTo().isBefore(req.effectiveFrom())) {
            errors.reject("effective_to", "range_invalid");
        }
        String label = TradingParams.checkMaxLength(req.label(), 64, "label", errors);
        errors.throwIfAny();
        return new ValidRule(cc, region, taxType, rate, Boolean.TRUE.equals(req.appliesToShipping()),
                req.thresholdUsd(), req.effectiveFrom(), req.effectiveTo(),
                req.enabled() == null || req.enabled(), label);
    }

    private static void apply(TaxRule rule, ValidRule valid) {
        rule.setCountryCode(valid.countryCode());
        rule.setRegion(valid.region());
        rule.setTaxType(valid.taxType());
        rule.setRateScaled(valid.rateScaled());
        rule.setAppliesToShipping(valid.appliesToShipping());
        rule.setThresholdUsd(valid.thresholdUsd());
        rule.setEffectiveFrom(valid.from());
        rule.setEffectiveTo(valid.to());
        rule.setEnabled(valid.enabled());
        rule.setLabel(valid.label());
    }

    /** 同 key 生效窗口重叠 → 422907（NULL 边界视为无穷） */
    void assertNoOverlap(TaxRule candidate, Long excludeId) {
        for (TaxRule other : ruleRepository.listByKey(candidate.getCountryCode(), candidate.getRegion(),
                candidate.getTaxType(), excludeId)) {
            if (windowsOverlap(candidate.getEffectiveFrom(), candidate.getEffectiveTo(),
                    other.getEffectiveFrom(), other.getEffectiveTo())) {
                Map<String, Object> details = new java.util.LinkedHashMap<>(keyDetails(candidate));
                details.put("conflict_rule_id", other.getId());
                throw new TradingException(TradingErrorCode.TAX_RULE_OVERLAP, details);
            }
        }
    }

    static boolean windowsOverlap(LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
        LocalDate af = aFrom == null ? LocalDate.MIN : aFrom;
        LocalDate at = aTo == null ? LocalDate.MAX : aTo;
        LocalDate bf = bFrom == null ? LocalDate.MIN : bFrom;
        LocalDate bt = bTo == null ? LocalDate.MAX : bTo;
        return !af.isAfter(bt) && !bf.isAfter(at);
    }

    static TaxRuleDto toDto(TaxRule r) {
        return new TaxRuleDto(r.getId(), r.getCountryCode(), r.getRegion(),
                r.getTaxType() == null ? null : r.getTaxType().getKey(), r.getRateScaled(), r.getAppliesToShipping(),
                r.getThresholdUsd(), r.getEffectiveFrom(), r.getEffectiveTo(), r.getEnabled(), r.getLabel(),
                r.getUpdatedAt());
    }

    private static String key(TaxRule r) {
        return r.getCountryCode() + "/" + (r.getRegion() == null || r.getRegion().isEmpty() ? "*" : r.getRegion())
                + "/" + (r.getTaxType() == null ? "?" : r.getTaxType().name());
    }

    private static Map<String, Object> keyDetails(TaxRule r) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("country_code", r.getCountryCode());
        details.put("region", r.getRegion());
        details.put("tax_type", r.getTaxType() == null ? null : r.getTaxType().getKey());
        return details;
    }

    // ==================== 目的国政策 ====================

    public List<TaxDestinationPolicyDto> listPolicies() {
        return policyRepository.listAll().stream().map(TaxRuleService::toDto).toList();
    }

    /** 无记录 → 默认 DDU + 提示（不落库） */
    public TaxDestinationPolicyDto getPolicy(String countryCode) {
        String cc = normalizeCountry(countryCode);
        TaxDestinationPolicy policy = policyRepository.findByCountry(cc);
        if (policy == null) {
            return new TaxDestinationPolicyDto(cc, Incoterm.DDU.getKey(), true, TaxCalculator.DEFAULT_DDU_NOTICE, null);
        }
        return toDto(policy);
    }

    public TaxDestinationPolicyDto upsertPolicy(String countryCode, TaxDestinationPolicyUpsert req) {
        String cc = normalizeCountry(countryCode);
        TradingFieldErrors errors = new TradingFieldErrors();
        Incoterm incoterm = req == null ? null : Incoterm.of(req.incoterm());
        if (incoterm == null) {
            errors.reject("incoterm", req == null || req.incoterm() == null ? "required" : "invalid_enum");
        }
        String text = TradingParams.checkMaxLength(req == null ? null : req.noticeText(), 255, "notice_text", errors);
        errors.throwIfAny();
        boolean notice = req.dutiesNotice() == null ? incoterm == Incoterm.DDU : req.dutiesNotice();
        TaxDestinationPolicy existing = policyRepository.findByCountry(cc);
        TaxDestinationPolicy policy = existing == null ? new TaxDestinationPolicy() : existing;
        policy.setCountryCode(cc);
        policy.setIncoterm(incoterm);
        policy.setDutiesNotice(notice);
        policy.setNoticeText(text);
        txRunner.inTx(() -> {
            if (existing == null) {
                policyRepository.insert(policy);
            } else {
                policyRepository.updateAll(policy);
            }
            audit.record(ACTION_TAX_POLICY, cc, "{\"incoterm\":" + incoterm.getKey() + ",\"duties_notice\":" + notice + "}");
            enqueue("tax_policy.upsert", policy.getId(), cc);
        });
        return toDto(policy);
    }

    private static String normalizeCountry(String countryCode) {
        String cc = TradingParams.trimToNull(countryCode);
        if (cc == null) {
            throw TradingException.fieldValidation("country_code", "required");
        }
        cc = cc.toUpperCase(Locale.ROOT);
        if (!CountryCatalog.isKnownCode(cc)) {
            throw TradingException.fieldValidation("country_code", "invalid_enum");
        }
        return cc;
    }

    static TaxDestinationPolicyDto toDto(TaxDestinationPolicy p) {
        return new TaxDestinationPolicyDto(p.getCountryCode(), p.getIncoterm() == null ? null : p.getIncoterm().getKey(),
                p.getDutiesNotice(), p.getNoticeText(), p.getUpdatedAt());
    }

    private void enqueue(String triggerPoint, Long id, String label) {
        cacheTasks.enqueue(CacheInvalidationTaskService.MODE_BUSINESS_WRITE, triggerPoint, "tax_rule", id, label,
                List.of(CacheInvalidationTarget.TAX_RULES), null, Map.of(), null);
    }
}
