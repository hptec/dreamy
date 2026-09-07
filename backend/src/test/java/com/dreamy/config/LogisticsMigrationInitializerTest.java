package com.dreamy.config;

import com.dreamy.domain.carrier.entity.Carrier;
import com.dreamy.domain.carrier.repository.CarrierRepository;
import com.dreamy.domain.shippingrate.entity.ShippingOption;
import com.dreamy.domain.shippingrate.entity.ShippingRate;
import com.dreamy.domain.shippingrate.repository.ShippingOptionRepository;
import com.dreamy.domain.shippingrate.repository.ShippingRateRepository;
import com.dreamy.enums.ShippingServiceLevel;
import com.dreamy.infra.ShippingCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 物流迁移器（E/C）：carrier code 回填 + shipping_rate → shipping_option（STANDARD/EXPRESS，Europe 复制到 UK，兜底 ANY）；幂等。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogisticsMigrationInitializerTest {

    @Mock CarrierRepository carrierRepository;
    @Mock ShippingRateRepository rateRepository;
    @Mock ShippingOptionRepository optionRepository;
    @Mock ShippingCacheService cache;

    private static Carrier carrier(long id, String name, String code) {
        Carrier c = new Carrier();
        c.setId(id);
        c.setName(name);
        c.setCode(code);
        return c;
    }

    private static ShippingRate rate(String zone, String under, String over, String threshold) {
        ShippingRate r = new ShippingRate();
        r.setZone(zone);
        r.setFeeUnder(new BigDecimal(under));
        r.setFeeOver(new BigDecimal(over));
        r.setThreshold(threshold == null ? null : new BigDecimal(threshold));
        return r;
    }

    @Test
    @DisplayName("carrier code 回填：FedEx→FEDEX + 模板；未知名派生 + 去重；已有 code 跳过")
    void backfillCarrierCodes() {
        when(carrierRepository.listAll()).thenReturn(List.of(
                carrier(1L, "FedEx International Priority", null),
                carrier(2L, "UPS Worldwide Express", "UPS"),
                carrier(3L, "Local Courier", null),
                carrier(4L, "Local Courier", null)));
        when(carrierRepository.backfillCode(anyLong(), any(), any())).thenReturn(1);
        int updated = new LogisticsMigrationInitializer(carrierRepository, rateRepository, optionRepository, cache)
                .backfillCarrierCodes();
        assertThat(updated).isEqualTo(3);
        verify(carrierRepository).backfillCode(1L, "FEDEX", "https://www.fedex.com/fedextrack/?trknbr={tracking_no}");
        verify(carrierRepository).backfillCode(3L, "LOCAL_COURIER", null);
        verify(carrierRepository).backfillCode(4L, "LOCAL_COURIER2", null);
        verify(carrierRepository, never()).backfillCode(eq(2L), any(), any());
        assertThat(LogisticsMigrationInitializer.recognize("DHL Express")[0]).isEqualTo("DHL");
        assertThat(LogisticsMigrationInitializer.recognize("USPS Priority")[1]).contains("usps.com");
    }

    @Test
    @DisplayName("shipping_option 迁移：仅空表时执行；Europe 行复制到 UK；无后缀 → ANY；每 key 生成 STANDARD + EXPRESS(fee×1.6)")
    void migrateOptions() {
        when(optionRepository.count()).thenReturn(0L);
        when(carrierRepository.listAll()).thenReturn(List.of(
                carrier(1L, "FedEx International Priority", "FEDEX"),
                carrier(3L, "DHL Express", "DHL")));
        when(rateRepository.listAll()).thenReturn(List.of(
                rate("North America / FedEx International Priority", "8.00", "0.00", "200.00"),
                rate("Europe / DHL Express", "27.00", "0.00", "400.00"),
                rate("Rest of World", "38.00", "0.00", "500.00")));
        int inserted = new LogisticsMigrationInitializer(carrierRepository, rateRepository, optionRepository, cache)
                .migrateShippingOptions();
        // NA/FEDEX ×2 + EUROPE/DHL ×2 + UK/DHL ×2 + REST/ANY ×2
        assertThat(inserted).isEqualTo(8);
        ArgumentCaptor<ShippingOption> captor = ArgumentCaptor.forClass(ShippingOption.class);
        verify(optionRepository, times(8)).insert(captor.capture());
        List<ShippingOption> all = captor.getAllValues();
        assertThat(all).anyMatch(o -> o.getZone().equals("NORTH_AMERICA") && o.getCarrierCode().equals("FEDEX")
                && o.getServiceLevel() == ShippingServiceLevel.STANDARD && o.getFeeUnder().compareTo(new BigDecimal("8.00")) == 0
                && o.getTransitDaysMin() == 3 && o.getTransitDaysMax() == 6);
        assertThat(all).anyMatch(o -> o.getZone().equals("NORTH_AMERICA") && o.getCarrierCode().equals("FEDEX")
                && o.getServiceLevel() == ShippingServiceLevel.EXPRESS && o.getFeeUnder().compareTo(new BigDecimal("12.80")) == 0
                && o.getThreshold() == null);
        assertThat(all).anyMatch(o -> o.getZone().equals("UK") && o.getCarrierCode().equals("DHL"));
        assertThat(all).anyMatch(o -> o.getZone().equals("REST") && o.getCarrierCode().equals("ANY"));
        // 幂等：非空表不再迁移
        when(optionRepository.count()).thenReturn(8L);
        assertThat(new LogisticsMigrationInitializer(carrierRepository, rateRepository, optionRepository, cache)
                .migrateShippingOptions()).isZero();
    }
}
