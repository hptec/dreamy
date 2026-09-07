package com.dreamy.sched;

import com.dreamy.domain.shipment.service.ShipmentTrackingSyncService;
import com.dreamy.infra.tracking.TrackingProperties;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 包裹轨迹同步调度（order-flow-complete D：cron dreamy.tracking.sync-cron 缺省每 30 分钟；
 * Redisson 锁 trading:shipment-sync；stub 模式静默跳过；批 200，单包裹失败不影响批次）。
 */
@Component
public class ShipmentTrackingSyncScheduler {

    public static final String LOCK_KEY = "trading:shipment-sync";

    private static final Logger log = LoggerFactory.getLogger(ShipmentTrackingSyncScheduler.class);

    private final RedissonClient redissonClient;
    private final ShipmentTrackingSyncService syncService;
    private final TrackingProperties properties;

    public ShipmentTrackingSyncScheduler(RedissonClient redissonClient, ShipmentTrackingSyncService syncService,
                                         TrackingProperties properties) {
        this.redissonClient = redissonClient;
        this.syncService = syncService;
        this.properties = properties;
    }

    @Scheduled(cron = "${dreamy.tracking.sync-cron:0 */30 * * * *}")
    public void run() {
        if (syncService.isStub()) {
            return;
        }
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            return;
        }
        try {
            syncService.sweep(properties.getSyncIntervalMinutes());
        } catch (Exception ex) {
            log.error("[SCHED-SHIPMENT-SYNC] sweep failed", ex);
        } finally {
            lock.unlock();
        }
    }
}
