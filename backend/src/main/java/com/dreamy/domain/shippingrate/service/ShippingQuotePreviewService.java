package com.dreamy.domain.shippingrate.service;

import com.dreamy.domain.tax.service.TaxCalculator;
import com.dreamy.dto.TradingDtos.ShippingOptionDto;
import com.dreamy.dto.TradingDtos.ShippingQuotePreviewRequest;
import com.dreamy.dto.TradingDtos.ShippingQuotePreviewResponse;
import com.dreamy.enums.ShippingServiceLevel;
import com.dreamy.error.ShippingException;
import com.dreamy.port.ShippingOptionQuote;
import com.dreamy.support.CountryCatalog;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 后台运费/税费试算（order-flow-complete §3.2 POST /api/admin/shipping/quote-preview）：
 * {country_code, region_code?, subtotal_usd, service_level?} → zone + 各承运商×等级 USD 报价 + 税费预估（USD, rate=1）。
 * 不依赖购物车/汇率；预计送达仅按 today + transit_days 展示（不含制作周期）。
 */
@Service
public class ShippingQuotePreviewService {

    private final ShippingQuoteService quoteService;
    private final TaxCalculator taxCalculator;

    public ShippingQuotePreviewService(ShippingQuoteService quoteService, TaxCalculator taxCalculator) {
        this.quoteService = quoteService;
        this.taxCalculator = taxCalculator;
    }

    public ShippingQuotePreviewResponse preview(ShippingQuotePreviewRequest req) {
        if (req == null) {
            throw ShippingException.fieldValidation("_body");
        }
        String cc = req.countryCode() == null ? null : req.countryCode().trim().toUpperCase(Locale.ROOT);
        if (cc == null || !CountryCatalog.isKnownCode(cc)) {
            throw ShippingException.fieldValidation("country_code");
        }
        BigDecimal subtotal = req.subtotalUsd() == null ? BigDecimal.ZERO : req.subtotalUsd();
        if (subtotal.signum() < 0) {
            throw ShippingException.fieldValidation("subtotal_usd");
        }
        ShippingServiceLevel requested = req.serviceLevel() == null ? null : ShippingServiceLevel.of(req.serviceLevel());
        if (req.serviceLevel() != null && requested == null) {
            throw ShippingException.fieldValidation("service_level");
        }
        String region = req.regionCode() == null ? null : CountryCatalog.resolveRegionCode(cc, req.regionCode());
        String zone = CountryCatalog.zoneOf(cc);
        List<ShippingOptionQuote> quotes = quoteService.quoteByZone(zone, subtotal);
        // selected：请求等级下最便宜；缺省 STANDARD 最便宜（无 STANDARD 时全体最便宜）
        ShippingOptionQuote selected = pickSelected(quotes, requested);
        LocalDate today = LocalDate.now();
        List<ShippingOptionDto> options = new ArrayList<>();
        for (ShippingOptionQuote q : quotes) {
            boolean isSelected = q == selected;
            options.add(new ShippingOptionDto(q.carrier(), q.feeUsd(), q.leadTime(), isSelected, q.carrierCode(),
                    q.carrier(), q.serviceLevel(), q.transitDaysMin(), q.transitDaysMax(),
                    q.transitDaysMin() == null ? null : today.plusDays(q.transitDaysMin()),
                    q.transitDaysMax() == null ? null : today.plusDays(q.transitDaysMax())));
        }
        BigDecimal shippingUsd = selected == null ? BigDecimal.ZERO : selected.feeUsd();
        TaxCalculator.TaxQuote tax = taxCalculator.compute(cc, region, subtotal, BigDecimal.ZERO, shippingUsd,
                "USD", BigDecimal.ONE);
        return new ShippingQuotePreviewResponse(zone, options, tax.taxAmount(), tax.breakdown(),
                tax.incoterm() == null ? null : tax.incoterm().getKey(), tax.dutiesNotice());
    }

    /** 缺省选中规则（与 CheckoutQuoteService 一致）：请求等级内最便宜 → STANDARD 最便宜 → 全体最便宜 */
    public static ShippingOptionQuote pickSelected(List<ShippingOptionQuote> quotes, ShippingServiceLevel requested) {
        if (quotes == null || quotes.isEmpty()) {
            return null;
        }
        Comparator<ShippingOptionQuote> byFee = Comparator.comparing(ShippingOptionQuote::feeUsd);
        if (requested != null) {
            ShippingOptionQuote hit = quotes.stream().filter(q -> requested.getKey().equals(q.serviceLevel()))
                    .min(byFee).orElse(null);
            if (hit != null) {
                return hit;
            }
        }
        return quotes.stream().filter(q -> q.serviceLevel() == null || q.serviceLevel() == 1)
                .min(byFee).orElseGet(() -> quotes.stream().min(byFee).orElse(quotes.get(0)));
    }
}
