package com.dreamy.trading.controller;

import com.dreamy.trading.domain.rate.service.ExchangeRateService;
import com.dreamy.trading.dto.TradingDtos.ExchangeRateListResponse;
import com.dreamy.trading.dto.TradingDtos.StoreExchangeRateDto;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费端汇率控制器（trading-api-detail §8，FLOW-P18，决策 14；匿名公开——
 * StoreJwtFilter 白名单 /api/store/exchange-rates）。
 * CDN s-maxage=600（CACHE-TRD-001）；payload 不含 updated_by。
 */
@RestController
public class StoreExchangeRateController {

    private static final String CACHE_600 = "s-maxage=600";

    private final ExchangeRateService exchangeRateService;

    public StoreExchangeRateController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    /** E-listStoreExchangeRates（USD 恒 rate=1；下单锁汇以服务端为准，本端点仅展示换算） */
    @GetMapping("/api/store/exchange-rates")
    public ResponseEntity<R<ExchangeRateListResponse<StoreExchangeRateDto>>> list() {
        return ResponseEntity.ok().header("Cache-Control", CACHE_600)
                .body(R.ok(new ExchangeRateListResponse<>(exchangeRateService.listStore())));
    }
}
