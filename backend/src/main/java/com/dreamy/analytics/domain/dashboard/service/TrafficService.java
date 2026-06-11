package com.dreamy.analytics.domain.dashboard.service;

import com.dreamy.analytics.dto.AnalyticsDtos.AnalyticsTrafficResponse;
import com.dreamy.analytics.error.Ga4TimeoutException;
import com.dreamy.analytics.error.Ga4UnavailableException;
import com.dreamy.analytics.infra.AnalyticsCacheService;
import com.dreamy.analytics.infra.ga4.Ga4FetchException;
import com.dreamy.analytics.infra.ga4.Ga4Normalizer;
import com.dreamy.analytics.infra.ga4.Ga4TrafficPort;
import com.dreamy.analytics.infra.ga4.Ga4TrafficRaw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 流量指标服务（E-ANA-03；FLOW-P16 下半场；决策 19 + DEC-ANA-5 三级降级链）。
 * ① fresh 缓存（300s）命中 → 200 ok；
 * ② 未命中调 GA4，成功 → 200 ok + 写 fresh + 写 stale 快照（24h remote-only）；
 * ③ GA4 失败/超时 → 读 stale，有 → 200 source_status=ok + fetched_at=快照生成时刻（不回写 fresh）；
 * ④ stale 亦缺失 → 200 source_status=unavailable + 流量字段 null + 降级体短缓存 60s（防穿透打 GA4）；
 * ⑤ 兜底链自身失效（读 stale/写降级体时缓存基础设施再抛）→ Ga4Timeout(504001)/Ga4Unavailable(502001)。
 * E-ANA-03 无 DB 访问，不开事务（TX-ANA-002）。
 */
@Service
public class TrafficService {

    private static final Logger log = LoggerFactory.getLogger(TrafficService.class);

    private final Ga4TrafficPort ga4Port;
    private final AnalyticsCacheService cache;

    public TrafficService(Ga4TrafficPort ga4Port, AnalyticsCacheService cache) {
        this.ga4Port = ga4Port;
        this.cache = cache;
    }

    /** E-ANA-03 getAdminAnalyticsTraffic（V-ANA-003 range 校验 → 降级链） */
    public AnalyticsTrafficResponse traffic(String rangeRaw) {
        RangeWindow range = RangeWindow.parse(rangeRaw);
        // STEP-ANA-01 fresh（或故障期降级体）命中 → 直接返回（fetched_at=缓存生成时刻）
        AnalyticsTrafficResponse cached = cache.getTraffic(range.range());
        if (cached != null) {
            return cached;
        }
        // STEP-ANA-02 调 GA4（单次 batchRunReports，connect 2s + read 8s，不重试）
        Ga4TrafficRaw raw;
        try {
            raw = ga4Port.fetch(range);
        } catch (Ga4FetchException fetchFailure) {
            return degrade(range, fetchFailure);
        }
        // STEP-ANA-03 归一化组装（MAP-ANA-005~007）→ 写 fresh(300s) + stale(24h)
        AnalyticsTrafficResponse ok = Ga4Normalizer.normalize(raw, Instant.now().toString());
        cache.putTrafficFresh(range.range(), ok);
        cache.putStale(range.range(), ok);
        return ok;
    }

    /** STEP-ANA-04/05/06：stale 兜底 → unavailable 降级体 → 兜底链自身失效映射 502001/504001 */
    private AnalyticsTrafficResponse degrade(RangeWindow range, Ga4FetchException cause) {
        try {
            // STEP-ANA-04（DEC-ANA-5 ③）：读 stale 快照——有则旧数据兜底，用户无感；不回写 fresh（下次再试 GA4）
            AnalyticsTrafficResponse stale = cache.getStale(range.range());
            if (stale != null) {
                log.info("[GA4-DEGRADE] ga4.degrade.stale range={} class={}",
                        range.range(), cause.isTimeout() ? "timeout" : "unavailable");
                return stale;
            }
            // STEP-ANA-05（DEC-ANA-5 ④）：彻底降级——200 unavailable + 三字段 null + 60s 短缓存防穿透
            AnalyticsTrafficResponse degraded = AnalyticsTrafficResponse.unavailable();
            cache.putTrafficDegraded(range.range(), degraded);
            log.info("[GA4-DEGRADE] ga4.degrade.unavailable range={} class={}",
                    range.range(), cause.isTimeout() ? "timeout" : "unavailable");
            return degraded;
        } catch (AnalyticsCacheService.CacheAccessException chainFailure) {
            // STEP-ANA-06（DEC-ANA-5 ⑤）：兜底链自身失效——按 GA4 失败形态映射 502001/504001
            log.error("[GA4-DEGRADE] fallback chain failed range={} class={}",
                    range.range(), cause.isTimeout() ? "timeout" : "unavailable");
            if (cause.isTimeout()) {
                throw new Ga4TimeoutException();
            }
            throw new Ga4UnavailableException();
        }
    }
}
