package com.dreamy.domain.exchangerate.service;

import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.domain.exchangerate.entity.ExchangeRate;
import com.dreamy.domain.exchangerate.entity.ExchangeRateHistory;
import com.dreamy.domain.exchangerate.repository.ExchangeRateHistoryRepository;
import com.dreamy.domain.exchangerate.repository.ExchangeRateRepository;
import com.dreamy.domain.cache.service.CacheInvalidationTarget;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.dto.TradingDtos.AdminExchangeRateDto;
import com.dreamy.dto.TradingDtos.ExchangeRateHistoryDto;
import com.dreamy.dto.TradingDtos.StoreExchangeRateDto;
import com.dreamy.enums.ExchangeRateSource;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.ExchangeRateCacheService;
import com.dreamy.infra.TradingAuditRecorder;
import com.dreamy.infra.TradingTxRunner;
import com.dreamy.support.TradingParams;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 汇率服务（trading-api-detail §8/§11，FLOW-P18，决策 14；order-flow-complete G 扩展）。
 * store 读：JetCache 两级 trading:exchange-rates（CACHE-TRD-001）；
 * admin 维护：TX-TRD-011 同事务审计 + 写 history(MANUAL, 当日, insertIgnore) + manual_override 标记；
 * effective_rate = rate × (1 + spread_scaled/10000) HALF_UP 6 位（与 CheckoutQuoteService 锁汇同口径）。
 */
@Service
public class ExchangeRateService {

    private static final BigDecimal SCALE_DIVISOR = new BigDecimal("10000");

    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateHistoryRepository historyRepository;
    private final CheckoutConfigRepository checkoutConfigRepository;
    private final ExchangeRateCacheService cacheService;
    private final TradingTxRunner txRunner;
    private final TradingAuditRecorder audit;
    private final CacheInvalidationTaskService cacheTasks;

    public ExchangeRateService(ExchangeRateRepository exchangeRateRepository,
                               ExchangeRateHistoryRepository historyRepository,
                               CheckoutConfigRepository checkoutConfigRepository,
                               ExchangeRateCacheService cacheService, TradingTxRunner txRunner,
                               TradingAuditRecorder audit, CacheInvalidationTaskService cacheTasks) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.historyRepository = historyRepository;
        this.checkoutConfigRepository = checkoutConfigRepository;
        this.cacheService = cacheService;
        this.txRunner = txRunner;
        this.audit = audit;
        this.cacheTasks = cacheTasks;
    }

    /** E-listStoreExchangeRates（STEP-TRD-01~03：读穿缓存；payload 不含 updated_by） */
    public List<StoreExchangeRateDto> listStore() {
        return cacheService.getOrLoad(() -> exchangeRateRepository.listAll().stream()
                .map(r -> new StoreExchangeRateDto(r.getCurrency(), r.getRate(), r.getUpdatedAt()))
                .toList());
    }

    /** E-listAdminExchangeRates（实时直查，不走缓存；admin 视图全字段 + effective_rate） */
    public List<AdminExchangeRateDto> listAdmin() {
        int spread = spreadScaled();
        return exchangeRateRepository.listAll().stream().map(r -> toAdminDto(r, spread)).toList();
    }

    /** 兼容旧签名（manual_override 保持现值） */
    public AdminExchangeRateDto update(String currency, BigDecimal rate) {
        return update(currency, rate, null);
    }

    /** E-updateAdminExchangeRate（V-TRD-058/059 + STEP-TRD-01~04；TX-TRD-011；G：manual_override + history） */
    public AdminExchangeRateDto update(String currency, BigDecimal rate, Boolean manualOverride) {
        // V-TRD-058：USD 恒 1 不可改；五币种外 → 422605
        if (!TradingParams.isSupportedCurrency(currency)) {
            throw new TradingException(TradingErrorCode.CURRENCY_NOT_SUPPORTED);
        }
        if ("USD".equals(currency)) {
            throw new TradingException(TradingErrorCode.CURRENCY_NOT_SUPPORTED,
                    Map.of("reason", "USD 恒为 1 不可改"));
        }
        // V-TRD-059 rate > 0（exclusiveMinimum）
        if (rate == null || rate.signum() <= 0) {
            throw TradingException.fieldValidation("rate", "range_invalid");
        }
        ExchangeRate before = exchangeRateRepository.findByCurrency(currency);
        if (before == null) {
            throw new TradingException(TradingErrorCode.CURRENCY_NOT_SUPPORTED, Map.of("currency", currency));
        }
        boolean override = manualOverride != null ? manualOverride : Boolean.TRUE.equals(before.getManualOverride());
        Long operatorId = audit.currentOperatorId();
        txRunner.inTx(() -> {
            exchangeRateRepository.updateManual(currency, rate, override, operatorId);
            // 手工修改也写 history(MANUAL, 当日；同日多次仅保留首条)
            historyRepository.insertIgnore(currency, rate, ExchangeRateSource.MANUAL, LocalDate.now());
            // 同事务审计（changes before/after）
            audit.record(TradingAuditRecorder.ACTION_RATE_UPDATE, currency,
                    "{\"currency\":\"" + currency + "\",\"before\":\"" + before.getRate()
                            + "\",\"after\":\"" + rate + "\",\"manual_override\":" + override + "}");
            cacheTasks.enqueue(CacheInvalidationTaskService.MODE_BUSINESS_WRITE, "exchange_rate.update",
                    "exchange_rate", currency, currency, List.of(CacheInvalidationTarget.TRADING_EXCHANGE_RATES),
                    null, Map.of("rate", rate), null);
        });
        return toAdminDto(exchangeRateRepository.findByCurrency(currency), spreadScaled());
    }

    /** GET /api/admin/exchange-rates/{currency}/history?days=30 */
    public List<ExchangeRateHistoryDto> history(String currency, Integer days) {
        if (!TradingParams.isSupportedCurrency(currency)) {
            throw new TradingException(TradingErrorCode.CURRENCY_NOT_SUPPORTED);
        }
        int window = days == null ? 30 : days;
        if (window < 1 || window > 365) {
            throw TradingException.fieldValidation("days", "range_invalid");
        }
        return historyRepository.listRecent(currency, window).stream().map(ExchangeRateService::toHistoryDto).toList();
    }

    /** checkout_config.exchange_rate_spread_scaled（缺省 0；读失败 0） */
    public int spreadScaled() {
        try {
            Integer spread = checkoutConfigRepository.getSingleton().getExchangeRateSpreadScaled();
            return spread == null ? 0 : spread;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /** 锁汇 = rate × (1 + spread_scaled/10000) HALF_UP 6 位；USD 恒 1 */
    public static BigDecimal applySpread(BigDecimal rate, int spreadScaled) {
        if (rate == null) {
            return null;
        }
        if (spreadScaled == 0) {
            return rate.setScale(6, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.add(BigDecimal.valueOf(spreadScaled).divide(SCALE_DIVISOR, 10, RoundingMode.HALF_UP));
        return rate.multiply(factor).setScale(6, RoundingMode.HALF_UP);
    }

    static AdminExchangeRateDto toAdminDto(ExchangeRate r, int spread) {
        boolean usd = "USD".equals(r.getCurrency());
        BigDecimal effective = usd ? BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP) : applySpread(r.getRate(), spread);
        return new AdminExchangeRateDto(r.getId(), r.getCurrency(), r.getRate(), r.getUpdatedBy(), r.getUpdatedAt(),
                r.getSource() == null ? ExchangeRateSource.MANUAL.getKey() : r.getSource().getKey(), r.getSyncedAt(),
                Boolean.TRUE.equals(r.getManualOverride()), effective, usd ? 0 : spread);
    }

    static ExchangeRateHistoryDto toHistoryDto(ExchangeRateHistory h) {
        return new ExchangeRateHistoryDto(h.getCurrency(), h.getRate(),
                h.getSource() == null ? null : h.getSource().getKey(), h.getQuoteDate(), h.getRecordedAt());
    }
}
