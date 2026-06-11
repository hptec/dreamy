package com.dreamy.analytics.domain.dashboard.service;

import com.dreamy.analytics.dto.AnalyticsDtos.AnalyticsOverviewResponse;
import com.dreamy.analytics.dto.AnalyticsDtos.DashboardResponse;
import com.dreamy.analytics.infra.AnalyticsCacheService;
import org.springframework.stereotype.Service;

/**
 * 交易指标查询服务（E-ANA-01/02 编排：缓存 → 聚合 → 回填；FLOW-P16 上半场）。
 * 缓存失效=TTL 自然过期（决策 10/19，聚合数据无主动失效）；缓存层异常静默降级回源（可用性优先）。
 */
@Service
public class AnalyticsQueryService {

    private final AnalyticsAggregator aggregator;
    private final AnalyticsCacheService cache;

    public AnalyticsQueryService(AnalyticsAggregator aggregator, AnalyticsCacheService cache) {
        this.aggregator = aggregator;
        this.cache = cache;
    }

    /** E-ANA-01 getAdminDashboard（V-ANA-001 鉴权前置由过滤器/切面承载；CACHE-ANA-001 TTL 60s） */
    public DashboardResponse dashboard() {
        // STEP-ANA-01 缓存命中直接返回
        DashboardResponse cached = cache.getAggregate(AnalyticsCacheService.KEY_DASHBOARD);
        if (cached != null) {
            return cached;
        }
        // STEP-ANA-02~04 聚合（同一只读事务，TX-ANA-002）→ STEP-ANA-05 回填
        DashboardResponse fresh = aggregator.aggregateDashboard();
        cache.putAggregate(AnalyticsCacheService.KEY_DASHBOARD, fresh);
        return fresh;
    }

    /** E-ANA-02 getAdminAnalyticsOverview（V-ANA-002 range 校验；CACHE-ANA-002 key 含 range，TTL 60s） */
    public AnalyticsOverviewResponse overview(String rangeRaw) {
        RangeWindow range = RangeWindow.parse(rangeRaw);
        String key = AnalyticsCacheService.overviewKey(range.range());
        // STEP-ANA-01 缓存命中直接返回
        AnalyticsOverviewResponse cached = cache.getAggregate(key);
        if (cached != null) {
            return cached;
        }
        // STEP-ANA-02~05 聚合 → STEP-ANA-06 回填
        AnalyticsOverviewResponse fresh = aggregator.aggregateOverview(range);
        cache.putAggregate(key, fresh);
        return fresh;
    }
}
