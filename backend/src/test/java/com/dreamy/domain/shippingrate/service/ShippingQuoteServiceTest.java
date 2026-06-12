package com.dreamy.domain.shippingrate.service;

import com.dreamy.port.ShippingOptionQuote;
import com.dreamy.domain.carrier.entity.Carrier;
import com.dreamy.domain.carrier.repository.CarrierRepository;
import com.dreamy.enums.CarrierStatus;
import com.dreamy.domain.shippingrate.entity.ShippingRate;
import com.dreamy.domain.shippingrate.repository.ShippingRateRepository;
import com.dreamy.infra.ShippingCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * SVC-SHP-01 报价算法单测（TC-SHP-003~008/010；§10.3 全分支 + DEC-SHP-3/5 + MAP-SHP-003 scale）。
 * 缓存 mock 直通 loader（缓存命中/失效链归 IT 层 TC-SHP-016）。
 * 数据工厂基线 = shipping-data-detail §8.2 种子（3 enabled 承运商 + 10 规则行）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShippingQuoteServiceTest {

    private static final String FEDEX = "FedEx International Priority";
    private static final String UPS = "UPS Worldwide Express";
    private static final String DHL = "DHL Express";

    @Mock
    private CarrierRepository carrierRepository;
    @Mock
    private ShippingRateRepository rateRepository;
    @Mock
    private ShippingCacheService cache;

    private ShippingQuoteService service;

    private List<Carrier> carriers;
    private List<ShippingRate> rates;

    @BeforeEach
    void setUp() {
        service = new ShippingQuoteService(carrierRepository, rateRepository, cache);
        carriers = seedEnabledCarriers();
        rates = seedRates();
        // 缓存直通 loader
        lenient().when(cache.getCarriers(any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        lenient().when(cache.getRates(any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        lenient().when(carrierRepository.listEnabled()).thenAnswer(inv -> carriers);
        lenient().when(rateRepository.listAll()).thenAnswer(inv -> rates);
    }

    @Test
    @DisplayName("TC-SHP-005 多承运商组装：US/100 → 3 项差异化价格，顺序 id ASC，lead_time 透传，scale=2")
    void quoteThreeCarriersForUs() {
        List<ShippingOptionQuote> options = service.quoteOptions("US", new BigDecimal("100"));
        assertThat(options).hasSize(3);
        assertThat(options.get(0).carrier()).isEqualTo(FEDEX);
        assertThat(options.get(0).feeUsd()).isEqualByComparingTo("8.00");
        assertThat(options.get(0).feeUsd().scale()).isEqualTo(2);
        assertThat(options.get(0).leadTime()).isEqualTo("3-5 天");
        assertThat(options.get(1).carrier()).isEqualTo(UPS);
        assertThat(options.get(1).feeUsd()).isEqualByComparingTo("10.00");
        assertThat(options.get(2).carrier()).isEqualTo(DHL);
        assertThat(options.get(2).feeUsd()).isEqualByComparingTo("9.00");
    }

    @Test
    @DisplayName("TC-SHP-003 阈值边界：199.99→fee_under；200.00→fee_over（等于取满额）；200.01→fee_over")
    void thresholdBoundary() {
        assertThat(feeFor(FEDEX, "US", "199.99")).isEqualByComparingTo("8.00");
        assertThat(feeFor(FEDEX, "US", "200.00")).isEqualByComparingTo("0.00");
        assertThat(feeFor(FEDEX, "US", "200.01")).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("TC-SHP-004 NULL 语义（DEC-SHP-3）：threshold null 恒 fee_under；fee null 计 0.00")
    void nullSemantics() {
        rates = new ArrayList<>(List.of(
                rate(1L, "North America / " + FEDEX, "8.00", null, null),       // threshold null
                rate(2L, "North America / " + UPS, null, "0.00", "250.00"),     // fee_under null
                rate(3L, "North America / " + DHL, "9.00", null, "220.00")));   // fee_over null
        // threshold=null → 任意 subtotal 恒 fee_under
        assertThat(feeFor(FEDEX, "US", "999999")).isEqualByComparingTo("8.00");
        // fee_under=null → 计 0.00（subtotal < threshold）
        assertThat(feeFor(UPS, "US", "100")).isEqualByComparingTo("0.00");
        // fee_over=null 且 subtotal>=threshold → 计 0.00
        assertThat(feeFor(DHL, "US", "220.00")).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("TC-SHP-006 兜底回退：删 Europe/DHL 精确行 → DHL 走 Rest of World（③）；新增 Europe 无后缀行 → DHL 取之（②）")
    void fallbackChain() {
        // 删除 'Europe / DHL Express' 行：Europe 无无后缀行 → DHL 回退 Rest of World 38.00
        rates = new ArrayList<>(rates);
        rates.removeIf(r -> r.getZone().equals("Europe / " + DHL));
        assertThat(feeFor(DHL, "FR", "100")).isEqualByComparingTo("38.00");
        // 新增 'Europe' 无后缀兜底行 25.00 → DHL 取 25.00，FedEx/UPS 仍取各自精确行
        rates.add(rate(99L, "Europe", "25.00", "0.00", "400.00"));
        assertThat(feeFor(DHL, "FR", "100")).isEqualByComparingTo("25.00");
        assertThat(feeFor(FEDEX, "FR", "100")).isEqualByComparingTo("28.00");
        assertThat(feeFor(UPS, "FR", "100")).isEqualByComparingTo("26.00");
    }

    @Test
    @DisplayName("TC-SHP-007 全局兜底：JP subtotal=600 → 三项均 fee_over=0.00；subtotal=100 → 均 38.00")
    void restOfWorldFallback() {
        List<ShippingOptionQuote> freeShipping = service.quoteOptions("JP", new BigDecimal("600"));
        assertThat(freeShipping).hasSize(3);
        assertThat(freeShipping).allSatisfy(o -> assertThat(o.feeUsd()).isEqualByComparingTo("0.00"));
        List<ShippingOptionQuote> base = service.quoteOptions("JP", new BigDecimal("100"));
        assertThat(base).hasSize(3);
        assertThat(base).allSatisfy(o -> assertThat(o.feeUsd()).isEqualByComparingTo("38.00"));
    }

    @Test
    @DisplayName("TC-SHP-008 无报价跳过（④）：清空规则行 → 空列表不抛错；disabled 承运商不出项")
    void emptyRatesAndDisabledCarrierSkipped() {
        rates = List.of();
        assertThat(service.quoteOptions("US", new BigDecimal("100"))).isEmpty();
        // 仅 disabled 承运商专属行存在 → enabled 列表不含 USPS，不为其出项
        rates = List.of(rate(1L, "North America / USPS Priority", "5.00", "0.00", "100.00"));
        assertThat(service.quoteOptions("US", new BigDecimal("100"))).isEmpty();
    }

    @Test
    @DisplayName("TC-SHP-010 承运商改名孤行（DEC-SHP-2）：FedEx 改名后回退 Rest of World，UPS/DHL 不受影响")
    void renamedCarrierOrphanLine() {
        carriers = List.of(
                carrier(1L, "FedEx Intl", "3-5 天"),
                carrier(2L, UPS, "4-6 天"),
                carrier(3L, DHL, "3-6 天"));
        List<ShippingOptionQuote> options = service.quoteOptions("US", new BigDecimal("100"));
        assertThat(options).hasSize(3);
        // 改名后精确行不匹配 → 无 North America 无后缀行 → Rest of World 38.00
        assertThat(options.get(0).carrier()).isEqualTo("FedEx Intl");
        assertThat(options.get(0).feeUsd()).isEqualByComparingTo("38.00");
        assertThat(options.get(1).feeUsd()).isEqualByComparingTo("10.00");
        assertThat(options.get(2).feeUsd()).isEqualByComparingTo("9.00");
    }

    @Test
    @DisplayName("DEC-SHP-1 报价匹配忽略大小写/空白：zone 行大小写变体仍命中")
    void matchIgnoresCaseAndWhitespace() {
        rates = List.of(rate(1L, "  north  america / fedex international priority ", "8.00", "0.00", "200.00"));
        assertThat(feeFor(FEDEX, "US", "100")).isEqualByComparingTo("8.00");
    }

    // ===== 工厂（shipping-test-detail 数据工厂基线）=====

    private BigDecimal feeFor(String carrierName, String country, String subtotal) {
        return service.quoteOptions(country, new BigDecimal(subtotal)).stream()
                .filter(o -> o.carrier().equals(carrierName))
                .findFirst().orElseThrow()
                .feeUsd();
    }

    private List<Carrier> seedEnabledCarriers() {
        return List.of(
                carrier(1L, FEDEX, "3-5 天"),
                carrier(2L, UPS, "4-6 天"),
                carrier(3L, DHL, "3-6 天"));
    }

    private List<ShippingRate> seedRates() {
        return new ArrayList<>(List.of(
                rate(1L, "North America / " + FEDEX, "8.00", "0.00", "200.00"),
                rate(2L, "North America / " + UPS, "10.00", "0.00", "250.00"),
                rate(3L, "North America / " + DHL, "9.00", "0.00", "220.00"),
                rate(4L, "Europe / " + FEDEX, "28.00", "0.00", "400.00"),
                rate(5L, "Europe / " + UPS, "26.00", "0.00", "380.00"),
                rate(6L, "Europe / " + DHL, "27.00", "0.00", "400.00"),
                rate(7L, "Oceania / " + FEDEX, "32.00", "0.00", "400.00"),
                rate(8L, "Oceania / " + UPS, "34.00", "0.00", "420.00"),
                rate(9L, "Oceania / " + DHL, "33.00", "0.00", "400.00"),
                rate(10L, "Rest of World", "38.00", "0.00", "500.00")));
    }

    private Carrier carrier(Long id, String name, String leadTime) {
        Carrier carrier = new Carrier();
        carrier.setId(id);
        carrier.setName(name);
        carrier.setLeadTime(leadTime);
        carrier.setStatus(CarrierStatus.ENABLED);
        return carrier;
    }

    private ShippingRate rate(Long id, String zone, String feeUnder, String feeOver, String threshold) {
        ShippingRate rate = new ShippingRate();
        rate.setId(id);
        rate.setZone(zone);
        rate.setFeeUnder(feeUnder == null ? null : new BigDecimal(feeUnder));
        rate.setFeeOver(feeOver == null ? null : new BigDecimal(feeOver));
        rate.setThreshold(threshold == null ? null : new BigDecimal(threshold));
        return rate;
    }
}
