package com.dreamy.analytics.controller;

import com.dreamy.analytics.domain.dashboard.service.AnalyticsQueryService;
import com.dreamy.analytics.domain.dashboard.service.TrafficService;
import com.dreamy.analytics.dto.AnalyticsDtos.AnalyticsOverviewResponse;
import com.dreamy.analytics.dto.AnalyticsDtos.AnalyticsTrafficResponse;
import com.dreamy.analytics.dto.AnalyticsDtos.DashboardResponse;
import com.dreamy.identity.aspect.RequirePermission;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据看板控制器（E-ANA-01~03；只读、不写 OperationLog——审计枚举无 analytics 条目）。
 * 鉴权：AdminJwtFilter(40100) + RBAC——E-ANA-01 → `/dashboard`；E-ANA-02/03 → `/analytics`（权限点隔离）。
 * 不经 CDN（管理端带鉴权数据）。
 */
@RestController
public class AnalyticsController {

    private final AnalyticsQueryService queryService;
    private final TrafficService trafficService;

    public AnalyticsController(AnalyticsQueryService queryService, TrafficService trafficService) {
        this.queryService = queryService;
        this.trafficService = trafficService;
    }

    /** E-ANA-01 getAdminDashboard（V-ANA-001；CACHE-ANA-001 TTL 60s） */
    @RequirePermission("/dashboard")
    @GetMapping("/api/admin/dashboard")
    public ResponseEntity<R<DashboardResponse>> dashboard() {
        return ResponseEntity.ok(R.ok(queryService.dashboard()));
    }

    /** E-ANA-02 getAdminAnalyticsOverview（V-ANA-002 range 缺省 30d；CACHE-ANA-002 TTL 60s） */
    @RequirePermission("/analytics")
    @GetMapping("/api/admin/analytics/overview")
    public ResponseEntity<R<AnalyticsOverviewResponse>> overview(
            @RequestParam(value = "range", required = false) String range) {
        return ResponseEntity.ok(R.ok(queryService.overview(range)));
    }

    /** E-ANA-03 getAdminAnalyticsTraffic（V-ANA-003；CACHE-ANA-003 TTL 300s + DEC-ANA-5 降级链） */
    @RequirePermission("/analytics")
    @GetMapping("/api/admin/analytics/traffic")
    public ResponseEntity<R<AnalyticsTrafficResponse>> traffic(
            @RequestParam(value = "range", required = false) String range) {
        return ResponseEntity.ok(R.ok(trafficService.traffic(range)));
    }
}
