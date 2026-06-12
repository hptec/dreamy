package com.dreamy.controller;

import com.dreamy.i18n.RequestLocaleContext;
import com.dreamy.domain.coupon.service.CouponDomainService;
import com.dreamy.domain.flashsale.service.StoreFlashSaleService;
import com.dreamy.dto.StoreMarketingDtos.CouponBrief;
import com.dreamy.dto.StoreMarketingDtos.CouponValidateResponse;
import com.dreamy.dto.StoreMarketingDtos.StoreFlashSale;
import com.dreamy.i18n.MarketingMessageResolver;
import com.dreamy.support.MarketingFieldErrors;
import com.dreamy.support.MarketingParams;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 消费端促销控制器（E-MKT-09 闪购 + E-MKT-10 券校验）。
 * E-MKT-09 匿名公开（白名单精确路径 `/api/store/promotions/flash-sales`——不得用 `/api/store/promotions/**`）；
 * E-MKT-10 StoreBearerAuth（不入白名单，customer 主体仅做登录态校验——本期券不绑用户），不缓存。
 */
@RestController
public class StorePromotionController {

    private static final String CACHE_60 = "s-maxage=60";
    /** V-MKT-007 code trim 大写归一后 ^[A-Z0-9]+$ 且 ≤32 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]{1,32}$");

    private final StoreFlashSaleService flashSaleService;
    private final CouponDomainService couponDomainService;

    public StorePromotionController(StoreFlashSaleService flashSaleService,
                                    CouponDomainService couponDomainService) {
        this.flashSaleService = flashSaleService;
        this.couponDomainService = couponDomainService;
    }

    /** E-MKT-09 listStoreFlashSales（V-MKT-002；s-maxage=60 短） */
    @GetMapping("/api/store/promotions/flash-sales")
    public ResponseEntity<R<Map<String, List<StoreFlashSale>>>> listFlashSales(
            @RequestParam(required = false) String locale) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        String parsedLocale = MarketingParams.parseLocale(locale, errors);
        errors.throwIfAny();
        RequestLocaleContext.set(MarketingMessageResolver.toLocale(parsedLocale));
        List<StoreFlashSale> items = flashSaleService.list(parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_60).body(R.ok(Map.of("items", items)));
    }

    /** E-MKT-10 请求体（V-MKT-007/008） */
    public record CouponValidateRequest(String code, BigDecimal subtotal) {
    }

    /**
     * E-MKT-10 validateStoreCoupon：券不可用一律 200 + valid=false + reason_code（4227xx）不抛错；
     * 仅请求格式校验失败抛 422704。核销不在本端点（trading 进程内直调 SVC-MKT-01）。
     */
    @PostMapping("/api/store/promotions/coupons/validate")
    public ResponseEntity<R<CouponValidateResponse>> validateCoupon(
            @RequestBody CouponValidateRequest req,
            @RequestParam(required = false) String locale) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        // V-MKT-007 code 必填 trim 大写归一 ^[A-Z0-9]+$ ≤32
        String code = req.code() == null ? null : req.code().trim().toUpperCase();
        if (code == null || code.isEmpty()) {
            errors.reject("code", "required");
        } else if (!CODE_PATTERN.matcher(code).matches()) {
            errors.reject("code", "pattern_invalid");
        }
        // V-MKT-008 subtotal 必填数值 ≥0（USD 基准）
        if (req.subtotal() == null) {
            errors.reject("subtotal", "required");
        } else if (req.subtotal().signum() < 0) {
            errors.reject("subtotal", "range_invalid");
        }
        // locale 取 query/Accept-Language 缺省 en（STEP-MKT-04）
        String parsedLocale = MarketingParams.parseLocale(locale, errors);
        errors.throwIfAny();
        RequestLocaleContext.set(MarketingMessageResolver.toLocale(parsedLocale));

        CouponDomainService.CouponQuote quote = couponDomainService.validate(code, req.subtotal(), parsedLocale);
        CouponBrief brief = quote.coupon() == null ? null
                : new CouponBrief(quote.coupon().code(), quote.coupon().name(), quote.coupon().type(),
                quote.coupon().value(), quote.coupon().minAmount());
        CouponValidateResponse body = quote.valid()
                ? new CouponValidateResponse(true, null, quote.discountUsd(), quote.freeShipping(), brief)
                : new CouponValidateResponse(false, quote.reasonCode(), null, null, brief);
        return ResponseEntity.ok(R.ok(body));
    }
}
