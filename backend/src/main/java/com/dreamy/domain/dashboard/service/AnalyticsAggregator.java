package com.dreamy.domain.dashboard.service;

import com.dreamy.dto.AnalyticsDtos.AnalyticsOverviewResponse;
import com.dreamy.dto.AnalyticsDtos.DashboardResponse;
import com.dreamy.dto.AnalyticsDtos.Kpis;
import com.dreamy.dto.AnalyticsDtos.Todos;
import com.dreamy.dto.AnalyticsDtos.TrendSeries;
import com.dreamy.domain.dashboard.repository.AnalyticsReadMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 交易指标聚合器（TX-ANA-001/002：本域无写事务；多条聚合 SQL 同一声明式只读事务内执行——
 * 同一 MVCC 快照，KPI 与趋势数值口径一致；不持有行锁/表锁，不阻塞 trading 写路径）。
 */
@Service
public class AnalyticsAggregator {

    /** Top 商品取前 6（契约 maxItems=6） */
    private static final int TOP_PRODUCTS_LIMIT = 6;

    private final AnalyticsReadMapper readMapper;

    public AnalyticsAggregator(AnalyticsReadMapper readMapper) {
        this.readMapper = readMapper;
    }

    /** E-ANA-01 STEP-ANA-02~04（KPI 本月 + 待办全量现态 + 近 30 天趋势，DEC-ANA-4） */
    @Transactional(readOnly = true)
    public DashboardResponse aggregateDashboard() {
        Kpis kpis = aggregateMonthKpis();
        // STEP-ANA-03 待办计数（全量现态，非窗口）
        Todos todos = new Todos(
                readMapper.countPendingRefunds(),
                readMapper.countPendingReviews(),
                readMapper.countUnshippedOrders());
        // STEP-ANA-04 gmv_trend 固定近 30 天（前端 14/30 天按钮客户端切片）
        RangeWindow trend = RangeWindow.of(RangeWindow.DEFAULT_RANGE, 30);
        TrendSeries gmvTrend = AnalyticsAssembler.fillTrend(trend.firstDay(), trend.days(),
                readMapper.gmvTrendDaily(trend.from(), trend.to()));
        return new DashboardResponse(kpis, todos, gmvTrend);
    }

    /** E-ANA-02 STEP-ANA-02~05（KPI 固定本月与 range 无关；趋势/品类/Top 按 range 窗口） */
    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse aggregateOverview(RangeWindow range) {
        Kpis kpis = aggregateMonthKpis();
        TrendSeries gmvTrend = AnalyticsAssembler.fillTrend(range.firstDay(), range.days(),
                readMapper.gmvTrendDaily(range.from(), range.to()));
        var categorySales = AnalyticsAssembler.buildCategorySales(
                readMapper.categorySales(range.from(), range.to()));
        var topProducts = AnalyticsAssembler.buildTopProducts(
                readMapper.topProducts(range.from(), range.to(), TOP_PRODUCTS_LIMIT));
        return new AnalyticsOverviewResponse(kpis, gmvTrend, categorySales, topProducts);
    }

    /** STEP-ANA-02 KPI（窗口=本月 UTC：当月 1 日 00:00 至当前，DEC-ANA-2/3 口径） */
    private Kpis aggregateMonthKpis() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
        var gmv = readMapper.sumPaidGmvUsd(monthStart, now);
        long orderCount = readMapper.countPaidOrders(monthStart, now);
        long approvedRefunds = readMapper.countApprovedRefunds(monthStart, now);
        return AnalyticsAssembler.buildKpis(gmv, orderCount, approvedRefunds);
    }
}
