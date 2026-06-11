package com.dreamy.trading.domain.rate.service;

import com.dreamy.trading.domain.rate.entity.ExchangeRate;
import com.dreamy.trading.domain.rate.repository.ExchangeRateRepository;
import com.dreamy.trading.dto.TradingDtos.AdminExchangeRateDto;
import com.dreamy.trading.dto.TradingDtos.StoreExchangeRateDto;
import com.dreamy.trading.error.TradingErrorCode;
import com.dreamy.trading.error.TradingException;
import com.dreamy.trading.infra.ExchangeRateCacheService;
import com.dreamy.trading.infra.TradingAfterCommitRunner;
import com.dreamy.trading.infra.TradingAuditRecorder;
import com.dreamy.trading.infra.TradingTxRunner;
import com.dreamy.trading.mq.TradingEventsPublisher;
import com.dreamy.trading.support.TradingParams;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 汇率服务（trading-api-detail §8/§11，FLOW-P18，决策 14；derived_scope host TASK-051）。
 * store 读：JetCache 两级 trading:exchange-rates（CACHE-TRD-001）+ CDN s-maxage（控制器响应头）；
 * admin 维护：TX-TRD-011 同事务审计 → 提交后失效链（@CacheInvalidate 语义 → MQ content.invalidated → purge）。
 * 语义（决策 14）：仅影响新订单锁汇，既有订单 exchange_rate 快照不变。
 */
@Service
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateCacheService cacheService;
    private final TradingTxRunner txRunner;
    private final TradingAfterCommitRunner afterCommit;
    private final TradingAuditRecorder audit;
    private final TradingEventsPublisher eventsPublisher;

    public ExchangeRateService(ExchangeRateRepository exchangeRateRepository,
                               ExchangeRateCacheService cacheService, TradingTxRunner txRunner,
                               TradingAfterCommitRunner afterCommit, TradingAuditRecorder audit,
                               TradingEventsPublisher eventsPublisher) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.cacheService = cacheService;
        this.txRunner = txRunner;
        this.afterCommit = afterCommit;
        this.audit = audit;
        this.eventsPublisher = eventsPublisher;
    }

    /** E-listStoreExchangeRates（STEP-TRD-01~03：读穿缓存；payload 不含 updated_by） */
    public List<StoreExchangeRateDto> listStore() {
        return cacheService.getOrLoad(() -> exchangeRateRepository.listAll().stream()
                .map(r -> new StoreExchangeRateDto(r.getCurrency(), r.getRate(), r.getUpdatedAt()))
                .toList());
    }

    /** E-listAdminExchangeRates（实时直查，不走缓存；admin 视图全字段） */
    public List<AdminExchangeRateDto> listAdmin() {
        return exchangeRateRepository.listAll().stream()
                .map(r -> new AdminExchangeRateDto(r.getId(), r.getCurrency(), r.getRate(), r.getUpdatedBy(),
                        r.getUpdatedAt()))
                .toList();
    }

    /** E-updateAdminExchangeRate（V-TRD-058/059 + STEP-TRD-01~04；TX-TRD-011） */
    public AdminExchangeRateDto update(String currency, BigDecimal rate) {
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
        Long operatorId = audit.currentOperatorId();
        txRunner.inTx(() -> {
            exchangeRateRepository.updateRate(currency, rate, operatorId);
            // 同事务审计（changes before/after）
            audit.record(TradingAuditRecorder.ACTION_RATE_UPDATE, currency,
                    "{\"currency\":\"" + currency + "\",\"before\":\"" + before.getRate()
                            + "\",\"after\":\"" + rate + "\"}");
            // 提交后失效链（CP-031：JetCache 失效 → MQ content.invalidated → Cloudflare purge，EVT-TRD-005）
            afterCommit.run(() -> {
                cacheService.invalidate();
                eventsPublisher.publishExchangeRatesInvalidated();
            });
        });
        ExchangeRate updated = exchangeRateRepository.findByCurrency(currency);
        return new AdminExchangeRateDto(updated.getId(), updated.getCurrency(), updated.getRate(),
                updated.getUpdatedBy(), updated.getUpdatedAt());
    }
}
