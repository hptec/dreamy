package com.dreamy.domain.tax.service;

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
import com.dreamy.testsupport.TradingImmediateTxRunner;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TaxRuleService 单测（§6.1：region 空串规范化、重叠 422907、字段校验、enabled、目的国政策 upsert/默认）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaxRuleServiceTest {

    @Mock TaxRuleRepository ruleRepository;
    @Mock TaxDestinationPolicyRepository policyRepository;
    @Mock TradingAuditRecorder audit;
    @Mock CacheInvalidationTaskService cacheTasks;

    TaxRuleService service;

    @BeforeEach
    void setUp() {
        service = new TaxRuleService(ruleRepository, policyRepository, new TradingImmediateTxRunner(), audit, cacheTasks);
        doAnswer(inv -> {
            ((TaxRule) inv.getArgument(0)).setId(11L);
            return null;
        }).when(ruleRepository).insert(any());
    }

    private static TaxRuleUpsert upsert(String cc, String region, Integer type, Integer rate, LocalDate from, LocalDate to) {
        return new TaxRuleUpsert(cc, region, type, rate, true, null, from, to, true, null);
    }

    @Test
    @DisplayName("create：country 大写化、region 空→''、US 州名规范化；写审计 + 失效任务")
    void createNormalizes() {
        TaxRuleDto dto = service.create(upsert("gb", null, 1, 2000, null, null));
        assertThat(dto.countryCode()).isEqualTo("GB");
        assertThat(dto.region()).isEqualTo("");
        assertThat(dto.taxType()).isEqualTo(1);
        assertThat(dto.id()).isEqualTo(11L);
        verify(audit).record(eq(TaxRuleService.ACTION_TAX_RULE), eq("GB/*/VAT"), any());
        verify(cacheTasks).enqueue(any(), eq("tax_rule.create"), eq("tax_rule"), any(), any(), any(), any(), any(), any());
        TaxRuleDto tx = service.create(upsert("US", "texas", 3, 825, null, null));
        assertThat(tx.region()).isEqualTo("TX");
    }

    @Test
    @DisplayName("重叠 422907：同 key 生效窗口相交（NULL 边界=无穷）；不相交允许；update 排除自身")
    void overlapDetection() {
        TaxRule existing = TaxCalculatorTest.rule(1L, "GB", "", TaxType.VAT, 2000, true, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        when(ruleRepository.listByKey("GB", "", TaxType.VAT, null)).thenReturn(List.of(existing));
        assertThatThrownBy(() -> service.create(upsert("GB", "", 1, 2100, LocalDate.of(2026, 6, 1), null)))
                .isInstanceOfSatisfying(TradingException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.TAX_RULE_OVERLAP);
                    assertThat(ex.getDetails()).containsEntry("conflict_rule_id", 1L);
                });
        assertThatThrownBy(() -> service.create(upsert("GB", "", 1, 2100, null, null)))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.TAX_RULE_OVERLAP));
        // 不相交：2027 起
        service.create(upsert("GB", "", 1, 2100, LocalDate.of(2027, 1, 1), null));
        verify(ruleRepository).insert(any());
        // update 自身：listByKey 排除自身 → 不冲突
        when(ruleRepository.findById(1L)).thenReturn(existing);
        when(ruleRepository.listByKey("GB", "", TaxType.VAT, 1L)).thenReturn(List.of());
        TaxRuleDto updated = service.update(1L, upsert("GB", "", 1, 2200, LocalDate.of(2026, 1, 1), null));
        assertThat(updated.rateScaled()).isEqualTo(2200);
        assertThat(TaxRuleService.windowsOverlap(null, null, null, null)).isTrue();
        assertThat(TaxRuleService.windowsOverlap(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 1), null)).isFalse();
    }

    @Test
    @DisplayName("字段校验 422601：未知国家、tax_type 枚举外、rate 越界、effective_to < from、US 未知州")
    void validation() {
        assertField(() -> service.create(upsert("ZZ", null, 1, 100, null, null)), "country_code");
        assertField(() -> service.create(upsert("GB", null, 9, 100, null, null)), "tax_type");
        assertField(() -> service.create(upsert("GB", null, 1, 10001, null, null)), "rate_scaled");
        assertField(() -> service.create(upsert("GB", null, 1, null, null, null)), "rate_scaled");
        assertField(() -> service.create(upsert("GB", null, 1, 100, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1))),
                "effective_to");
        assertField(() -> service.create(upsert("US", "Nowhere", 3, 100, null, null)), "region");
        verify(ruleRepository, never()).insert(any());
    }

    @Test
    @DisplayName("enabled PATCH：幂等（同值不写）；不存在 404906；delete 404906")
    void enabledAndDelete() {
        TaxRule rule = TaxCalculatorTest.rule(1L, "GB", "", TaxType.VAT, 2000, true, null, null, null);
        when(ruleRepository.findById(1L)).thenReturn(rule);
        service.setEnabled(1L, true);
        verify(ruleRepository, never()).updateEnabled(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
        service.setEnabled(1L, false);
        verify(ruleRepository).updateEnabled(1L, false);
        assertThatThrownBy(() -> service.setEnabled(2L, true))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.TAX_RULE_NOT_FOUND));
        when(ruleRepository.deleteById(1L)).thenReturn(1);
        service.delete(1L);
        verify(ruleRepository).deleteById(1L);
        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.TAX_RULE_NOT_FOUND));
    }

    @Test
    @DisplayName("目的国政策：无记录 GET → 默认 DDU + 默认提示（不落库）；PUT 新建/覆盖；DDU 缺省 duties_notice=true")
    void destinationPolicy() {
        TaxDestinationPolicyDto def = service.getPolicy("br");
        assertThat(def.countryCode()).isEqualTo("BR");
        assertThat(def.incoterm()).isEqualTo(Incoterm.DDU.getKey());
        assertThat(def.dutiesNotice()).isTrue();
        assertThat(def.noticeText()).isEqualTo(TaxCalculator.DEFAULT_DDU_NOTICE);
        verify(policyRepository, never()).insert(any());

        TaxDestinationPolicyDto created = service.upsertPolicy("BR", new TaxDestinationPolicyUpsert(1, null, null));
        assertThat(created.incoterm()).isEqualTo(1);
        assertThat(created.dutiesNotice()).isFalse();
        verify(policyRepository).insert(any());

        TaxDestinationPolicy existing = TaxCalculatorTest.policy("BR", Incoterm.DDP, false, null);
        existing.setId(5L);
        when(policyRepository.findByCountry("BR")).thenReturn(existing);
        TaxDestinationPolicyDto updated = service.upsertPolicy("BR", new TaxDestinationPolicyUpsert(2, null, "Pay at door"));
        assertThat(updated.incoterm()).isEqualTo(2);
        assertThat(updated.dutiesNotice()).isTrue();
        assertThat(updated.noticeText()).isEqualTo("Pay at door");
        verify(policyRepository).updateAll(any());
        assertField(() -> service.upsertPolicy("BR", new TaxDestinationPolicyUpsert(9, null, null)), "incoterm");
        assertField(() -> service.upsertPolicy("ZZ", new TaxDestinationPolicyUpsert(1, null, null)), "country_code");
    }

    private static void assertField(Runnable action, String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(TradingException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED);
            assertThat(((Map<?, ?>) ex.getDetails().get("fields")).containsKey(field)).as(field).isTrue();
        });
    }
}
