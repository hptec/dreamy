package com.dreamy.config;

import com.dreamy.domain.tax.entity.TaxDestinationPolicy;
import com.dreamy.domain.tax.entity.TaxRule;
import com.dreamy.domain.tax.repository.TaxDestinationPolicyRepository;
import com.dreamy.domain.tax.repository.TaxRuleRepository;
import com.dreamy.enums.Incoterm;
import com.dreamy.enums.TaxType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 税费种子（order-flow-complete F；配置类数据生产与 dev 同灌，表非空即跳过——幂等）：
 * GB VAT 20% DDP、EU 27 国标准 VAT DDP、AU GST 10% DDP、CA GST 5% DDP、US 无税 DDP（domestic，无提示）；
 * 其余国家无政策 → TaxCalculator 默认 DDU + 提示。
 */
@Component
@Order(32)
public class TaxSeedInitializer {

    private static final Logger log = LoggerFactory.getLogger(TaxSeedInitializer.class);

    /** EU 27 标准 VAT（rate_scaled；FI 25.5% = 2550） */
    static final Map<String, Integer> EU_VAT = new LinkedHashMap<>();

    static {
        EU_VAT.put("DE", 1900);
        EU_VAT.put("FR", 2000);
        EU_VAT.put("IT", 2200);
        EU_VAT.put("ES", 2100);
        EU_VAT.put("NL", 2100);
        EU_VAT.put("BE", 2100);
        EU_VAT.put("AT", 2000);
        EU_VAT.put("IE", 2300);
        EU_VAT.put("PT", 2300);
        EU_VAT.put("SE", 2500);
        EU_VAT.put("DK", 2500);
        EU_VAT.put("FI", 2550);
        EU_VAT.put("PL", 2300);
        EU_VAT.put("CZ", 2100);
        EU_VAT.put("GR", 2400);
        EU_VAT.put("HU", 2700);
        EU_VAT.put("RO", 1900);
        EU_VAT.put("SK", 2000);
        EU_VAT.put("SI", 2200);
        EU_VAT.put("HR", 2500);
        EU_VAT.put("BG", 2000);
        EU_VAT.put("LT", 2100);
        EU_VAT.put("LV", 2100);
        EU_VAT.put("EE", 2400);
        EU_VAT.put("LU", 1700);
        EU_VAT.put("MT", 1800);
        EU_VAT.put("CY", 1900);
    }

    private final TaxRuleRepository ruleRepository;
    private final TaxDestinationPolicyRepository policyRepository;

    public TaxSeedInitializer(TaxRuleRepository ruleRepository, TaxDestinationPolicyRepository policyRepository) {
        this.ruleRepository = ruleRepository;
        this.policyRepository = policyRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        seedRules();
        seedPolicies();
    }

    void seedRules() {
        if (ruleRepository.count() > 0) {
            return;
        }
        int inserted = 0;
        inserted += rule("GB", TaxType.VAT, 2000, true, "VAT 20%");
        for (Map.Entry<String, Integer> e : EU_VAT.entrySet()) {
            inserted += rule(e.getKey(), TaxType.VAT, e.getValue(), true, "VAT " + pct(e.getValue()));
        }
        inserted += rule("AU", TaxType.GST, 1000, true, "GST 10%");
        inserted += rule("CA", TaxType.GST, 500, true, "GST 5%");
        log.info("[TAX-SEED] tax_rule seeded rows={}", inserted);
    }

    void seedPolicies() {
        if (policyRepository.count() > 0) {
            return;
        }
        int inserted = 0;
        inserted += policy("US", Incoterm.DDP, false, null);
        inserted += policy("GB", Incoterm.DDP, false, null);
        for (String cc : EU_VAT.keySet()) {
            inserted += policy(cc, Incoterm.DDP, false, null);
        }
        inserted += policy("AU", Incoterm.DDP, false, null);
        inserted += policy("CA", Incoterm.DDP, false, null);
        log.info("[TAX-SEED] tax_destination_policy seeded rows={}", inserted);
    }

    private int rule(String cc, TaxType type, int rateScaled, boolean shipping, String label) {
        TaxRule rule = new TaxRule();
        rule.setCountryCode(cc);
        rule.setRegion("");
        rule.setTaxType(type);
        rule.setRateScaled(rateScaled);
        rule.setAppliesToShipping(shipping);
        rule.setThresholdUsd(null);
        rule.setEnabled(true);
        rule.setLabel(label);
        ruleRepository.insert(rule);
        return 1;
    }

    private int policy(String cc, Incoterm incoterm, boolean notice, String text) {
        TaxDestinationPolicy policy = new TaxDestinationPolicy();
        policy.setCountryCode(cc);
        policy.setIncoterm(incoterm);
        policy.setDutiesNotice(notice);
        policy.setNoticeText(text);
        policyRepository.insert(policy);
        return 1;
    }

    private static String pct(int rateScaled) {
        return java.math.BigDecimal.valueOf(rateScaled).movePointLeft(2).stripTrailingZeros().toPlainString() + "%";
    }
}
