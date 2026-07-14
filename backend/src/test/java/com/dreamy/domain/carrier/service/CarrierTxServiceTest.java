package com.dreamy.domain.carrier.service;

import com.dreamy.domain.carrier.entity.Carrier;
import com.dreamy.domain.carrier.repository.CarrierRepository;
import com.dreamy.enums.CarrierStatus;
import com.dreamy.error.ShippingErrorCode;
import com.dreamy.error.ShippingException;
import com.dreamy.infra.ShippingAfterCommitRunner;
import com.dreamy.infra.ShippingAuditRecorder;
import com.dreamy.infra.ShippingCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarrierTxServiceTest {

    @Mock CarrierRepository carrierRepository;
    @Mock ShippingAuditRecorder audit;
    @Mock ShippingAfterCommitRunner afterCommit;
    @Mock ShippingCacheService cache;

    private CarrierTxService service;

    @BeforeEach
    void setUp() {
        service = new CarrierTxService(carrierRepository, audit, afterCommit, cache, new ObjectMapper());
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
        verify(afterCommit).run(any(Runnable.class));
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
        verify(afterCommit, never()).run(any());
    }

    private Carrier carrier(Long id, CarrierStatus status) {
        Carrier carrier = new Carrier();
        carrier.setId(id);
        carrier.setName("DHL");
        carrier.setStatus(status);
        return carrier;
    }
}
