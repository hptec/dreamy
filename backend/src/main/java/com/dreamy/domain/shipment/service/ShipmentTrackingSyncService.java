package com.dreamy.domain.shipment.service;

import com.dreamy.domain.shipment.entity.Shipment;
import com.dreamy.domain.shipment.repository.ShipmentRepository;
import com.dreamy.port.TrackingProviderPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 包裹轨迹供应商同步（order-flow-complete §4.6）：批 200（PENDING/IN_TRANSIT/OUT_FOR_DELIVERY/EXCEPTION 且
 * synced_at 早于 now-间隔），单包裹失败不影响批次；失败累加 sync_failures，连续 ≥3 次 [ALERT] 告警不改状态；
 * Micrometer dreamy.shipment.sync.failure.total。
 */
@Service
public class ShipmentTrackingSyncService {

    public static final String METRIC_SYNC_FAILURE = "dreamy.shipment.sync.failure.total";
    public static final int BATCH_LIMIT = 200;
    public static final int ALERT_FAILURES = 3;

    private static final Logger log = LoggerFactory.getLogger(ShipmentTrackingSyncService.class);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentService shipmentService;
    private final TrackingProviderPort provider;
    private final MeterRegistry meterRegistry;

    public ShipmentTrackingSyncService(ShipmentRepository shipmentRepository, ShipmentService shipmentService,
                                       TrackingProviderPort provider, MeterRegistry meterRegistry) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentService = shipmentService;
        this.provider = provider;
        this.meterRegistry = meterRegistry;
    }

    public boolean isStub() {
        return provider.isStub();
    }

    /** 扫一批；返回 {synced, failed} */
    public int[] sweep(int intervalMinutes) {
        if (provider.isStub()) {
            return new int[]{0, 0};
        }
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(intervalMinutes);
        List<Shipment> batch = shipmentRepository.listPendingSync(cutoff, BATCH_LIMIT);
        int synced = 0;
        int failed = 0;
        for (Shipment shipment : batch) {
            if (syncOne(shipment)) {
                synced++;
            } else {
                failed++;
            }
        }
        if (!batch.isEmpty()) {
            log.info("[SHIPMENT-SYNC] provider={} scanned={} synced={} failed={}", provider.name(), batch.size(),
                    synced, failed);
        }
        return new int[]{synced, failed};
    }

    /** 单包裹同步：成功 true；失败计数 + 告警 false（不改包裹状态） */
    public boolean syncOne(Shipment shipment) {
        try {
            List<TrackingProviderPort.ProviderEvent> events = provider.fetchEvents(shipment);
            shipmentService.applyProviderEvents(shipment, events);
            return true;
        } catch (Exception ex) {
            meterRegistry.counter(METRIC_SYNC_FAILURE, "provider", provider.name()).increment();
            int failures = (shipment.getSyncFailures() == null ? 0 : shipment.getSyncFailures()) + 1;
            try {
                shipmentRepository.incrementSyncFailures(shipment.getId());
            } catch (Exception inner) {
                log.warn("[SHIPMENT-SYNC] failure counter update failed shipment_no={}", shipment.getShipmentNo(), inner);
            }
            if (failures >= ALERT_FAILURES) {
                log.error("[SHIPMENT-SYNC][ALERT] shipment_no={} consecutive failures={} —— 状态保持不变，请检查供应商",
                        shipment.getShipmentNo(), failures, ex);
            } else {
                log.warn("[SHIPMENT-SYNC] shipment_no={} sync failed ({}): {}", shipment.getShipmentNo(), failures,
                        ex.getMessage());
            }
            return false;
        }
    }

    /** 供应商无事件 id：sha1(occurred_at|status|description) 前 40 位 */
    public static String derivedEventId(TrackingProviderPort.ProviderEvent event) {
        String input = event.occurredAt() + "|" + (event.status() == null ? "" : event.status().name()) + "|"
                + (event.description() == null ? "" : event.description());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] raw = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.substring(0, 40);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-1 unavailable", ex);
        }
    }
}
