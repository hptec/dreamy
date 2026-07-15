package com.dreamy.domain.carrier.service;

import com.dreamy.domain.carrier.entity.Carrier;
import com.dreamy.domain.carrier.repository.CarrierRepository;
import com.dreamy.enums.CarrierStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarrierTxServiceTest {

    @Mock CarrierRepository carrierRepository;
    @Mock ShippingAuditRecorder audit;
    @Mock com.dreamy.domain.cache.service.CacheInvalidationTaskService cacheTasks;

    private CarrierTxService service;

    @BeforeEach
    void setUp() {
        service = new CarrierTxService(carrierRepository, audit, new ObjectMapper(), cacheTasks);
    }

    @Test
    @DisplayName("E-SHP-04：禁用承运方物理删除并记录审计、提交后失效缓存")
    void deleteDisabledCarrierUsesPhysicalDelete() {
        Carrier carrier = carrier(7L, CarrierStatus.DISABLED);
        when(carrierRepository.findById(7L)).thenReturn(carrier);

        service.delete(7L);

        verify(carrierRepository, never()).countEnabled();
        verify(carrierRepository).deleteById(7L);
        verify(audit).record(eq("删除承运方"), eq("DHL"), anyString());
        verify(cacheTasks).enqueue(anyString(), anyString(), anyString(),
                nullable(Object.class), nullable(String.class), anyList(), nullable(java.time.LocalDateTime.class),
                anyMap(), nullable(String.class));
    }

    @Test
    @DisplayName("E-SHP-04：最后一个启用承运方不可删除")
    void deleteLastEnabledCarrierKeepsGuard() {
        Carrier carrier = carrier(7L, CarrierStatus.ENABLED);
        when(carrierRepository.findById(7L)).thenReturn(carrier);
        when(carrierRepository.countEnabled()).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(ShippingException.class)
                .satisfies(ex -> assertThat(((ShippingException) ex).getErrorCode())
                        .isEqualTo(ShippingErrorCode.LAST_ENABLED_CARRIER));

        verify(carrierRepository, never()).deleteById(any());
        verify(audit, never()).record(anyString(), anyString(), any());
        verifyNoInteractions(cacheTasks);
    }

    private Carrier carrier(Long id, CarrierStatus status) {
        Carrier carrier = new Carrier();
        carrier.setId(id);
        carrier.setName("DHL");
        carrier.setStatus(status);
        return carrier;
    }
}
