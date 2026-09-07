package com.dreamy.domain.shippingrate.service;

import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.domain.carrier.entity.Carrier;
import com.dreamy.domain.carrier.repository.CarrierRepository;
import com.dreamy.domain.shippingrate.entity.ShippingOption;
import com.dreamy.domain.shippingrate.repository.ShippingOptionRepository;
import com.dreamy.dto.TradingDtos.ShippingOptionAdminDto;
import com.dreamy.dto.TradingDtos.ShippingOptionUpsert;
import com.dreamy.enums.ShippingServiceLevel;
import com.dreamy.error.ShippingErrorCode;
import com.dreamy.error.ShippingException;
import com.dreamy.infra.ShippingAuditRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 运费选项后台 CRUD（E）：zone 旧名兼容规范化、carrier_code 校验、uk 409904、字段 422901、enabled 幂等、失效任务。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShippingOptionAdminServiceTest {

    @Mock ShippingOptionRepository optionRepository;
    @Mock CarrierRepository carrierRepository;
    @Mock ShippingAuditRecorder audit;
    @Mock CacheInvalidationTaskService cacheTasks;

    ShippingOptionAdminService service;

    @BeforeEach
    void setUp() {
        service = new ShippingOptionAdminService(optionRepository, carrierRepository, audit, new ObjectMapper(), cacheTasks);
        Carrier fedex = new Carrier();
        fedex.setId(1L);
        fedex.setName("FedEx International Priority");
        fedex.setCode("FEDEX");
        when(carrierRepository.findByCode("FEDEX")).thenReturn(fedex);
        when(carrierRepository.listAll()).thenReturn(List.of(fedex));
        doAnswer(inv -> {
            ((ShippingOption) inv.getArgument(0)).setId(9L);
            return null;
        }).when(optionRepository).insert(any());
    }

    @Test
    @DisplayName("create：旧 zone 名 'North America' → NORTH_AMERICA；code 小写 → 大写；缺省 STANDARD/enabled/天数；carrier_name 派生")
    void createNormalizes() {
        ShippingOptionAdminDto dto = service.create(new ShippingOptionUpsert("North America", "fedex", null,
                new BigDecimal("8.00"), new BigDecimal("0.00"), new BigDecimal("200.00"), null, null, null));
        assertThat(dto.zone()).isEqualTo("NORTH_AMERICA");
        assertThat(dto.carrierCode()).isEqualTo("FEDEX");
        assertThat(dto.carrierName()).isEqualTo("FedEx International Priority");
        assertThat(dto.serviceLevel()).isEqualTo(1);
        assertThat(dto.enabled()).isTrue();
        assertThat(dto.transitDaysMin()).isEqualTo(5);
        assertThat(dto.transitDaysMax()).isEqualTo(10);
        verify(cacheTasks).enqueue(any(), eq("shipping_option.create"), eq("shipping_option"), any(), any(), any(), any(), any(), any());
        // ANY 通配允许
        ShippingOptionAdminDto any = service.create(new ShippingOptionUpsert("REST", "any", 2, new BigDecimal("50"),
                null, null, 8, 14, false));
        assertThat(any.carrierCode()).isEqualTo("ANY");
        assertThat(any.carrierName()).isEqualTo("Any carrier");
        assertThat(any.enabled()).isFalse();
    }

    @Test
    @DisplayName("422901：未知 zone / 未知 carrier_code / 等级枚举外 / 负费用 / 天数倒置")
    void validation() {
        assertField(() -> service.create(new ShippingOptionUpsert("Mars", "FEDEX", 1, null, null, null, 1, 2, true)), "zone");
        assertField(() -> service.create(new ShippingOptionUpsert("EUROPE", "NOPE", 1, null, null, null, 1, 2, true)), "carrier_code");
        assertField(() -> service.create(new ShippingOptionUpsert("EUROPE", "FEDEX", 9, null, null, null, 1, 2, true)), "service_level");
        assertField(() -> service.create(new ShippingOptionUpsert("EUROPE", "FEDEX", 1, new BigDecimal("-1"), null, null, 1, 2, true)), "fee_under");
        assertField(() -> service.create(new ShippingOptionUpsert("EUROPE", "FEDEX", 1, null, null, null, 5, 2, true)), "transit_days_max");
        verify(optionRepository, never()).insert(any());
    }

    @Test
    @DisplayName("uk 409904：同 (zone, carrier_code, level) 已存在；update 排除自身；404903 不存在")
    void duplicateKey() {
        when(optionRepository.existsByKey("EUROPE", "FEDEX", ShippingServiceLevel.STANDARD, null)).thenReturn(true);
        assertThatThrownBy(() -> service.create(new ShippingOptionUpsert("EUROPE", "FEDEX", 1, null, null, null, 1, 2, true)))
                .isInstanceOfSatisfying(ShippingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShippingErrorCode.SHIPPING_OPTION_EXISTS));
        assertThatThrownBy(() -> service.update("77", new ShippingOptionUpsert("EUROPE", "FEDEX", 1, null, null, null, 1, 2, true)))
                .isInstanceOfSatisfying(ShippingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShippingErrorCode.SHIPPING_OPTION_NOT_FOUND));
        ShippingOption existing = ShippingQuoteServiceTest.option(5L, "EUROPE", "FEDEX", 1, "28.00", "0.00", "400.00", 5, 8);
        when(optionRepository.findById(5L)).thenReturn(existing);
        when(optionRepository.existsByKey("EUROPE", "FEDEX", ShippingServiceLevel.STANDARD, 5L)).thenReturn(false);
        ShippingOptionAdminDto updated = service.update("5", new ShippingOptionUpsert("EUROPE", "FEDEX", 1,
                new BigDecimal("30.00"), new BigDecimal("0.00"), new BigDecimal("400.00"), 4, 7, true));
        assertThat(updated.feeUnder()).isEqualByComparingTo("30.00");
        verify(optionRepository).updateAll(existing);
    }

    @Test
    @DisplayName("enabled PATCH 幂等；delete 404903")
    void enabledAndDelete() {
        ShippingOption existing = ShippingQuoteServiceTest.option(5L, "EUROPE", "FEDEX", 1, "28.00", "0.00", "400.00", 5, 8);
        when(optionRepository.findById(5L)).thenReturn(existing);
        service.setEnabled("5", true);
        verify(optionRepository, never()).updateEnabled(eq(5L), org.mockito.ArgumentMatchers.anyBoolean());
        service.setEnabled("5", false);
        verify(optionRepository).updateEnabled(5L, false);
        when(optionRepository.deleteById(5L)).thenReturn(0);
        assertThatThrownBy(() -> service.delete("5"))
                .isInstanceOfSatisfying(ShippingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShippingErrorCode.SHIPPING_OPTION_NOT_FOUND));
    }

    private static void assertField(Runnable action, String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ShippingException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ShippingErrorCode.FIELD_VALIDATION_FAILED);
            assertThat(ex.getDetails()).containsEntry("field", field);
        });
    }
}
