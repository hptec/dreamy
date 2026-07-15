package com.dreamy.domain.exchangerate.service;

import com.dreamy.domain.exchangerate.entity.ExchangeRate;
import com.dreamy.domain.exchangerate.repository.ExchangeRateRepository;
import com.dreamy.domain.cache.service.CacheInvalidationTarget;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.dto.TradingDtos.AdminExchangeRateDto;
import com.dreamy.dto.TradingDtos.StoreExchangeRateDto;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.ExchangeRateCacheService;
import com.dreamy.infra.TradingAuditRecorder;
import com.dreamy.infra.TradingTxRunner;
import com.dreamy.support.TradingParams;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 汇率服务（trading-api-detail §8/§11，FLOW-P18，决策 14；derived_scope host TASK-051）。
 * store 读：JetCache 两级 trading:exchange-rates（CACHE-TRD-001）；
 * admin 维护：TX-TRD-011 同事务审计并创建可追踪的缓存失效任务。
 * 语义（决策 14）：仅影响新订单锁汇，既有订单 exchange_rate 快照不变。
 */
@Service
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateCacheService cacheService;
    private final TradingTxRunner txRunner;
    private final TradingAuditRecorder audit;
    private final CacheInvalidationTaskService cacheTasks;

    public ExchangeRateService(ExchangeRateRepository exchangeRateRepository,
                               ExchangeRateCacheService cacheService, TradingTxRunner txRunner,
                               TradingAuditRecorder audit, CacheInvalidationTaskService cacheTasks) {
        this.exchangeRateRepository = exchangeRateRepository;
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
            cacheTasks.enqueue(CacheInvalidationTaskService.MODE_BUSINESS_WRITE, "exchange_rate.update",
                    "exchange_rate", currency, currency, List.of(CacheInvalidationTarget.TRADING_EXCHANGE_RATES),
                    null, Map.of("rate", rate), null);
        });
        ExchangeRate updated = exchangeRateRepository.findByCurrency(currency);
        return new AdminExchangeRateDto(updated.getId(), updated.getCurrency(), updated.getRate(),
                updated.getUpdatedBy(), updated.getUpdatedAt());
    }
}
