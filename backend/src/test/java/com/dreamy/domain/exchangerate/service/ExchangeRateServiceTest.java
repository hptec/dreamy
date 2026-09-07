package com.dreamy.domain.exchangerate.service;

import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.domain.checkout.entity.CheckoutConfig;
import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.domain.exchangerate.entity.ExchangeRate;
import com.dreamy.domain.exchangerate.repository.ExchangeRateHistoryRepository;
import com.dreamy.domain.exchangerate.repository.ExchangeRateRepository;
import com.dreamy.dto.TradingDtos.AdminExchangeRateDto;
import com.dreamy.enums.ExchangeRateSource;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.ExchangeRateCacheService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ExchangeRateService 单测（G：spread 锁汇口径、manual_override、history(MANUAL)、admin 视图新字段、history 窗口校验）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExchangeRateServiceTest {

    @Mock ExchangeRateRepository rateRepository;
    @Mock ExchangeRateHistoryRepository historyRepository;
    @Mock CheckoutConfigRepository checkoutConfigRepository;
    @Mock ExchangeRateCacheService cacheService;
    @Mock TradingAuditRecorder audit;
    @Mock CacheInvalidationTaskService cacheTasks;

    ExchangeRateService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeRateService(rateRepository, historyRepository, checkoutConfigRepository, cacheService,
                new TradingImmediateTxRunner(), audit, cacheTasks);
        CheckoutConfig config = new CheckoutConfig();
        config.setExchangeRateSpreadScaled(150); // 1.5%
        when(checkoutConfigRepository.getSingleton()).thenReturn(config);
        ExchangeRate usd = row("USD", "1.000000", false, ExchangeRateSource.MANUAL);
        ExchangeRate eur = row("EUR", "0.920000", false, ExchangeRateSource.PROVIDER);
        when(rateRepository.listAll()).thenReturn(List.of(usd, eur));
        when(rateRepository.findByCurrency("EUR")).thenReturn(eur);
    }

    private static ExchangeRate row(String c, String rate, boolean override, ExchangeRateSource source) {
        ExchangeRate r = new ExchangeRate();
        r.setId(1L);
        r.setCurrency(c);
        r.setRate(new BigDecimal(rate));
        r.setManualOverride(override);
        r.setSource(source);
        return r;
    }

    @Test
    @DisplayName("applySpread：rate × (1 + spread/10000) HALF_UP 6 位；spread=0 原值 6 位")
    void applySpread() {
        assertThat(ExchangeRateService.applySpread(new BigDecimal("0.92"), 150)).isEqualByComparingTo("0.933800");
        assertThat(ExchangeRateService.applySpread(new BigDecimal("0.92"), 150).scale()).isEqualTo(6);
        assertThat(ExchangeRateService.applySpread(new BigDecimal("1.3333333"), 0)).isEqualByComparingTo("1.333333");
        assertThat(ExchangeRateService.applySpread(new BigDecimal("0.7912345"), 1)).isEqualByComparingTo("0.791314");
    }

    @Test
    @DisplayName("listAdmin：effective_rate 含 spread；USD 恒 1 且 spread 0；source/manual_override 透出")
    void listAdminEffectiveRate() {
        List<AdminExchangeRateDto> items = service.listAdmin();
        assertThat(items.get(0).currency()).isEqualTo("USD");
        assertThat(items.get(0).effectiveRate()).isEqualByComparingTo("1");
        assertThat(items.get(0).spreadScaled()).isEqualTo(0);
        assertThat(items.get(1).effectiveRate()).isEqualByComparingTo("0.933800");
        assertThat(items.get(1).spreadScaled()).isEqualTo(150);
        assertThat(items.get(1).source()).isEqualTo(ExchangeRateSource.PROVIDER.getKey());
        assertThat(items.get(1).manualOverride()).isFalse();
    }

    @Test
    @DisplayName("update：写 MANUAL + manual_override + history(MANUAL,当日)；USD 不可改 422605；rate ≤0 422601")
    void updateWritesHistoryAndOverride() {
        service.update("EUR", new BigDecimal("0.95"), true);
        verify(rateRepository).updateManual(eq("EUR"), eq(new BigDecimal("0.95")), eq(true), any());
        verify(historyRepository).insertIgnore(eq("EUR"), eq(new BigDecimal("0.95")), eq(ExchangeRateSource.MANUAL), any());
        verify(audit).record(eq(TradingAuditRecorder.ACTION_RATE_UPDATE), eq("EUR"), any());
        // manual_override 缺省保持现值（false）
        service.update("EUR", new BigDecimal("0.96"));
        verify(rateRepository).updateManual(eq("EUR"), eq(new BigDecimal("0.96")), eq(false), any());
        assertThatThrownBy(() -> service.update("USD", BigDecimal.ONE, null))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.CURRENCY_NOT_SUPPORTED));
        assertThatThrownBy(() -> service.update("EUR", BigDecimal.ZERO, null))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
    }

    @Test
    @DisplayName("history：days 缺省 30，1..365 越界 422601；币种外 422605")
    void historyWindow() {
        service.history("EUR", null);
        verify(historyRepository).listRecent("EUR", 30);
        assertThatThrownBy(() -> service.history("EUR", 0))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
        assertThatThrownBy(() -> service.history("JPY", 7))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.CURRENCY_NOT_SUPPORTED));
    }
}
