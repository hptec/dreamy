package com.dreamy.controller;

import com.dreamy.domain.shippingrate.service.StoreCountryService;
import com.dreamy.dto.TradingDtos.CountryListResponse;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费端物流公开端点（order-flow-complete D：GET /api/store/shipping/countries；公开白名单 + JetCache shipping:countries）。
 */
@RestController
public class StoreShippingController {

    private final StoreCountryService countryService;

    public StoreShippingController(StoreCountryService countryService) {
        this.countryService = countryService;
    }

    /** ISO 国家列表 [{code,name,zone,supported,regions[]}] */
    @GetMapping("/api/store/shipping/countries")
    public ResponseEntity<R<CountryListResponse>> countries() {
        return ResponseEntity.ok().header("Cache-Control", "public, max-age=600")
                .body(R.ok(new CountryListResponse(countryService.list())));
    }
}
