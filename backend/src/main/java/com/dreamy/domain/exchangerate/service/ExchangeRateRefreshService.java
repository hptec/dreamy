package com.dreamy.domain.exchangerate.service;

import com.dreamy.domain.cache.service.CacheInvalidationTarget;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.domain.exchangerate.entity.ExchangeRate;
import com.dreamy.domain.exchangerate.repository.ExchangeRateHistoryRepository;
import com.dreamy.domain.exchangerate.repository.ExchangeRateRepository;
import com.dreamy.dto.TradingDtos.ExchangeRateRefreshResponse;
import com.dreamy.enums.ExchangeRateSource;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingAuditRecorder;
import com.dreamy.infra.TradingTxRunner;
import com.dreamy.port.ExchangeRateProviderPort;
import com.dreamy.support.TradingParams;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 汇率供应商刷新（order-flow-complete G / §4.6）：
 * manual 模式 → 409905；供应商失败 → 保留现值 + [ALERT] + Micrometer dreamy.exchange.refresh.failure.total（手动触发时 502602）；
 * manual_override=1 的币种跳过；成功写 exchange_rate（source=PROVIDER, synced_at）+ history(PROVIDER, quote_date) insertIgnore
 * + 审计 + 失效 trading:exchange-rates。
 */
@Service
public class ExchangeRateRefreshService {

    public static final String METRIC_REFRESH_FAILURE = "dreamy.exchange.refresh.failure.total";
    public static final String ACTION_RATE_REFRESH = "汇率供应商刷新";

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateRefreshService.class);

    private final ExchangeRateProviderPort provider;
    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateHistoryRepository historyRepository;
    private final TradingTxRunner txRunner;
    private final TradingAuditRecorder audit;
    private final CacheInvalidationTaskService cacheTasks;
    private final MeterRegistry meterRegistry;

    public ExchangeRateRefreshService(ExchangeRateProviderPort provider, ExchangeRateRepository exchangeRateRepository,
                                      ExchangeRateHistoryRepository historyRepository, TradingTxRunner txRunner,
                                      TradingAuditRecorder audit, CacheInvalidationTaskService cacheTasks,
                                      MeterRegistry meterRegistry) {
        this.provider = provider;
        this.exchangeRateRepository = exchangeRateRepository;
        this.historyRepository = historyRepository;
        this.txRunner = txRunner;
        this.audit = audit;
        this.cacheTasks = cacheTasks;
        this.meterRegistry = meterRegistry;
    }

    public boolean isManualMode() {
        return provider.isManual();
    }

    /** 手动/定时刷新入口（manual 模式 409905；供应商失败 502602 保留现值） */
    public ExchangeRateRefreshResponse refresh() {
        if (provider.isManual()) {
            throw new TradingException(TradingErrorCode.EXCHANGE_RATE_REFRESH_UNAVAILABLE,
                    Map.of("mode", provider.name()));
        }
        List<String> targets = TradingParams.CURRENCIES.stream().filter(c -> !"USD".equals(c)).toList();
        ExchangeRateProviderPort.RateQuote quote;
        try {
            quote = provider.fetchLatest("USD", targets);
        } catch (RuntimeException ex) {
            meterRegistry.counter(METRIC_REFRESH_FAILURE, "provider", provider.name()).increment();
            log.error("[EXRATE][ALERT] provider {} refresh failed —— 保留现值，下一 cron 再试", provider.name(), ex);
            throw new TradingException(TradingErrorCode.EXCHANGE_RATE_PROVIDER_UNAVAILABLE,
                    Map.of("provider", provider.name()));
        }
        LocalDateTime now = LocalDateTime.now();
        List<String> skipped = new ArrayList<>();
        int[] updated = {0};
        txRunner.inTx(() -> {
            for (String currency : targets) {
                BigDecimal rate = quote.rates().get(currency);
                if (rate == null || rate.signum() <= 0) {
                    skipped.add(currency);
                    log.warn("[EXRATE] provider {} missing rate for {}", provider.name(), currency);
                    continue;
                }
                ExchangeRate current = exchangeRateRepository.findByCurrency(currency);
                if (current == null) {
                    skipped.add(currency);
                    continue;
                }
                if (Boolean.TRUE.equals(current.getManualOverride())) {
                    skipped.add(currency);
                    continue;
                }
                if (exchangeRateRepository.updateFromProvider(currency, rate, now) > 0) {
                    updated[0]++;
                }
                historyRepository.insertIgnore(currency, rate, ExchangeRateSource.PROVIDER, quote.quoteDate());
            }
            audit.record(ACTION_RATE_REFRESH, provider.name(),
                    "{\"updated\":" + updated[0] + ",\"skipped\":" + skipped + ",\"quote_date\":\""
                            + quote.quoteDate() + "\"}");
            if (updated[0] > 0) {
                cacheTasks.enqueue(CacheInvalidationTaskService.MODE_BUSINESS_WRITE, "exchange_rate.refresh",
                        "exchange_rate", provider.name(), provider.name(),
                        List.of(CacheInvalidationTarget.TRADING_EXCHANGE_RATES), null, Map.of(), null);
            }
        });
        log.info("[EXRATE] provider {} refreshed updated={} skipped={} quote_date={}", provider.name(), updated[0],
                skipped, quote.quoteDate());
        return new ExchangeRateRefreshResponse(updated[0], skipped, now);
    }
}
