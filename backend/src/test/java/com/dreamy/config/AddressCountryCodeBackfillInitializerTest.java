package com.dreamy.config;

import com.dreamy.domain.address.entity.Address;
import com.dreamy.domain.address.repository.AddressRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 地址 country_code 回填（§6.1）：名称/别名解析、US 州规范化、无法解析留空、keyset 分批。 */
@ExtendWith(MockitoExtension.class)
class AddressCountryCodeBackfillInitializerTest {

    @Mock AddressRepository addressRepository;

    private static Address address(long id, String country, String state) {
        Address a = new Address();
        a.setId(id);
        a.setCountry(country);
        a.setState(state);
        return a;
    }

    @Test
    @DisplayName("回填：United States/TX → US/TX；Canada/Ontario → CA/ON；Germany → DE（无 region）；Wakanda 留空")
    void backfill() {
        when(addressRepository.listMissingCountryCodeAfterId(eq(0L), anyInt())).thenReturn(List.of(
                address(1L, "United States", "Texas"),
                address(2L, "Canada", "Ontario"),
                address(3L, "Germany", "Bavaria"),
                address(4L, "Wakanda", null),
                address(5L, "GB", null)));
        when(addressRepository.backfillCodes(anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        int[] result = new AddressCountryCodeBackfillInitializer(addressRepository).backfill();
        assertThat(result).containsExactly(4, 1);
        verify(addressRepository).backfillCodes(1L, "US", "TX");
        verify(addressRepository).backfillCodes(2L, "CA", "ON");
        verify(addressRepository).backfillCodes(3L, "DE", null);
        verify(addressRepository).backfillCodes(5L, "GB", null);
        verify(addressRepository, never()).backfillCodes(eq(4L), org.mockito.ArgumentMatchers.any(), isNull());
    }
}
