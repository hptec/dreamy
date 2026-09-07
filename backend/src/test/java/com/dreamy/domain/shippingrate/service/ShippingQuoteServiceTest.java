package com.dreamy.domain.shippingrate.service;

import com.dreamy.domain.carrier.entity.Carrier;
import com.dreamy.domain.carrier.repository.CarrierRepository;
import com.dreamy.domain.shippingrate.entity.ShippingOption;
import com.dreamy.domain.shippingrate.repository.ShippingOptionRepository;
import com.dreamy.enums.CarrierStatus;
import com.dreamy.enums.ShippingServiceLevel;
import com.dreamy.infra.ShippingCacheService;
import com.dreamy.port.ShippingOptionQuote;
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
 * SVC-SHP-01 报价算法单测（order-flow-complete E：改读 shipping_option，zone × carrier × level）。
 * 缓存 mock 直通 loader。数据工厂 = 迁移器产物基线（3 enabled 承运商 + 分区 STANDARD/EXPRESS 选项 + REST/ANY 兜底）。
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
    private ShippingOptionRepository optionRepository;
    @Mock
    private ShippingCacheService cache;

    private ShippingQuoteService service;

    private List<Carrier> carriers;
    private List<ShippingOption> options;

    @BeforeEach
    void setUp() {
        service = new ShippingQuoteService(carrierRepository, optionRepository, cache);
        carriers = seedEnabledCarriers();
        options = seedOptions();
        lenient().when(cache.getCarriers(any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        lenient().when(cache.getOptions(any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        lenient().when(carrierRepository.listEnabled()).thenAnswer(inv -> carriers);
        lenient().when(optionRepository.listEnabled()).thenAnswer(inv -> options);
    }

    @Test
    @DisplayName("US/100 → 3 承运商 × STANDARD/EXPRESS = 6 项；顺序 承运商 id ASC × 等级；carrier_code/transit 透传；scale=2")
    void quoteCarriersByLevelForUs() {
        List<ShippingOptionQuote> quotes = service.quoteOptions("US", new BigDecimal("100"));
        assertThat(quotes).hasSize(6);
        assertThat(quotes.get(0).carrier()).isEqualTo(FEDEX);
        assertThat(quotes.get(0).carrierCode()).isEqualTo("FEDEX");
        assertThat(quotes.get(0).serviceLevel()).isEqualTo(1);
        assertThat(quotes.get(0).feeUsd()).isEqualByComparingTo("8.00");
        assertThat(quotes.get(0).feeUsd().scale()).isEqualTo(2);
        assertThat(quotes.get(0).transitDaysMin()).isEqualTo(3);
        assertThat(quotes.get(0).transitDaysMax()).isEqualTo(6);
        assertThat(quotes.get(0).leadTime()).isEqualTo("3-5 天");
        assertThat(quotes.get(1).carrier()).isEqualTo(FEDEX);
        assertThat(quotes.get(1).serviceLevel()).isEqualTo(2);
        assertThat(quotes.get(1).feeUsd()).isEqualByComparingTo("12.80");
        assertThat(quotes.get(2).carrier()).isEqualTo(UPS);
        assertThat(quotes.get(2).feeUsd()).isEqualByComparingTo("10.00");
        assertThat(quotes.get(4).carrier()).isEqualTo(DHL);
        assertThat(quotes.get(4).feeUsd()).isEqualByComparingTo("9.00");
    }

    @Test
    @DisplayName("阈值边界：199.99→fee_under；200.00→fee_over（等于取满额）；200.01→fee_over")
    void thresholdBoundary() {
        assertThat(feeFor(FEDEX, 1, "US", "199.99")).isEqualByComparingTo("8.00");
        assertThat(feeFor(FEDEX, 1, "US", "200.00")).isEqualByComparingTo("0.00");
        assertThat(feeFor(FEDEX, 1, "US", "200.01")).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("NULL 语义（DEC-SHP-3）：threshold null 恒 fee_under；fee null 计 0.00")
    void nullSemantics() {
        options = new ArrayList<>(List.of(
                option(1L, "NORTH_AMERICA", "FEDEX", 1, "8.00", null, null, 3, 6),
                option(2L, "NORTH_AMERICA", "UPS", 1, null, "0.00", "250.00", 3, 6),
                option(3L, "NORTH_AMERICA", "DHL", 1, "9.00", null, "220.00", 3, 6)));
        assertThat(feeFor(FEDEX, 1, "US", "999999")).isEqualByComparingTo("8.00");
        assertThat(feeFor(UPS, 1, "US", "100")).isEqualByComparingTo("0.00");
        assertThat(feeFor(DHL, 1, "US", "300")).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("匹配优先级：(zone,code) → (zone,ANY) → (REST,code) → (REST,ANY) → 跳过")
    void matchPriority() {
        // JP → ASIA 无精确行 → REST/ANY 兜底 38.00（三承运商同价）
        List<ShippingOptionQuote> jp = service.quoteOptions("JP", new BigDecimal("100"));
        assertThat(jp).hasSize(3);
        assertThat(jp).allMatch(q -> q.feeUsd().compareTo(new BigDecimal("38.00")) == 0);
        assertThat(jp).allMatch(q -> q.serviceLevel() == 1);
        // 无任何兜底 → 空列表（不抛错）
        options = new ArrayList<>(List.of(option(1L, "NORTH_AMERICA", "FEDEX", 1, "8.00", "0.00", "200.00", 3, 6)));
        assertThat(service.quoteOptions("JP", new BigDecimal("100"))).isEmpty();
        // disabled 选项不参与
        options = new ArrayList<>(List.of(disabled(option(1L, "NORTH_AMERICA", "FEDEX", 1, "8.00", "0.00", "200.00", 3, 6))));
        assertThat(service.quoteOptions("US", new BigDecimal("100"))).isEmpty();
    }

    @Test
    @DisplayName("国家名/旧 zone 兼容：'United Kingdom' → UK zone；GB 有 UK 行时不再落 EUROPE")
    void ukZoneSeparatedFromEurope() {
        List<ShippingOptionQuote> gb = service.quoteOptions("United Kingdom", new BigDecimal("100"));
        assertThat(gb).isNotEmpty();
        assertThat(gb.get(0).feeUsd()).isEqualByComparingTo("28.00");
        assertThat(feeFor(FEDEX, 1, "DE", "100")).isEqualByComparingTo("28.00");
    }

    @Test
    @DisplayName("zoneSupported：zone 有启用行或 REST 兜底 → true；无 → false")
    void zoneSupported() {
        assertThat(service.zoneSupported("ASIA")).isTrue();
        options = new ArrayList<>(List.of(option(1L, "NORTH_AMERICA", "FEDEX", 1, "8.00", "0.00", "200.00", 3, 6)));
        assertThat(service.zoneSupported("ASIA")).isFalse();
        assertThat(service.zoneSupported("NORTH_AMERICA")).isTrue();
    }

    // ===== 工厂 =====

    private BigDecimal feeFor(String carrierName, int level, String country, String subtotal) {
        return service.quoteOptions(country, new BigDecimal(subtotal)).stream()
                .filter(o -> o.carrier().equals(carrierName) && o.serviceLevel() == level)
                .findFirst().orElseThrow()
                .feeUsd();
    }

    private List<Carrier> seedEnabledCarriers() {
        return List.of(
                carrier(1L, FEDEX, "FEDEX", "3-5 天"),
                carrier(2L, UPS, "UPS", "4-6 天"),
                carrier(3L, DHL, "DHL", "3-6 天"));
    }

    private List<ShippingOption> seedOptions() {
        return new ArrayList<>(List.of(
                option(1L, "NORTH_AMERICA", "FEDEX", 1, "8.00", "0.00", "200.00", 3, 6),
                option(2L, "NORTH_AMERICA", "FEDEX", 2, "12.80", "12.80", null, 1, 3),
                option(3L, "NORTH_AMERICA", "UPS", 1, "10.00", "0.00", "250.00", 3, 6),
                option(4L, "NORTH_AMERICA", "UPS", 2, "16.00", "16.00", null, 1, 3),
                option(5L, "NORTH_AMERICA", "DHL", 1, "9.00", "0.00", "220.00", 3, 6),
                option(6L, "NORTH_AMERICA", "DHL", 2, "14.40", "14.40", null, 1, 3),
                option(7L, "EUROPE", "FEDEX", 1, "28.00", "0.00", "400.00", 5, 8),
                option(8L, "UK", "FEDEX", 1, "28.00", "0.00", "400.00", 5, 8),
                option(9L, "REST", "ANY", 1, "38.00", "0.00", "500.00", 8, 14)));
    }

    private Carrier carrier(Long id, String name, String code, String leadTime) {
        Carrier carrier = new Carrier();
        carrier.setId(id);
        carrier.setName(name);
        carrier.setCode(code);
        carrier.setLeadTime(leadTime);
        carrier.setStatus(CarrierStatus.ENABLED);
        return carrier;
    }

    static ShippingOption option(Long id, String zone, String code, int level, String feeUnder, String feeOver,
                                 String threshold, int min, int max) {
        ShippingOption option = new ShippingOption();
        option.setId(id);
        option.setZone(zone);
        option.setCarrierCode(code);
        option.setServiceLevel(ShippingServiceLevel.of(level));
        option.setFeeUnder(feeUnder == null ? null : new BigDecimal(feeUnder));
        option.setFeeOver(feeOver == null ? null : new BigDecimal(feeOver));
        option.setThreshold(threshold == null ? null : new BigDecimal(threshold));
        option.setTransitDaysMin(min);
        option.setTransitDaysMax(max);
        option.setEnabled(true);
        return option;
    }

    private static ShippingOption disabled(ShippingOption option) {
        option.setEnabled(false);
        return option;
    }
}
