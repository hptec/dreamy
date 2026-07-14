package com.dreamy.sched;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.gateway.entity.ExternalGatewayConfig;
import com.dreamy.domain.gateway.repository.ExternalGatewayConfigMapper;
import com.dreamy.domain.gateway.service.GatewayConfigService;
import com.dreamy.enums.GatewayType;
import com.dreamy.enums.ModelRefreshStrategy;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 网关模型定时刷新调度（决策5 / gateway_degradation.model_sync_failure）。
 * 扫描 model_refresh_strategy=SCHEDULED 且 enabled 的 AI 网关，按 interval 到期则触发 syncModels。
 * 分布式锁 gateway:model-refresh 防多实例并发；单条失败不影响其它（syncModels 内部降级计数）。
 */
@Component
public class GatewayModelRefreshScheduler {

    public static final String LOCK_KEY = "gateway:model-refresh";

    private static final Logger log = LoggerFactory.getLogger(GatewayModelRefreshScheduler.class);

    private final RedissonClient redissonClient;
    private final ExternalGatewayConfigMapper mapper;
    private final GatewayConfigService gatewayConfigService;

    public GatewayModelRefreshScheduler(RedissonClient redissonClient,
                                        ExternalGatewayConfigMapper mapper,
                                        GatewayConfigService gatewayConfigService) {
        this.redissonClient = redissonClient;
        this.mapper = mapper;
        this.gatewayConfigService = gatewayConfigService;
    }

    /** 每 5 分钟扫描一次，到期（now - models_synced_at >= interval）的配置触发同步。 */
    @Scheduled(fixedDelayString = "${dreamy.gateway.model-refresh-scan-delay-ms:300000}")
    public void run() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (!lock.tryLock()) {
            return;
        }
        try {
            LambdaQueryWrapper<ExternalGatewayConfig> qw = new LambdaQueryWrapper<>();
            qw.eq(ExternalGatewayConfig::getGatewayType, GatewayType.AI.getKey())
                    .eq(ExternalGatewayConfig::getModelRefreshStrategy, ModelRefreshStrategy.SCHEDULED.getKey())
                    .eq(ExternalGatewayConfig::getEnabled, true);
            List<ExternalGatewayConfig> configs = mapper.selectList(qw);
            LocalDateTime now = LocalDateTime.now();
            int triggered = 0;
            for (ExternalGatewayConfig cfg : configs) {
                if (isDue(cfg, now)) {
                    try {
                        gatewayConfigService.syncModelsScheduled(cfg.getId());
                        triggered++;
                    } catch (RuntimeException ex) {
                        // syncModels 内部已记降级计数；调度层吞异常防止单条失败中断扫描
                        log.warn("[SCHED-GATEWAY] id={} 定时模型刷新失败（已降级计数）", cfg.getId());
                    }
                }
            }
            if (triggered > 0) {
                log.info("[SCHED-GATEWAY] 定时模型刷新触发 {} 条", triggered);
            }
        } catch (Exception ex) {
            log.error("[SCHED-GATEWAY] 模型刷新扫描失败", ex);
        } finally {
            lock.unlock();
        }
    }

    /** 到期判定：从未同步 → 立即；否则 now - models_synced_at >= interval 分钟。 */
    private boolean isDue(ExternalGatewayConfig cfg, LocalDateTime now) {
        Integer interval = cfg.getModelRefreshIntervalMin();
        if (interval == null || interval <= 0) {
            return false;
        }
        LocalDateTime syncedAt = cfg.getModelsSyncedAt();
        if (syncedAt == null) {
            return true;
        }
        return !now.isBefore(syncedAt.plusMinutes(interval));
    }
}
