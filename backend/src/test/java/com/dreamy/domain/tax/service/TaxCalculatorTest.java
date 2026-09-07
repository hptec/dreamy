package com.dreamy.domain.tax.service;

import com.dreamy.domain.tax.entity.TaxDestinationPolicy;
import com.dreamy.domain.tax.entity.TaxRule;
import com.dreamy.domain.tax.repository.TaxDestinationPolicyRepository;
import com.dreamy.domain.tax.repository.TaxRuleRepository;
import com.dreamy.enums.Incoterm;
import com.dreamy.enums.TaxType;
import com.dreamy.infra.TaxCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * TaxCalculator 单测（§6.1：精确/国家级优先级、生效窗口、DDP/DDU、起征额、HALF_UP、运费计税、无政策默认 DDU 提示）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaxCalculatorTest {

    @Mock TaxRuleRepository ruleRepository;
    @Mock TaxDestinationPolicyRepository policyRepository;
    @Mock TaxCacheService cache;

    TaxCalculator calculator;
    List<TaxRule> rules = new ArrayList<>();
    List<TaxDestinationPolicy> policies = new ArrayList<>();
    LocalDate today = LocalDate.of(2026, 9, 7);

    @BeforeEach
    void setUp() {
        calculator = new TaxCalculator(ruleRepository, policyRepository, cache);
        lenient().when(cache.getRules(any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        lenient().when(cache.getPolicies(any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        lenient().when(ruleRepository.listEnabled()).thenAnswer(inv -> rules);
        lenient().when(policyRepository.listAll()).thenAnswer(inv -> policies);
        policies.add(policy("GB", Incoterm.DDP, false, null));
        policies.add(policy("US", Incoterm.DDP, false, null));
        policies.add(policy("JP", Incoterm.DDU, true, "Duties collected by carrier"));
        rules.add(rule(1L, "GB", "", TaxType.VAT, 2000, true, null, null, null));
    }

    @Test
    @DisplayName("GB VAT 20% DDP：base = subtotal − discount + shipping；HALF_UP 2 位；incoterm=DDP 无提示")
    void gbVatDdp() {
        TaxCalculator.TaxQuote q = calculator.compute("GB", null, new BigDecimal("100.05"), new BigDecimal("10.00"),
                new BigDecimal("28.00"), "GBP", BigDecimal.ONE, today);
        // base 118.05 × 20% = 23.61
        assertThat(q.taxAmount()).isEqualByComparingTo("23.61");
        assertThat(q.taxAmount().scale()).isEqualTo(2);
        assertThat(q.incoterm()).isEqualTo(Incoterm.DDP);
        assertThat(q.dutiesNotice()).isFalse();
        assertThat(q.breakdown()).hasSize(1);
        assertThat(q.breakdown().get(0).label()).isEqualTo("VAT 20%");
        assertThat(q.breakdown().get(0).base()).isEqualByComparingTo("118.05");
        assertThat(q.breakdown().get(0).rateScaled()).isEqualTo(2000);
    }

    @Test
    @DisplayName("HALF_UP 取整：EUR 汇率换算后 base 再计税；每条规则独立取 2 位后相加")
    void halfUpRoundingInOrderCurrency() {
        rules.clear();
        rules.add(rule(1L, "GB", "", TaxType.VAT, 2000, false, null, null, null));
        rules.add(rule(2L, "GB", "", TaxType.DUTY, 333, false, null, null, null));
        TaxCalculator.TaxQuote q = calculator.compute("GB", null, new BigDecimal("100.00"), BigDecimal.ZERO,
                new BigDecimal("28.00"), "EUR", new BigDecimal("0.92"), today);
        // base 92.00：VAT 18.40；DUTY 92 × 0.0333 = 3.0636 → 3.06；合计 21.46
        assertThat(q.taxAmount()).isEqualByComparingTo("21.46");
        assertThat(q.breakdown()).hasSize(2);
    }

    @Test
    @DisplayName("优先级：同 tax_type 下 (country, region 精确) 覆盖 (country, '')；不同 region 的规则不参与")
    void regionOverridesCountryLevel() {
        policies.add(policy("CA", Incoterm.DDP, false, null));
        rules.add(rule(10L, "CA", "", TaxType.GST, 500, false, null, null, null));
        rules.add(rule(11L, "CA", "ON", TaxType.GST, 1300, false, null, null, null));
        rules.add(rule(12L, "CA", "QC", TaxType.SALES_TAX, 998, false, null, null, null));
        TaxCalculator.TaxQuote on = calculator.compute("CA", "ON", new BigDecimal("100.00"), null, null, "CAD",
                BigDecimal.ONE, today);
        assertThat(on.taxAmount()).isEqualByComparingTo("13.00");
        TaxCalculator.TaxQuote bc = calculator.compute("CA", "BC", new BigDecimal("100.00"), null, null, "CAD",
                BigDecimal.ONE, today);
        assertThat(bc.taxAmount()).isEqualByComparingTo("5.00");
        TaxCalculator.TaxQuote qc = calculator.compute("CA", "qc", new BigDecimal("100.00"), null, null, "CAD",
                BigDecimal.ONE, today);
        // QC：GST 国家级 5% + QST 9.98%
        assertThat(qc.taxAmount()).isEqualByComparingTo("14.98");
        assertThat(qc.breakdown()).hasSize(2);
    }

    @Test
    @DisplayName("生效窗口：today 不在 [from,to] 内的规则不命中；enabled=false 不命中")
    void effectiveWindow() {
        rules.clear();
        rules.add(rule(1L, "GB", "", TaxType.VAT, 2000, false, null, today.plusDays(1), null));
        assertThat(calculator.compute("GB", null, new BigDecimal("100"), null, null, "GBP", BigDecimal.ONE, today)
                .taxAmount()).isEqualByComparingTo("0.00");
        rules.clear();
        rules.add(rule(1L, "GB", "", TaxType.VAT, 2000, false, null, null, today.minusDays(1)));
        assertThat(calculator.compute("GB", null, new BigDecimal("100"), null, null, "GBP", BigDecimal.ONE, today)
                .taxAmount()).isEqualByComparingTo("0.00");
        rules.clear();
        rules.add(rule(1L, "GB", "", TaxType.VAT, 2000, false, null, today, today));
        assertThat(calculator.compute("GB", null, new BigDecimal("100"), null, null, "GBP", BigDecimal.ONE, today)
                .taxAmount()).isEqualByComparingTo("20.00");
        TaxRule disabled = rule(2L, "GB", "", TaxType.DUTY, 1000, false, null, null, null);
        disabled.setEnabled(false);
        rules.add(disabled);
        assertThat(calculator.compute("GB", null, new BigDecimal("100"), null, null, "GBP", BigDecimal.ONE, today)
                .taxAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("起征额 threshold_usd 以 USD 比较：base < threshold → 不计税；≥ → 计税（含运费口径）")
    void thresholdUsd() {
        rules.clear();
        rules.add(rule(1L, "GB", "", TaxType.DUTY, 1000, false, new BigDecimal("150.00"), null, null));
        assertThat(calculator.compute("GB", null, new BigDecimal("149.99"), null, new BigDecimal("50"), "GBP",
                new BigDecimal("0.79"), today).taxAmount()).isEqualByComparingTo("0.00");
        TaxCalculator.TaxQuote hit = calculator.compute("GB", null, new BigDecimal("150.00"), null, null, "GBP",
                new BigDecimal("0.79"), today);
        // 150 × 0.79 = 118.50 × 10% = 11.85
        assertThat(hit.taxAmount()).isEqualByComparingTo("11.85");
    }

    @Test
    @DisplayName("DDU：税费恒 0、breakdown 空、duties_notice=true + 政策文案；无政策国家默认 DDU + 默认提示；US 无规则 DDP → 0 无提示")
    void dduAndDefaults() {
        rules.add(rule(5L, "JP", "", TaxType.VAT, 1000, false, null, null, null));
        TaxCalculator.TaxQuote jp = calculator.compute("JP", null, new BigDecimal("100"), null, null, "USD",
                BigDecimal.ONE, today);
        assertThat(jp.taxAmount()).isEqualByComparingTo("0.00");
        assertThat(jp.breakdown()).isEmpty();
        assertThat(jp.incoterm()).isEqualTo(Incoterm.DDU);
        assertThat(jp.dutiesNotice()).isTrue();
        assertThat(jp.noticeText()).isEqualTo("Duties collected by carrier");
        TaxCalculator.TaxQuote br = calculator.compute("BR", null, new BigDecimal("100"), null, null, "USD",
                BigDecimal.ONE, today);
        assertThat(br.incoterm()).isEqualTo(Incoterm.DDU);
        assertThat(br.dutiesNotice()).isTrue();
        assertThat(br.noticeText()).isEqualTo(TaxCalculator.DEFAULT_DDU_NOTICE);
        assertThat(br.policyDefined()).isFalse();
        TaxCalculator.TaxQuote us = calculator.compute("US", "TX", new BigDecimal("100"), null, null, "USD",
                BigDecimal.ONE, today);
        assertThat(us.taxAmount()).isEqualByComparingTo("0.00");
        assertThat(us.incoterm()).isEqualTo(Incoterm.DDP);
        assertThat(us.dutiesNotice()).isFalse();
        TaxCalculator.TaxQuote none = calculator.compute(null, null, new BigDecimal("100"), null, null, "USD",
                BigDecimal.ONE, today);
        assertThat(none.incoterm()).isEqualTo(Incoterm.DDU);
    }

    static TaxRule rule(Long id, String cc, String region, TaxType type, int rate, boolean shipping,
                        BigDecimal threshold, LocalDate from, LocalDate to) {
        TaxRule rule = new TaxRule();
        rule.setId(id);
        rule.setCountryCode(cc);
        rule.setRegion(region);
        rule.setTaxType(type);
        rule.setRateScaled(rate);
        rule.setAppliesToShipping(shipping);
        rule.setThresholdUsd(threshold);
        rule.setEffectiveFrom(from);
        rule.setEffectiveTo(to);
        rule.setEnabled(true);
        rule.setLabel(type == TaxType.VAT && rate == 2000 ? "VAT 20%" : null);
        return rule;
    }

    static TaxDestinationPolicy policy(String cc, Incoterm incoterm, boolean notice, String text) {
        TaxDestinationPolicy policy = new TaxDestinationPolicy();
        policy.setCountryCode(cc);
        policy.setIncoterm(incoterm);
        policy.setDutiesNotice(notice);
        policy.setNoticeText(text);
        return policy;
    }
}
