package com.dreamy.domain.order.service;

import com.dreamy.domain.coupon.service.CouponDomainService;
import com.dreamy.port.ShippingOptionQuote;
import com.dreamy.port.ShippingQuotePort;
import com.dreamy.domain.address.entity.Address;
import com.dreamy.domain.address.repository.AddressRepository;
import com.dreamy.domain.cart.entity.CartItem;
import com.dreamy.domain.cart.repository.CartItemRepository;
import com.dreamy.domain.checkout.entity.CheckoutConfig;
import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.domain.exchangerate.entity.ExchangeRate;
import com.dreamy.domain.exchangerate.repository.ExchangeRateRepository;
import com.dreamy.domain.exchangerate.service.ExchangeRateService;
import com.dreamy.domain.tax.service.TaxCalculator;
import com.dreamy.dto.TradingDtos.CheckoutQuoteRequest;
import com.dreamy.dto.TradingDtos.CheckoutQuoteResponse;
import com.dreamy.dto.TradingDtos.ShippingOptionDto;
import com.dreamy.dto.TradingDtos.TaxBreakdownDto;
import com.dreamy.enums.Incoterm;
import com.dreamy.enums.ShippingServiceLevel;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.port.TradingCatalogSnapshotPort;
import com.dreamy.port.TradingCatalogSnapshotPort.ProductBrief;
import com.dreamy.port.TradingCatalogSnapshotPort.SkuBrief;
import com.dreamy.port.TradingDyeLotPort;
import com.dreamy.support.CountryCatalog;
import com.dreamy.support.TradingFieldErrors;
import com.dreamy.support.Money;
import com.dreamy.support.TradingParams;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 结算报价计算核心（FLOW-P05 quoteCheckout STEP-TRD-01~10；createOrder.STEP-TRD-03 复用全部服务端重算，
 * 不信任前端金额）。只读试算，不落库不缓存（决策 4 第 3 层）。
 * 跨域：ShippingQuotePort（F-036 多承运商）/ CouponDomainService.validate（无效不阻断报价）/
 * TradingDyeLotPort（决策 20.4）/ TradingCatalogSnapshotPort（行快照）。
 * L2 TRACE: trading-api-detail §3.1 / TX 边界外（纯读） / TC-TRD-001/002/009/010/011/065。
 */
@Service
public class CheckoutQuoteService {

    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final CheckoutConfigRepository checkoutConfigRepository;
    private final TradingCatalogSnapshotPort catalogSnapshotPort;
    private final ShippingQuotePort shippingQuotePort;
    private final CouponDomainService couponDomainService;
    private final TradingDyeLotPort dyeLotPort;
    private final TaxCalculator taxCalculator;

    public CheckoutQuoteService(CartItemRepository cartItemRepository, AddressRepository addressRepository,
                                ExchangeRateRepository exchangeRateRepository,
                                CheckoutConfigRepository checkoutConfigRepository,
                                TradingCatalogSnapshotPort catalogSnapshotPort, ShippingQuotePort shippingQuotePort,
                                CouponDomainService couponDomainService, TradingDyeLotPort dyeLotPort,
                                TaxCalculator taxCalculator) {
        this.cartItemRepository = cartItemRepository;
        this.addressRepository = addressRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.checkoutConfigRepository = checkoutConfigRepository;
        this.catalogSnapshotPort = catalogSnapshotPort;
        this.shippingQuotePort = shippingQuotePort;
        this.couponDomainService = couponDomainService;
        this.dyeLotPort = dyeLotPort;
        this.taxCalculator = taxCalculator;
    }

    /** 行价快照（lines 装配载体） */
    public record PricedLine(CartItem item, ProductBrief product, SkuBrief sku,
                             BigDecimal unitPrice, BigDecimal unitPriceUsd, int qty) {

        public boolean custom() {
            return item.getCustomSizeData() != null;
        }
    }

    /**
     * 报价计算结果（quote 出参装配 + createOrder 快照源）。
     * order-flow-complete §4.3：尾部追加 tax（订单币种）/ incoterm / duties / 锁汇说明 / 运费选项快照 / 预计送达 / 规范码。
     */
    public record Computation(String currency, BigDecimal rate, List<PricedLine> lines, BigDecimal subtotal,
                              BigDecimal subtotalUsd, List<ShippingOptionDto> shippingOptions, String selectedCarrier,
                              BigDecimal shippingFee, boolean giftWrap, BigDecimal giftWrapFee,
                              CouponDomainService.CouponQuote couponQuote, BigDecimal discountAmount,
                              BigDecimal totalAmount, Integer maxLeadTimeDays, boolean leadTimeWarning,
                              List<Long> dyeLotProductIds, Address address, String country,
                              BigDecimal taxAmount, List<TaxBreakdownDto> taxBreakdown, Incoterm incoterm,
                              boolean dutiesNotice, String dutiesNoticeText, boolean exchangeRateLockedNote,
                              ShippingServiceLevel serviceLevel, String selectedCarrierCode,
                              LocalDate estimatedDeliveryFrom, LocalDate estimatedDeliveryTo, Integer productionDays,
                              String countryCode, String regionCode) {

        /** 兼容旧 18 参构造（存量单测夹具：无税、DDU 提示、STANDARD） */
        public Computation(String currency, BigDecimal rate, List<PricedLine> lines, BigDecimal subtotal,
                           BigDecimal subtotalUsd, List<ShippingOptionDto> shippingOptions, String selectedCarrier,
                           BigDecimal shippingFee, boolean giftWrap, BigDecimal giftWrapFee,
                           CouponDomainService.CouponQuote couponQuote, BigDecimal discountAmount,
                           BigDecimal totalAmount, Integer maxLeadTimeDays, boolean leadTimeWarning,
                           List<Long> dyeLotProductIds, Address address, String country) {
            this(currency, rate, lines, subtotal, subtotalUsd, shippingOptions, selectedCarrier, shippingFee, giftWrap,
                    giftWrapFee, couponQuote, discountAmount, totalAmount, maxLeadTimeDays, leadTimeWarning,
                    dyeLotProductIds, address, country, Money.zero(), List.of(), Incoterm.DDU, true, null, false,
                    ShippingServiceLevel.STANDARD, null, null, null, null,
                    address == null ? CountryCatalog.resolveCode(country) : address.getCountryCode(),
                    address == null ? null : address.getRegionCode());
        }
    }

    /** E-quoteCheckout 入口（V-TRD-015~020 + STEP-TRD-01~10） */
    public CheckoutQuoteResponse quote(Long customerId, CheckoutQuoteRequest request, String locale) {
        Computation c = compute(customerId,
                request.addressId(), request.country(), request.currency(), request.carrier(),
                request.couponCode(), Boolean.TRUE.equals(request.giftWrap()), request.weddingDate(),
                locale, false, request.serviceLevel());
        CouponDomainService.CouponQuote couponQuote = c.couponQuote();
        return new CheckoutQuoteResponse(
                c.currency(), c.rate(), c.subtotal(), c.shippingOptions(), c.shippingFee(), c.giftWrapFee(),
                c.discountAmount(), c.totalAmount(),
                couponQuote == null ? null : couponQuote.valid(),
                couponQuote == null ? null : couponQuote.reasonCode(),
                c.leadTimeWarning() ? Boolean.TRUE : Boolean.FALSE,
                c.maxLeadTimeDays(), c.dyeLotProductIds(),
                c.taxAmount(), c.taxBreakdown(), c.incoterm() == null ? null : c.incoterm().getKey(),
                c.dutiesNotice(), c.dutiesNoticeText(), c.exchangeRateLockedNote(),
                c.serviceLevel() == null ? null : c.serviceLevel().getKey(), c.selectedCarrierCode(),
                c.estimatedDeliveryFrom(), c.estimatedDeliveryTo(), c.productionDays(), c.countryCode(),
                c.regionCode());
    }

    /**
     * 报价/下单共用核心（strict=true：createOrder 口径——下架行 422601 details.unavailable_product_ids，
     * 无效券抛 MarketingException 透传；strict=false：quote 口径——下架行剔除，无效券不阻断）。
     */
    public Computation compute(Long customerId, Long addressId, String country, String currency, String carrier,
                               String couponCode, boolean giftWrap, LocalDate weddingDate,
                               String locale, boolean strict) {
        return compute(customerId, addressId, country, currency, carrier, couponCode, giftWrap, weddingDate, locale,
                strict, null);
    }

    /**
     * order-flow-complete §4.3 报价链：锁汇(含 spread) → 行价 → 运费选项(zone × carrier × level) → 礼品包装 → 券
     * → 税费(country_code[+region_code]) → total(amount_version=2) → 预计送达。
     *
     * @param carrier      承运商 code 或 name（兼容旧 name；strict 口径下未命中报价 → 422601 carrier invalid_enum）
     * @param serviceLevel 1=STANDARD 2=EXPRESS（缺省 STANDARD 最便宜）
     */
    public Computation compute(Long customerId, Long addressId, String country, String currency, String carrier,
                               String couponCode, boolean giftWrap, LocalDate weddingDate,
                               String locale, boolean strict, Integer serviceLevel) {
        TradingFieldErrors errors = new TradingFieldErrors();
        // V-TRD-015/023 币种（422605）
        if (!TradingParams.isSupportedCurrency(currency)) {
            throw new TradingException(TradingErrorCode.CURRENCY_NOT_SUPPORTED);
        }
        // V-TRD-017/024 carrier：改由 carrier 表（报价结果）校验（order-flow-complete C）；此处仅长度
        String requestedCarrier = TradingParams.checkMaxLength(carrier, 64, "carrier", errors);
        ShippingServiceLevel requestedLevel = serviceLevel == null ? null : ShippingServiceLevel.of(serviceLevel);
        if (serviceLevel != null && requestedLevel == null) {
            errors.reject("service_level", "invalid_enum");
        }
        // V-TRD-018 coupon_code ≤32
        String coupon = TradingParams.checkMaxLength(couponCode, 32, "coupon_code", errors);
        // V-TRD-019 wedding_date ≥ 今天（CV-TRD-012）
        if (weddingDate != null && weddingDate.isBefore(LocalDate.now())) {
            errors.reject("wedding_date", "range_invalid");
        }
        // V-TRD-016 address_id 与 country 至少其一
        Address address = null;
        if (addressId != null) {
            address = addressRepository.findByIdAndCustomerId(addressId, customerId);
            if (address == null) {
                throw new TradingException(TradingErrorCode.ADDRESS_NOT_FOUND);
            }
        } else if (TradingParams.trimToNull(country) == null) {
            errors.reject("address_id", "required");
        }
        errors.throwIfAny();
        String resolvedCountry = address != null ? address.getCountry() : country.trim();
        // 规范码：地址 country_code 优先；文本兜底解析（税费匹配只读规范码）
        String countryCode = address != null && address.getCountryCode() != null
                ? address.getCountryCode() : CountryCatalog.resolveCode(resolvedCountry);
        String regionCode = address != null ? address.getRegionCode() : null;

        // STEP-TRD-01 读 cart + 快照
        List<CartItem> cartItems = cartItemRepository.listByCustomerId(customerId);
        if (cartItems.isEmpty()) {
            throw new TradingException(TradingErrorCode.FIELD_VALIDATION_FAILED, Map.of("reason", "cart_empty"));
        }
        List<Long> productIds = cartItems.stream().map(CartItem::getProductId).distinct().toList();
        Map<Long, ProductBrief> products = catalogSnapshotPort.getProductBriefs(productIds, locale);
        List<Long> skuIds = cartItems.stream().map(CartItem::getSkuId).filter(java.util.Objects::nonNull).toList();
        Map<Long, SkuBrief> skus = catalogSnapshotPort.getSkus(skuIds);

        // 下架/缺失行处理（strict：422601 details.unavailable_product_ids；quote：剔除）
        List<Long> unavailable = new ArrayList<>();
        List<CartItem> effective = new ArrayList<>();
        for (CartItem item : cartItems) {
            ProductBrief product = products.get(item.getProductId());
            if (product == null || !product.published()) {
                unavailable.add(item.getProductId());
            } else {
                effective.add(item);
            }
        }
        if (strict && !unavailable.isEmpty()) {
            throw new TradingException(TradingErrorCode.FIELD_VALIDATION_FAILED,
                    Map.of("unavailable_product_ids", unavailable));
        }
        if (effective.isEmpty()) {
            throw new TradingException(TradingErrorCode.FIELD_VALIDATION_FAILED, Map.of("reason", "cart_empty"));
        }

        // STEP-TRD-07 试算汇率（USD 恒 1；下单时本值即锁汇快照；order-flow-complete E：× (1 + spread) HALF_UP 6 位）
        CheckoutConfig config = checkoutConfigRepository.getSingleton();
        int spread = config == null || config.getExchangeRateSpreadScaled() == null ? 0 : config.getExchangeRateSpreadScaled();
        BigDecimal rate = "USD".equals(currency) ? BigDecimal.ONE : ExchangeRateService.applySpread(resolveRate(currency), spread);
        boolean exchangeRateLockedNote = !"USD".equals(currency);

        // STEP-TRD-02 行价与小计（覆盖价优先，HALF_UP 2 位）
        List<PricedLine> lines = new ArrayList<>();
        BigDecimal subtotal = Money.zero();
        BigDecimal subtotalUsd = Money.zero();
        for (CartItem item : effective) {
            ProductBrief product = products.get(item.getProductId());
            SkuBrief sku = item.getSkuId() == null ? null : skus.get(item.getSkuId());
            if (strict && item.getSkuId() != null && sku == null) {
                // V-TRD-026 现货 sku 失效复核
                throw new TradingException(TradingErrorCode.SKU_REQUIRED, Map.of("sku_id", item.getSkuId()));
            }
            BigDecimal unitPrice = Money.unitPrice(product.price(), product.multiCurrencyPrices(), currency, rate);
            BigDecimal unitPriceUsd = product.price();
            int qty = item.getQty();
            lines.add(new PricedLine(item, product, sku, unitPrice, unitPriceUsd, qty));
            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(qty)));
            subtotalUsd = subtotalUsd.add(unitPriceUsd.multiply(BigDecimal.valueOf(qty)));
        }
        subtotal = subtotal.setScale(2, java.math.RoundingMode.HALF_UP);
        subtotalUsd = subtotalUsd.setScale(2, java.math.RoundingMode.HALF_UP);

        // STEP-TRD-03/04 多承运商报价组装（F-036 + order-flow-complete E：zone × carrier × level；fee 换算订单币种）
        List<ShippingOptionQuote> quotes = shippingQuotePort.quoteOptions(resolvedCountry, subtotalUsd);
        List<ShippingOptionDto> shippingOptions = new ArrayList<>();
        String selectedCarrier = null;
        String selectedCarrierCode = null;
        ShippingServiceLevel selectedLevel = null;
        BigDecimal shippingFee = Money.zero();
        BigDecimal shippingFeeUsd = Money.zero();
        Integer transitMin = null;
        Integer transitMax = null;
        int productionDays = productionDays(lines, config);
        LocalDate today = LocalDate.now();
        if (quotes != null && !quotes.isEmpty()) {
            ShippingOptionQuote selected = selectQuote(quotes, requestedCarrier, requestedLevel);
            if (selected == null && strict && requestedCarrier != null) {
                throw TradingException.fieldValidation("carrier", "invalid_enum");
            }
            if (selected == null) {
                selected = com.dreamy.domain.shippingrate.service.ShippingQuotePreviewService.pickSelected(quotes, requestedLevel);
            }
            for (ShippingOptionQuote q : quotes) {
                boolean isSelected = q == selected;
                BigDecimal fee = Money.toCurrency(q.feeUsd(), rate);
                LocalDate etaFrom = q.transitDaysMin() == null ? null : today.plusDays(productionDays + q.transitDaysMin());
                LocalDate etaTo = q.transitDaysMax() == null ? null : today.plusDays(productionDays + q.transitDaysMax());
                shippingOptions.add(new ShippingOptionDto(q.carrier(), fee, q.leadTime(), isSelected, q.carrierCode(),
                        q.carrier(), q.serviceLevel(), q.transitDaysMin(), q.transitDaysMax(), etaFrom, etaTo));
                if (isSelected) {
                    selectedCarrier = q.carrier();
                    selectedCarrierCode = q.carrierCode();
                    selectedLevel = q.serviceLevel() == null ? ShippingServiceLevel.STANDARD : ShippingServiceLevel.of(q.serviceLevel());
                    shippingFee = fee;
                    shippingFeeUsd = q.feeUsd();
                    transitMin = q.transitDaysMin();
                    transitMax = q.transitDaysMax();
                }
            }
        } else if (strict && requestedCarrier != null) {
            throw TradingException.fieldValidation("carrier", "invalid_enum");
        }

        // STEP-TRD-05 礼品包装费（决策 28：CheckoutConfig 固定 USD 价 × rate）
        BigDecimal giftWrapFee = Money.zero();
        if (giftWrap) {
            giftWrapFee = Money.toCurrency(config.getGiftWrapFeeUsd(), rate);
        }

        // STEP-TRD-06 券校验（quote：无效不阻断 discount=0；strict：无效抛 MarketingException 透传 4227xx）
        CouponDomainService.CouponQuote couponQuote = null;
        BigDecimal discountAmount = Money.zero();
        if (coupon != null) {
            couponQuote = couponDomainService.validate(coupon, subtotalUsd, locale);
            if (couponQuote.valid()) {
                if (couponQuote.freeShipping()) {
                    // free_shipping 券：减免额 = 所选承运商运费（保持金额恒等式）
                    discountAmount = shippingFee;
                } else {
                    discountAmount = Money.toCurrency(couponQuote.discountUsd(), rate);
                }
            } else if (strict) {
                throw marketingExceptionOf(couponQuote.reasonCode());
            }
        }
        // 防御：减免不得超过应付项之和（恒等式非负）
        BigDecimal payable = subtotal.add(shippingFee).add(giftWrapFee);
        if (discountAmount.compareTo(payable) > 0) {
            discountAmount = payable;
        }

        // order-flow-complete F 税费：country_code[+region_code]（USD 基准比较起征额；金额按订单币种 HALF_UP）
        BigDecimal discountUsd = rate.signum() > 0
                ? discountAmount.divide(rate, 2, java.math.RoundingMode.HALF_UP) : Money.zero();
        TaxCalculator.TaxQuote tax = taxCalculator.compute(countryCode, regionCode, subtotalUsd, discountUsd,
                shippingFeeUsd, currency, rate);
        BigDecimal taxAmount = tax.taxAmount();

        // STEP-TRD-08 金额恒等式（CV-TRD-003；amount_version=2：+ tax_amount）
        BigDecimal totalAmount = Money.total(subtotal, shippingFee, giftWrapFee, discountAmount).add(taxAmount)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        // STEP-TRD-09 交期复核（决策 20.6）
        Integer maxLeadTimeDays = lines.stream()
                .map(l -> l.product().leadTimeDays())
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
        boolean leadTimeWarning = weddingDate != null && maxLeadTimeDays != null
                && LocalDate.now().plusDays(maxLeadTimeDays).isAfter(weddingDate);

        // STEP-TRD-10 dye lot 提示（决策 20.4；showroom 未就绪 stub 空数组）
        List<Long> dyeLotProductIds = dyeLotPort.hintProductIds(customerId,
                lines.stream().map(l -> l.product().id()).distinct().toList());

        // order-flow-complete §4.3 预计送达 = today + production_days + transit_days_min..max
        LocalDate etaFrom = transitMin == null ? null : today.plusDays(productionDays + transitMin);
        LocalDate etaTo = transitMax == null ? null : today.plusDays(productionDays + transitMax);

        return new Computation(currency, rate, lines, subtotal, subtotalUsd, shippingOptions, selectedCarrier,
                shippingFee, giftWrap, giftWrapFee, couponQuote, discountAmount, totalAmount,
                maxLeadTimeDays, leadTimeWarning, dyeLotProductIds, address, resolvedCountry,
                taxAmount, tax.breakdown(), tax.incoterm(), tax.dutiesNotice(), tax.noticeText(),
                exchangeRateLockedNote, selectedLevel, selectedCarrierCode, etaFrom, etaTo, productionDays,
                countryCode, regionCode);
    }

    /** 制作周期 = max(商品 lead_time_days 最大值（若有）, checkout_config.production_days_default) */
    static int productionDays(List<PricedLine> lines, CheckoutConfig config) {
        int configured = config == null || config.getProductionDaysDefault() == null ? 21 : config.getProductionDaysDefault();
        int lead = lines.stream().map(l -> l.product().leadTimeDays()).filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0);
        return Math.max(lead, configured);
    }

    /**
     * 选中规则：请求 carrier（code 或 name，大小写不敏感）命中 → 该承运商内按请求等级（缺省 STANDARD）取项，
     * 无该等级则取该承运商最便宜；未命中 → null（quote 口径回退缺省；strict 口径 422601）。
     */
    static ShippingOptionQuote selectQuote(List<ShippingOptionQuote> quotes, String requestedCarrier,
                                           ShippingServiceLevel requestedLevel) {
        if (requestedCarrier == null) {
            return null;
        }
        List<ShippingOptionQuote> hits = quotes.stream()
                .filter(q -> requestedCarrier.equalsIgnoreCase(q.carrier())
                        || (q.carrierCode() != null && requestedCarrier.equalsIgnoreCase(q.carrierCode())))
                .toList();
        if (hits.isEmpty()) {
            return null;
        }
        ShippingServiceLevel level = requestedLevel == null ? ShippingServiceLevel.STANDARD : requestedLevel;
        return hits.stream().filter(q -> level.getKey().equals(q.serviceLevel())).findFirst()
                .orElseGet(() -> hits.stream().min(Comparator.comparing(ShippingOptionQuote::feeUsd)).orElse(hits.get(0)));
    }

    /** 汇率解析（USD 恒 1；汇率行缺失 → 422605 防御口径） */
    public BigDecimal resolveRate(String currency) {
        if ("USD".equals(currency)) {
            return BigDecimal.ONE;
        }
        ExchangeRate row = exchangeRateRepository.findByCurrency(currency);
        if (row == null || row.getRate() == null || row.getRate().signum() <= 0) {
            throw new TradingException(TradingErrorCode.CURRENCY_NOT_SUPPORTED, Map.of("currency", currency));
        }
        return row.getRate();
    }

    /** 券 reason_code → MarketingException（createOrder 透传 422701/422702/422703） */
    private RuntimeException marketingExceptionOf(Integer reasonCode) {
        com.dreamy.error.MarketingErrorCode code;
        if (reasonCode != null && reasonCode == com.dreamy.error.MarketingErrorCode.COUPON_EXHAUSTED.getCode()) {
            code = com.dreamy.error.MarketingErrorCode.COUPON_EXHAUSTED;
        } else if (reasonCode != null
                && reasonCode == com.dreamy.error.MarketingErrorCode.COUPON_MIN_AMOUNT_NOT_MET.getCode()) {
            code = com.dreamy.error.MarketingErrorCode.COUPON_MIN_AMOUNT_NOT_MET;
        } else {
            code = com.dreamy.error.MarketingErrorCode.COUPON_INVALID;
        }
        return new com.dreamy.error.MarketingException(code);
    }
}
