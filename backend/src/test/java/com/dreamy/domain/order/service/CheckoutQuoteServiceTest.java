package com.dreamy.domain.order.service;

import com.dreamy.domain.coupon.service.CouponDomainService;
import com.dreamy.domain.coupon.service.CouponDomainService.CouponQuote;
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
import com.dreamy.dto.TradingDtos.CheckoutQuoteRequest;
import com.dreamy.dto.TradingDtos.CheckoutQuoteResponse;
import com.dreamy.dto.TradingDtos.ShippingOptionDto;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.port.TradingCatalogSnapshotPort;
import com.dreamy.port.TradingCatalogSnapshotPort.ProductBrief;
import com.dreamy.port.TradingCatalogSnapshotPort.SkuBrief;
import com.dreamy.port.TradingDyeLotPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 结算报价计算核心单测（FLOW-P05）。
 * L2 TRACE: TC-TRD-001/002（金额联动）/ TC-TRD-009/010 [P1]（报价组装 + selected 规则）/
 * TC-TRD-011 [P2]（交期复核）/ TC-TRD-065 [P1]（空车 422601 / 无效券不阻断 / gift_wrap 联动）/ V-TRD-015。
 */
@ExtendWith(MockitoExtension.class)
class CheckoutQuoteServiceTest {

    private static final long CUSTOMER = 7L;
    private static final long PRODUCT = 11L;
    private static final long SKU = 21L;
    private static final long ADDRESS = 31L;

    @Mock
    CartItemRepository cartItemRepository;
    @Mock
    AddressRepository addressRepository;
    @Mock
    ExchangeRateRepository exchangeRateRepository;
    @Mock
    CheckoutConfigRepository checkoutConfigRepository;
    @Mock
    TradingCatalogSnapshotPort catalogSnapshotPort;
    @Mock
    ShippingQuotePort shippingQuotePort;
    @Mock
    CouponDomainService couponDomainService;
    @Mock
    TradingDyeLotPort dyeLotPort;

    CheckoutQuoteService service;

    @BeforeEach
    void setUp() {
        service = new CheckoutQuoteService(cartItemRepository, addressRepository, exchangeRateRepository,
                checkoutConfigRepository, catalogSnapshotPort, shippingQuotePort, couponDomainService, dyeLotPort);
        lenient().when(dyeLotPort.hintProductIds(anyLong(), anyList())).thenReturn(List.of());
        Address address = new Address();
        address.setId(ADDRESS);
        address.setCustomerId(CUSTOMER);
        address.setCountry("US");
        lenient().when(addressRepository.findByIdAndCustomerId(ADDRESS, CUSTOMER)).thenReturn(address);
        CartItem item = new CartItem();
        item.setId(1L);
        item.setCustomerId(CUSTOMER);
        item.setProductId(PRODUCT);
        item.setSkuId(SKU);
        item.setQty(2);
        lenient().when(cartItemRepository.listByCustomerId(CUSTOMER)).thenReturn(List.of(item));
        lenient().when(catalogSnapshotPort.getProductBriefs(anyCollection(), anyString()))
                .thenReturn(Map.of(PRODUCT, product(2)));
        lenient().when(catalogSnapshotPort.getSkus(anyCollection()))
                .thenReturn(Map.of(SKU, new SkuBrief(SKU, PRODUCT, "AUR-IV-2", "Ivory", "2", 5, 0L)));
        lenient().when(shippingQuotePort.quoteOptions(anyString(), any())).thenReturn(List.of(
                new ShippingOptionQuote("FedEx International Priority", new BigDecimal("25.00"), "5-7 days"),
                new ShippingOptionQuote("UPS Worldwide Express", new BigDecimal("22.00"), "6-8 days"),
                new ShippingOptionQuote("DHL Express", new BigDecimal("28.00"), "4-6 days")));
        CheckoutConfig config = new CheckoutConfig();
        config.setGiftWrapFeeUsd(new BigDecimal("15.00"));
        config.setCustomRefundGraceHours(24);
        lenient().when(checkoutConfigRepository.getSingleton()).thenReturn(config);
    }

    private ProductBrief product(Integer status) {
        return new ProductBrief(PRODUCT, "aurelia-gown", "Aurelia Gown", new BigDecimal("100.00"),
                null, null, "https://img/aurelia.jpg", 30, false, true, status);
    }

    private CheckoutQuoteRequest request(String currency, String carrier, String coupon, boolean giftWrap,
                                         LocalDate weddingDate) {
        return new CheckoutQuoteRequest(ADDRESS, null, currency, carrier, coupon, giftWrap, weddingDate);
    }

    @Test
    @DisplayName("TC-TRD-010 [P1]: 未传 carrier → 最低价项 selected=true；shipping_fee = selected 项 fee")
    void defaultSelectedIsCheapest() {
        CheckoutQuoteResponse resp = service.quote(CUSTOMER, request("USD", null, null, false, null), "en");
        assertThat(resp.shippingOptions()).hasSize(3);
        ShippingOptionDto selected = resp.shippingOptions().stream()
                .filter(o -> Boolean.TRUE.equals(o.selected())).findFirst().orElseThrow();
        assertThat(selected.carrier()).isEqualTo("UPS Worldwide Express");
        assertThat(resp.shippingFee()).isEqualByComparingTo("22.00");
        // 恒等式：200(行) + 22(运费) = 222
        assertThat(resp.totalAmount()).isEqualByComparingTo("222.00");
    }

    @Test
    @DisplayName("TC-TRD-010 [P1]: 请求 carrier 命中 → 对应项 selected；disabled 承运商请求回退最低价")
    void requestedCarrierSelected() {
        CheckoutQuoteResponse resp = service.quote(CUSTOMER,
                request("USD", "DHL Express", null, false, null), "en");
        assertThat(resp.shippingFee()).isEqualByComparingTo("28.00");
        // disabled（报价缺席）承运商：仅返回两项时请求 FedEx 之外的 DHL → 回退最低价
        when(shippingQuotePort.quoteOptions(anyString(), any())).thenReturn(List.of(
                new ShippingOptionQuote("FedEx International Priority", new BigDecimal("25.00"), null),
                new ShippingOptionQuote("UPS Worldwide Express", new BigDecimal("22.00"), null)));
        CheckoutQuoteResponse fallback = service.quote(CUSTOMER,
                request("USD", "DHL Express", null, false, null), "en");
        assertThat(fallback.shippingOptions()).hasSize(2);
        assertThat(fallback.shippingFee()).isEqualByComparingTo("22.00");
    }

    @Test
    @DisplayName("TC-TRD-002/009 [P1]: 币种切换金额联动（fee/gift_wrap USD×rate HALF_UP；覆盖价优先）")
    void currencyConversion() {
        ExchangeRate eur = new ExchangeRate();
        eur.setCurrency("EUR");
        eur.setRate(new BigDecimal("0.920000"));
        when(exchangeRateRepository.findByCurrency("EUR")).thenReturn(eur);
        when(catalogSnapshotPort.getProductBriefs(anyCollection(), anyString())).thenReturn(Map.of(PRODUCT,
                new ProductBrief(PRODUCT, "aurelia-gown", "Aurelia Gown", new BigDecimal("100.00"),
                        null, Map.of("EUR", new BigDecimal("90.00")), null, 30, false, true, 2)));
        CheckoutQuoteResponse resp = service.quote(CUSTOMER, request("EUR", null, null, true, null), "en");
        // 行价覆盖价 90×2=180；运费 22×0.92=20.24；礼品包装 15×0.92=13.80
        assertThat(resp.subtotal()).isEqualByComparingTo("180.00");
        assertThat(resp.shippingFee()).isEqualByComparingTo("20.24");
        assertThat(resp.giftWrapFee()).isEqualByComparingTo("13.80");
        assertThat(resp.totalAmount()).isEqualByComparingTo("214.04");
        assertThat(resp.exchangeRate()).isEqualByComparingTo("0.92");
    }

    @Test
    @DisplayName("TC-TRD-065 [P1]: 无效券 200 + coupon_valid=false 不阻断报价（discount=0）")
    void invalidCouponNotBlocking() {
        when(couponDomainService.validate(eq("BRIDE10"), any(), anyString()))
                .thenReturn(new CouponQuote(false, null, null, false, 422701, null));
        CheckoutQuoteResponse resp = service.quote(CUSTOMER,
                request("USD", null, "BRIDE10", false, null), "en");
        assertThat(resp.couponValid()).isFalse();
        assertThat(resp.couponReasonCode()).isEqualTo(422701);
        assertThat(resp.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(resp.totalAmount()).isEqualByComparingTo("222.00");
    }

    @Test
    @DisplayName("STEP-TRD-06: 有效券减免换算订单币种并参与恒等式")
    void validCouponDiscount() {
        when(couponDomainService.validate(eq("BRIDE10"), any(), anyString()))
                .thenReturn(new CouponQuote(true, 5L, new BigDecimal("20.00"), false, null, null));
        CheckoutQuoteResponse resp = service.quote(CUSTOMER,
                request("USD", null, "BRIDE10", false, null), "en");
        assertThat(resp.couponValid()).isTrue();
        assertThat(resp.discountAmount()).isEqualByComparingTo("20.00");
        assertThat(resp.totalAmount()).isEqualByComparingTo("202.00");
    }

    @Test
    @DisplayName("TC-TRD-011 [P2]: today+max_lead_time > wedding_date → lead_time_warning=true；无婚期不告警")
    void leadTimeWarning() {
        CheckoutQuoteResponse warn = service.quote(CUSTOMER,
                request("USD", null, null, false, LocalDate.now().plusDays(10)), "en");
        assertThat(warn.leadTimeWarning()).isTrue();
        assertThat(warn.maxLeadTimeDays()).isEqualTo(30);
        CheckoutQuoteResponse ok = service.quote(CUSTOMER,
                request("USD", null, null, false, LocalDate.now().plusDays(60)), "en");
        assertThat(ok.leadTimeWarning()).isFalse();
        CheckoutQuoteResponse none = service.quote(CUSTOMER, request("USD", null, null, false, null), "en");
        assertThat(none.leadTimeWarning()).isFalse();
    }

    @Test
    @DisplayName("V-TRD-020: 空车 → 422601 details.reason=cart_empty")
    void emptyCartRejected() {
        when(cartItemRepository.listByCustomerId(CUSTOMER)).thenReturn(List.of());
        assertThatThrownBy(() -> service.quote(CUSTOMER, request("USD", null, null, false, null), "en"))
                .isInstanceOfSatisfying(TradingException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED);
                    assertThat(ex.getDetails()).containsEntry("reason", "cart_empty");
                });
    }

    @Test
    @DisplayName("V-TRD-015: 币种枚举外 → 422605 CURRENCY_NOT_SUPPORTED")
    void unsupportedCurrency() {
        assertThatThrownBy(() -> service.quote(CUSTOMER, request("JPY", null, null, false, null), "en"))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.CURRENCY_NOT_SUPPORTED));
    }

    @Test
    @DisplayName("strict 口径：下架行 → 422601 details.unavailable_product_ids（V-TRD-026）；quote 口径剔除")
    void draftLineHandling() {
        when(catalogSnapshotPort.getProductBriefs(anyCollection(), anyString()))
                .thenReturn(Map.of(PRODUCT, product(1)));
        assertThatThrownBy(() -> service.compute(CUSTOMER, ADDRESS, null, "USD", null, null, false, null,
                "en", true))
                .isInstanceOfSatisfying(TradingException.class, ex ->
                        assertThat(ex.getDetails()).containsKey("unavailable_product_ids"));
        // quote 口径：剔除后空车 → cart_empty
        assertThatThrownBy(() -> service.quote(CUSTOMER, request("USD", null, null, false, null), "en"))
                .isInstanceOfSatisfying(TradingException.class, ex ->
                        assertThat(ex.getDetails()).containsEntry("reason", "cart_empty"));
    }
}
