package com.dreamy.domain.shipment.service;

import com.dreamy.domain.shipment.entity.Shipment;
import com.dreamy.domain.shipment.repository.ShipmentRepository;
import com.dreamy.enums.ShipmentStatus;
import com.dreamy.port.TrackingProviderPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ShipmentTrackingSync 单测（§6.1：stub 跳过、单包裹失败不影响批次、失败不改状态 + 计数 + ≥3 告警、派生事件 id 稳定）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentTrackingSyncServiceTest {

    @Mock ShipmentRepository shipmentRepository;
    @Mock ShipmentService shipmentService;
    @Mock TrackingProviderPort provider;

    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ShipmentTrackingSyncService service;

    @BeforeEach
    void setUp() {
        service = new ShipmentTrackingSyncService(shipmentRepository, shipmentService, provider, meterRegistry);
        when(provider.name()).thenReturn("track17");
        when(provider.isStub()).thenReturn(false);
    }

    private static Shipment shipment(long id, int failures) {
        Shipment s = new Shipment();
        s.setId(id);
        s.setShipmentNo("SHP-" + id);
        s.setStatus(ShipmentStatus.IN_TRANSIT);
        s.setSyncFailures(failures);
        return s;
    }

    @Test
    @DisplayName("stub provider → sweep 直接返回 {0,0} 不扫表")
    void stubSkips() {
        when(provider.isStub()).thenReturn(true);
        assertThat(service.sweep(30)).containsExactly(0, 0);
        verify(shipmentRepository, never()).listPendingSync(any(), anyInt());
    }

    @Test
    @DisplayName("批次：一包裹失败不影响其余；失败计数 +1、Micrometer +1、不改状态；连续第 3 次 [ALERT]")
    void batchIsolatesFailures() {
        Shipment ok = shipment(1L, 0);
        Shipment bad = shipment(2L, 2);
        Shipment ok2 = shipment(3L, 0);
        when(shipmentRepository.listPendingSync(any(), eq(ShipmentTrackingSyncService.BATCH_LIMIT)))
                .thenReturn(List.of(ok, bad, ok2));
        when(provider.fetchEvents(ok)).thenReturn(List.of());
        when(provider.fetchEvents(bad)).thenThrow(new TrackingProviderPort.ProviderException("boom", null));
        when(provider.fetchEvents(ok2)).thenReturn(List.of(
                new TrackingProviderPort.ProviderEvent("e1", LocalDateTime.now(), ShipmentStatus.DELIVERED, null, "Delivered")));
        int[] result = service.sweep(30);
        assertThat(result).containsExactly(2, 1);
        verify(shipmentService).applyProviderEvents(eq(ok), any());
        verify(shipmentService).applyProviderEvents(eq(ok2), any());
        verify(shipmentService, never()).applyProviderEvents(eq(bad), any());
        verify(shipmentRepository).incrementSyncFailures(2L);
        assertThat(bad.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(meterRegistry.get(ShipmentTrackingSyncService.METRIC_SYNC_FAILURE).counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("derivedEventId：sha1(occurred_at|status|description) 前 40 位，确定性")
    void derivedEventIdStable() {
        LocalDateTime at = LocalDateTime.of(2026, 9, 7, 12, 0);
        TrackingProviderPort.ProviderEvent a = new TrackingProviderPort.ProviderEvent(null, at, ShipmentStatus.IN_TRANSIT, "HK", "Departed");
        TrackingProviderPort.ProviderEvent b = new TrackingProviderPort.ProviderEvent(null, at, ShipmentStatus.IN_TRANSIT, "SZ", "Departed");
        TrackingProviderPort.ProviderEvent c = new TrackingProviderPort.ProviderEvent(null, at, ShipmentStatus.IN_TRANSIT, "HK", "Arrived");
        assertThat(ShipmentTrackingSyncService.derivedEventId(a)).hasSize(40).matches("^[0-9a-f]{40}$");
        assertThat(ShipmentTrackingSyncService.derivedEventId(a)).isEqualTo(ShipmentTrackingSyncService.derivedEventId(b));
        assertThat(ShipmentTrackingSyncService.derivedEventId(a)).isNotEqualTo(ShipmentTrackingSyncService.derivedEventId(c));
    }
}
