package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.checkout.service.CheckoutConfigService;
import com.dreamy.domain.exchangerate.service.ExchangeRateService;
import com.dreamy.dto.TradingDtos.AdminExchangeRateDto;
import com.dreamy.dto.TradingDtos.CheckoutConfigDto;
import com.dreamy.dto.TradingDtos.ExchangeRateListResponse;
import com.dreamy.dto.TradingDtos.ExchangeRateUpdateRequest;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台汇率维护 + 结算配置控制器（trading-api-detail §11/§12，FLOW-P18，决策 14/24/28；RBAC `/settings`）。
 */
@RestController
public class AdminCheckoutSettingsController {

    private static final String PERMISSION = "/settings";

    private final ExchangeRateService exchangeRateService;
    private final CheckoutConfigService checkoutConfigService;

    public AdminCheckoutSettingsController(ExchangeRateService exchangeRateService,
                                           CheckoutConfigService checkoutConfigService) {
        this.exchangeRateService = exchangeRateService;
        this.checkoutConfigService = checkoutConfigService;
    }

    /** E-listAdminExchangeRates（实时直查，不走缓存；含 updated_by/updated_at） */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/exchange-rates")
    public ResponseEntity<R<ExchangeRateListResponse<AdminExchangeRateDto>>> listRates() {
        return ResponseEntity.ok(R.ok(new ExchangeRateListResponse<>(exchangeRateService.listAdmin())));
    }

    /** E-updateAdminExchangeRate（V-TRD-058/059；TX-TRD-011 + 失效链 EVT-TRD-005） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/exchange-rates/{currency}")
    public ResponseEntity<R<AdminExchangeRateDto>> updateRate(@PathVariable String currency,
                                                              @RequestBody ExchangeRateUpdateRequest request) {
        return ResponseEntity.ok(R.ok(exchangeRateService.update(currency,
                request == null ? null : request.rate())));
    }

    /** E-getAdminCheckoutConfig */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/checkout-config")
    public ResponseEntity<R<CheckoutConfigDto>> getConfig() {
        return ResponseEntity.ok(R.ok(checkoutConfigService.get()));
    }

    /** E-updateAdminCheckoutConfig（V-TRD-060/061；TX-TRD-012） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/checkout-config")
    public ResponseEntity<R<CheckoutConfigDto>> updateConfig(@RequestBody CheckoutConfigDto request) {
        return ResponseEntity.ok(R.ok(checkoutConfigService.update(
                request == null ? null : request.giftWrapFeeUsd(),
                request == null ? null : request.customRefundGraceHours())));
    }
}
