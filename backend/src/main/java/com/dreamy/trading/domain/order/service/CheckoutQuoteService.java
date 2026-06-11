package com.dreamy.trading.domain.order.service;

import com.dreamy.marketing.domain.coupon.service.CouponDomainService;
import com.dreamy.shipping.api.ShippingOptionQuote;
import com.dreamy.shipping.api.ShippingQuotePort;
import com.dreamy.trading.domain.address.entity.Address;
import com.dreamy.trading.domain.address.repository.AddressRepository;
import com.dreamy.trading.domain.cart.entity.CartItem;
import com.dreamy.trading.domain.cart.repository.CartItemRepository;
import com.dreamy.trading.domain.checkout.entity.CheckoutConfig;
import com.dreamy.trading.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.trading.domain.rate.entity.ExchangeRate;
import com.dreamy.trading.domain.rate.repository.ExchangeRateRepository;
import com.dreamy.trading.dto.TradingDtos.CheckoutQuoteRequest;
import com.dreamy.trading.dto.TradingDtos.CheckoutQuoteResponse;
import com.dreamy.trading.dto.TradingDtos.ShippingOptionDto;
import com.dreamy.trading.error.TradingErrorCode;
import com.dreamy.trading.error.TradingException;
import com.dreamy.trading.port.CatalogSnapshotPort;
import com.dreamy.trading.port.CatalogSnapshotPort.ProductBrief;
import com.dreamy.trading.port.CatalogSnapshotPort.SkuBrief;
import com.dreamy.trading.port.DyeLotPort;
import com.dreamy.trading.support.FieldErrors;
import com.dreamy.trading.support.Money;
import com.dreamy.trading.support.TradingParams;
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
 * DyeLotPort（决策 20.4）/ CatalogSnapshotPort（行快照）。
 * L2 TRACE: trading-api-detail §3.1 / TX 边界外（纯读） / TC-TRD-001/002/009/010/011/065。
 */
@Service
public class CheckoutQuoteService {

    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final CheckoutConfigRepository checkoutConfigRepository;
    private final CatalogSnapshotPort catalogSnapshotPort;
    private final ShippingQuotePort shippingQuotePort;
    private final CouponDomainService couponDomainService;
    private final DyeLotPort dyeLotPort;

    public CheckoutQuoteService(CartItemRepository cartItemRepository, AddressRepository addressRepository,
                                ExchangeRateRepository exchangeRateRepository,
                                CheckoutConfigRepository checkoutConfigRepository,
                                CatalogSnapshotPort catalogSnapshotPort, ShippingQuotePort shippingQuotePort,
                                CouponDomainService couponDomainService, DyeLotPort dyeLotPort) {
        this.cartItemRepository = cartItemRepository;
        this.addressRepository = addressRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.checkoutConfigRepository = checkoutConfigRepository;
        this.catalogSnapshotPort = catalogSnapshotPort;
        this.shippingQuotePort = shippingQuotePort;
        this.couponDomainService = couponDomainService;
        this.dyeLotPort = dyeLotPort;
    }

    /** 行价快照（lines 装配载体） */
    public record PricedLine(CartItem item, ProductBrief product, SkuBrief sku,
                             BigDecimal unitPrice, BigDecimal unitPriceUsd, int qty) {

        public boolean custom() {
            return item.getCustomSizeData() != null;
        }
    }

    /** 报价计算结果（quote 出参装配 + createOrder 快照源） */
    public record Computation(String currency, BigDecimal rate, List<PricedLine> lines, BigDecimal subtotal,
                              BigDecimal subtotalUsd, List<ShippingOptionDto> shippingOptions, String selectedCarrier,
                              BigDecimal shippingFee, boolean giftWrap, BigDecimal giftWrapFee,
                              CouponDomainService.CouponQuote couponQuote, BigDecimal discountAmount,
                              BigDecimal totalAmount, Integer maxLeadTimeDays, boolean leadTimeWarning,
                              List<Long> dyeLotProductIds, Address address, String country) {
    }

    /** E-quoteCheckout 入口（V-TRD-015~020 + STEP-TRD-01~10） */
    public CheckoutQuoteResponse quote(Long customerId, CheckoutQuoteRequest request, String locale) {
        Computation c = compute(customerId,
                request.addressId(), request.country(), request.currency(), request.carrier(),
                request.couponCode(), Boolean.TRUE.equals(request.giftWrap()), request.weddingDate(),
                locale, false);
        CouponDomainService.CouponQuote couponQuote = c.couponQuote();
        return new CheckoutQuoteResponse(
                c.currency(), c.rate(), c.subtotal(), c.shippingOptions(), c.shippingFee(), c.giftWrapFee(),
                c.discountAmount(), c.totalAmount(),
                couponQuote == null ? null : couponQuote.valid(),
                couponQuote == null ? null : couponQuote.reasonCode(),
                c.leadTimeWarning() ? Boolean.TRUE : Boolean.FALSE,
                c.maxLeadTimeDays(), c.dyeLotProductIds());
    }

    /**
     * 报价/下单共用核心（strict=true：createOrder 口径——下架行 422601 details.unavailable_product_ids，
     * 无效券抛 MarketingException 透传；strict=false：quote 口径——下架行剔除，无效券不阻断）。
     */
    public Computation compute(Long customerId, Long addressId, String country, String currency, String carrier,
                               String couponCode, boolean giftWrap, LocalDate weddingDate,
                               String locale, boolean strict) {
        FieldErrors errors = new FieldErrors();
        // V-TRD-015/023 币种（422605）
        if (!TradingParams.isSupportedCurrency(currency)) {
            throw new TradingException(TradingErrorCode.CURRENCY_NOT_SUPPORTED);
        }
        // V-TRD-017/024 carrier 枚举
        if (carrier != null && !TradingParams.isSupportedCarrier(carrier)) {
            errors.reject("carrier", "invalid_enum");
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

        // STEP-TRD-07 试算汇率（USD 恒 1；下单时本值即锁汇快照）
        BigDecimal rate = resolveRate(currency);

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

        // STEP-TRD-03/04 多承运商报价组装（F-036；fee 换算订单币种；selected 规则 TC-TRD-010）
        List<ShippingOptionQuote> quotes = shippingQuotePort.quoteOptions(resolvedCountry, subtotalUsd);
        List<ShippingOptionDto> shippingOptions = new ArrayList<>();
        String selectedCarrier = null;
        BigDecimal shippingFee = Money.zero();
        if (quotes != null && !quotes.isEmpty()) {
            String requested = carrier != null && quotes.stream().anyMatch(q -> q.carrier().equals(carrier))
                    ? carrier : null;
            ShippingOptionQuote cheapest = quotes.stream()
                    .min(Comparator.comparing(ShippingOptionQuote::feeUsd))
                    .orElse(quotes.get(0));
            String effectiveSelected = requested != null ? requested : cheapest.carrier();
            for (ShippingOptionQuote q : quotes) {
                boolean selected = q.carrier().equals(effectiveSelected);
                BigDecimal fee = Money.toCurrency(q.feeUsd(), rate);
                shippingOptions.add(new ShippingOptionDto(q.carrier(), fee, q.leadTime(), selected));
                if (selected) {
                    selectedCarrier = q.carrier();
                    shippingFee = fee;
                }
            }
        }

        // STEP-TRD-05 礼品包装费（决策 28：CheckoutConfig 固定 USD 价 × rate）
        BigDecimal giftWrapFee = Money.zero();
        if (giftWrap) {
            CheckoutConfig config = checkoutConfigRepository.getSingleton();
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

        // STEP-TRD-08 金额恒等式（CV-TRD-003）
        BigDecimal totalAmount = Money.total(subtotal, shippingFee, giftWrapFee, discountAmount);

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

        return new Computation(currency, rate, lines, subtotal, subtotalUsd, shippingOptions, selectedCarrier,
                shippingFee, giftWrap, giftWrapFee, couponQuote, discountAmount, totalAmount,
                maxLeadTimeDays, leadTimeWarning, dyeLotProductIds, address, resolvedCountry);
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
        com.dreamy.marketing.error.MarketingErrorCode code;
        if (reasonCode != null && reasonCode == com.dreamy.marketing.error.MarketingErrorCode.COUPON_EXHAUSTED.getCode()) {
            code = com.dreamy.marketing.error.MarketingErrorCode.COUPON_EXHAUSTED;
        } else if (reasonCode != null
                && reasonCode == com.dreamy.marketing.error.MarketingErrorCode.COUPON_MIN_AMOUNT_NOT_MET.getCode()) {
            code = com.dreamy.marketing.error.MarketingErrorCode.COUPON_MIN_AMOUNT_NOT_MET;
        } else {
            code = com.dreamy.marketing.error.MarketingErrorCode.COUPON_INVALID;
        }
        return new com.dreamy.marketing.error.MarketingException(code);
    }
}
