package com.dreamy.domain.address.service;

import com.dreamy.domain.address.entity.Address;
import com.dreamy.domain.address.repository.AddressRepository;
import com.dreamy.dto.TradingDtos.AddressDto;
import com.dreamy.dto.TradingDtos.AddressUpsert;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.testsupport.TradingImmediateTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AddressService 单测（G：country_code 必须在目录内；country 文本解析兜底；US/CA/AU region 规范化；DTO 新字段）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddressServiceTest {

    @Mock AddressRepository addressRepository;
    AddressService service;

    @BeforeEach
    void setUp() {
        service = new AddressService(addressRepository, new TradingImmediateTxRunner());
        when(addressRepository.countByCustomerId(7L)).thenReturn(0L);
    }

    private static AddressUpsert upsert(String state, String country, String countryCode, String regionCode) {
        return new AddressUpsert("Emma", null, "1 Main St", "Austin", state, "73301", country, true, countryCode, regionCode);
    }

    @Test
    @DisplayName("country_code 显式 + region_code 缩写 → 规范化落库；DTO 透出 country_code/region_code")
    void explicitCodes() {
        AddressDto dto = service.create(7L, upsert("Texas", "United States", "us", "tx"));
        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).insert(captor.capture());
        assertThat(captor.getValue().getCountryCode()).isEqualTo("US");
        assertThat(captor.getValue().getRegionCode()).isEqualTo("TX");
        assertThat(captor.getValue().getState()).isEqualTo("Texas");
        assertThat(dto.countryCode()).isEqualTo("US");
        assertThat(dto.regionCode()).isEqualTo("TX");
        assertThat(dto.isDefault()).isTrue();
    }

    @Test
    @DisplayName("无 country_code：country 名/别名解析（'USA' → US；state 'Washington DC' → DC）；仅 code 时回填 country 名")
    void resolveFromText() {
        service.create(7L, upsert("Washington DC", "USA", null, null));
        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).insert(captor.capture());
        assertThat(captor.getValue().getCountryCode()).isEqualTo("US");
        assertThat(captor.getValue().getRegionCode()).isEqualTo("DC");
        service.create(7L, upsert(null, null, "GB", null));
        verify(addressRepository, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getValue().getCountry()).isEqualTo("United Kingdom");
        assertThat(captor.getValue().getRegionCode()).isNull();
    }

    @Test
    @DisplayName("422601：目录外 country_code；无法解析的 country 文本；US 未知州；非字典国家 region 留空不报错")
    void validation() {
        assertField(() -> service.create(7L, upsert("TX", "United States", "ZZ", null)), "country_code");
        assertField(() -> service.create(7L, upsert("TX", "Wakanda", null, null)), "country_code");
        assertField(() -> service.create(7L, upsert("Nowhere", "United States", null, null)), "region_code");
        assertField(() -> service.create(7L, upsert("TX", "US", null, "XX")), "region_code");
        // US 未填州 → region_code 留空（前台后续补选）
        service.create(7L, upsert(null, "US", null, null));
        // FR：state 自由文本不校验字典
        service.create(7L, upsert("Île-de-France", "France", null, null));
        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getValue().getCountryCode()).isEqualTo("FR");
        assertThat(captor.getValue().getRegionCode()).isNull();
    }

    private static void assertField(Runnable action, String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(TradingException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED);
            assertThat(((Map<?, ?>) ex.getDetails().get("fields")).containsKey(field)).as(field).isTrue();
        });
    }
}
