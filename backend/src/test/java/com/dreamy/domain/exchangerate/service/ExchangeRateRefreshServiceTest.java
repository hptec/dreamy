package com.dreamy.domain.exchangerate.service;

import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.domain.exchangerate.entity.ExchangeRate;
import com.dreamy.domain.exchangerate.repository.ExchangeRateHistoryRepository;
import com.dreamy.domain.exchangerate.repository.ExchangeRateRepository;
import com.dreamy.dto.TradingDtos.ExchangeRateRefreshResponse;
import com.dreamy.enums.ExchangeRateSource;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingAuditRecorder;
import com.dreamy.port.ExchangeRateProviderPort;
import com.dreamy.testsupport.TradingImmediateTxRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ExchangeRateRefresh 单测（§6.1：manual 409905、provider 超时保留现值 + 计数、manual_override 跳过、history uk 幂等）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExchangeRateRefreshServiceTest {

    @Mock ExchangeRateProviderPort provider;
    @Mock ExchangeRateRepository rateRepository;
    @Mock ExchangeRateHistoryRepository historyRepository;
    @Mock TradingAuditRecorder audit;
    @Mock CacheInvalidationTaskService cacheTasks;

    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ExchangeRateRefreshService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeRateRefreshService(provider, rateRepository, historyRepository,
                new TradingImmediateTxRunner(), audit, cacheTasks, meterRegistry);
        when(provider.name()).thenReturn("frankfurter");
        when(provider.isManual()).thenReturn(false);
        for (String c : List.of("EUR", "CAD", "AUD", "GBP")) {
            ExchangeRate row = new ExchangeRate();
            row.setCurrency(c);
            row.setRate(new BigDecimal("1.000000"));
            row.setManualOverride("GBP".equals(c));
            when(rateRepository.findByCurrency(c)).thenReturn(row);
        }
        when(rateRepository.updateFromProvider(anyString(), any(), any())).thenReturn(1);
        when(historyRepository.insertIgnore(anyString(), any(), any(), any())).thenReturn(1, 1, 0);
    }

    @Test
    @DisplayName("manual 模式 → 409905，不调用供应商")
    void manualModeRejected() {
        when(provider.isManual()).thenReturn(true);
        when(provider.name()).thenReturn("manual");
        assertThatThrownBy(() -> service.refresh())
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.EXCHANGE_RATE_REFRESH_UNAVAILABLE));
        verify(provider, never()).fetchLatest(anyString(), any());
        assertThat(service.isManualMode()).isTrue();
    }

    @Test
    @DisplayName("供应商超时/失败 → 502602；不写库保留现值；[ALERT] 计数 dreamy.exchange.refresh.failure.total +1")
    void providerFailureKeepsCurrent() {
        when(provider.fetchLatest(anyString(), any()))
                .thenThrow(new ExchangeRateProviderPort.ProviderException("timeout", null));
        assertThatThrownBy(() -> service.refresh())
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.EXCHANGE_RATE_PROVIDER_UNAVAILABLE));
        verify(rateRepository, never()).updateFromProvider(anyString(), any(), any());
        verify(historyRepository, never()).insertIgnore(anyString(), any(), any(), any());
        assertThat(meterRegistry.get(ExchangeRateRefreshService.METRIC_REFRESH_FAILURE).counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("成功：manual_override 币种跳过；缺失币种跳过；其余更新 PROVIDER + history(quote_date)；失效缓存 + 审计")
    void refreshUpdatesNonOverridden() {
        LocalDate quoteDate = LocalDate.of(2026, 9, 5);
        when(provider.fetchLatest(eq("USD"), any())).thenReturn(new ExchangeRateProviderPort.RateQuote(quoteDate,
                Map.of("EUR", new BigDecimal("0.91"), "CAD", new BigDecimal("1.35"), "GBP", new BigDecimal("0.78"))));
        ExchangeRateRefreshResponse resp = service.refresh();
        assertThat(resp.updatedCount()).isEqualTo(2);
        assertThat(resp.skippedCurrencies()).containsExactlyInAnyOrder("AUD", "GBP");
        verify(rateRepository).updateFromProvider(eq("EUR"), eq(new BigDecimal("0.91")), any());
        verify(rateRepository).updateFromProvider(eq("CAD"), eq(new BigDecimal("1.35")), any());
        verify(rateRepository, never()).updateFromProvider(eq("GBP"), any(), any());
        verify(historyRepository).insertIgnore("EUR", new BigDecimal("0.91"), ExchangeRateSource.PROVIDER, quoteDate);
        verify(historyRepository, never()).insertIgnore(eq("GBP"), any(), any(), any());
        verify(audit).record(eq(ExchangeRateRefreshService.ACTION_RATE_REFRESH), eq("frankfurter"), any());
        verify(cacheTasks).enqueue(any(), eq("exchange_rate.refresh"), eq("exchange_rate"), any(), any(), any(), any(), any(), any());
        // 同日再次刷新：history insertIgnore 返回 0 不抛错（幂等）
        when(historyRepository.insertIgnore(anyString(), any(), any(), any())).thenReturn(0);
        assertThat(service.refresh().updatedCount()).isEqualTo(2);
    }
}
