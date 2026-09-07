package com.dreamy.domain.tax.service;

import com.dreamy.domain.tax.entity.TaxDestinationPolicy;
import com.dreamy.domain.tax.entity.TaxRule;
import com.dreamy.domain.tax.repository.TaxDestinationPolicyRepository;
import com.dreamy.domain.tax.repository.TaxRuleRepository;
import com.dreamy.dto.TradingDtos.TaxBreakdownDto;
import com.dreamy.enums.Incoterm;
import com.dreamy.enums.TaxType;
import com.dreamy.infra.TaxCacheService;
import com.dreamy.support.Money;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 税费计算器（order-flow-complete §4.3 报价链 "税费" 步）。
 * 规则：按 country_code[+region] 匹配 enabled 且 today ∈ 生效窗口的规则；同 tax_type 下 region 精确覆盖国家级；
 * base = subtotal − discount [+ shipping if applies_to_shipping]（USD 比较 threshold_usd）；
 * 逐条 base(订单币种) × rate_scaled / 10000 HALF_UP 2 位后相加；
 * incoterm 取目的国政策：DDP 计入 total；DDU 税费恒 0，仅 duties_notice；无政策 → DDU + 提示。
 */
@Service
public class TaxCalculator {

    public static final String DEFAULT_DDU_NOTICE =
            "Import duties and taxes may be collected by the carrier on delivery.";
    private static final BigDecimal SCALE_DIVISOR = new BigDecimal("10000");

    private final TaxRuleRepository ruleRepository;
    private final TaxDestinationPolicyRepository policyRepository;
    private final TaxCacheService cache;

    public TaxCalculator(TaxRuleRepository ruleRepository, TaxDestinationPolicyRepository policyRepository,
                         TaxCacheService cache) {
        this.ruleRepository = ruleRepository;
        this.policyRepository = policyRepository;
        this.cache = cache;
    }

    /** 税费报价结果（taxAmount 订单币种 HALF_UP 2 位；DDU 恒 0 且 breakdown 空） */
    public record TaxQuote(BigDecimal taxAmount, List<TaxBreakdownDto> breakdown, Incoterm incoterm,
                           boolean dutiesNotice, String noticeText, boolean policyDefined) {

        public static TaxQuote none(Incoterm incoterm, boolean notice, String text, boolean defined) {
            return new TaxQuote(Money.zero(), List.of(), incoterm, notice, text, defined);
        }
    }

    /**
     * @param countryCode ISO alpha-2（null/未知 → DDU + 提示）
     * @param regionCode  ISO-3166-2 后缀（可空）
     * @param subtotalUsd 行小计 USD
     * @param discountUsd 券减免 USD（可空）
     * @param shippingUsd 所选运费 USD（可空）
     * @param currency    订单币种
     * @param rate        锁汇汇率（USD→订单币种）
     */
    public TaxQuote compute(String countryCode, String regionCode, BigDecimal subtotalUsd, BigDecimal discountUsd,
                            BigDecimal shippingUsd, String currency, BigDecimal rate) {
        return compute(countryCode, regionCode, subtotalUsd, discountUsd, shippingUsd, currency, rate, LocalDate.now());
    }

    /** 可注入 today 的重载（单测生效窗口） */
    public TaxQuote compute(String countryCode, String regionCode, BigDecimal subtotalUsd, BigDecimal discountUsd,
                            BigDecimal shippingUsd, String currency, BigDecimal rate, LocalDate today) {
        String cc = countryCode == null ? null : countryCode.trim().toUpperCase(Locale.ROOT);
        TaxDestinationPolicy policy = cc == null ? null : findPolicy(cc);
        if (policy == null) {
            return TaxQuote.none(Incoterm.DDU, true, DEFAULT_DDU_NOTICE, false);
        }
        boolean notice = !Boolean.FALSE.equals(policy.getDutiesNotice());
        String text = policy.getNoticeText() != null ? policy.getNoticeText() : (notice ? DEFAULT_DDU_NOTICE : null);
        if (policy.getIncoterm() != Incoterm.DDP) {
            return TaxQuote.none(Incoterm.DDU, notice, text, true);
        }
        BigDecimal effectiveRate = rate == null ? BigDecimal.ONE : rate;
        BigDecimal subtotal = nvl(subtotalUsd);
        BigDecimal discount = nvl(discountUsd);
        BigDecimal shipping = nvl(shippingUsd);
        BigDecimal goodsBaseUsd = subtotal.subtract(discount).max(BigDecimal.ZERO);

        List<TaxBreakdownDto> breakdown = new ArrayList<>();
        BigDecimal total = Money.zero();
        for (TaxRule rule : selectRules(cc, regionCode, today)) {
            BigDecimal baseUsd = Boolean.TRUE.equals(rule.getAppliesToShipping())
                    ? goodsBaseUsd.add(shipping) : goodsBaseUsd;
            if (rule.getThresholdUsd() != null && baseUsd.compareTo(rule.getThresholdUsd()) < 0) {
                continue;
            }
            BigDecimal baseCcy = Money.toCurrency(baseUsd, effectiveRate);
            BigDecimal amount = baseCcy.multiply(BigDecimal.valueOf(rule.getRateScaled() == null ? 0 : rule.getRateScaled()))
                    .divide(SCALE_DIVISOR, 2, RoundingMode.HALF_UP);
            breakdown.add(new TaxBreakdownDto(rule.getTaxType() == null ? null : rule.getTaxType().getKey(),
                    rule.getLabel() != null ? rule.getLabel() : defaultLabel(rule), rule.getRateScaled(), baseCcy, amount));
            total = total.add(amount);
        }
        return new TaxQuote(total.setScale(2, RoundingMode.HALF_UP), breakdown, Incoterm.DDP, notice,
                notice ? text : null, true);
    }

    /** 目的国政策（缓存读穿） */
    public TaxDestinationPolicy findPolicy(String countryCode) {
        List<TaxDestinationPolicy> policies = cache.getPolicies(policyRepository::listAll);
        for (TaxDestinationPolicy policy : policies) {
            if (policy.getCountryCode() != null && policy.getCountryCode().equalsIgnoreCase(countryCode)) {
                return policy;
            }
        }
        return null;
    }

    /** 命中规则（同 tax_type：region 精确优先于国家级；仅 enabled + 生效窗口内） */
    List<TaxRule> selectRules(String countryCode, String regionCode, LocalDate today) {
        List<TaxRule> rules = cache.getRules(ruleRepository::listEnabled);
        String region = regionCode == null ? "" : regionCode.trim().toUpperCase(Locale.ROOT);
        Map<TaxType, TaxRule> chosen = new EnumMap<>(TaxType.class);
        for (TaxRule rule : rules) {
            if (Boolean.FALSE.equals(rule.getEnabled()) || rule.getTaxType() == null) {
                continue;
            }
            if (rule.getCountryCode() == null || !rule.getCountryCode().equalsIgnoreCase(countryCode)) {
                continue;
            }
            if (!effective(rule, today)) {
                continue;
            }
            String ruleRegion = rule.getRegion() == null ? "" : rule.getRegion().trim().toUpperCase(Locale.ROOT);
            boolean countryLevel = ruleRegion.isEmpty();
            boolean regionExact = !countryLevel && ruleRegion.equals(region);
            if (!countryLevel && !regionExact) {
                continue;
            }
            TaxRule existing = chosen.get(rule.getTaxType());
            if (existing == null || (regionExact && isCountryLevel(existing))) {
                chosen.put(rule.getTaxType(), rule);
            }
        }
        return new ArrayList<>(chosen.values());
    }

    static boolean effective(TaxRule rule, LocalDate today) {
        if (rule.getEffectiveFrom() != null && today.isBefore(rule.getEffectiveFrom())) {
            return false;
        }
        return rule.getEffectiveTo() == null || !today.isAfter(rule.getEffectiveTo());
    }

    private static boolean isCountryLevel(TaxRule rule) {
        return rule.getRegion() == null || rule.getRegion().isBlank();
    }

    private static String defaultLabel(TaxRule rule) {
        String type = rule.getTaxType() == null ? "TAX" : rule.getTaxType().name().replace('_', ' ');
        BigDecimal pct = BigDecimal.valueOf(rule.getRateScaled() == null ? 0 : rule.getRateScaled())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP).stripTrailingZeros();
        return type + " " + pct.toPlainString() + "%";
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
