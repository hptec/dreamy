package com.dreamy.domain.dashboard.service;

import com.dreamy.error.AnalyticsErrorCode;
import com.dreamy.error.AnalyticsException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * range 窗口解析（V-ANA-002/003 + DEC-ANA-2 时间口径）。
 * - range ∈ {7d, 30d, 90d}，缺省（null）30d；枚举外取值（含空串/大小写变体 30D）→ 422001。
 * - 窗口 = [今日(UTC)−(N−1)天 00:00, 现在]（含今日共 N 个日桶）。
 * 纯函数，独立可单测（TC-ANA-010）。
 */
public record RangeWindow(String range, int days, LocalDateTime from, LocalDateTime to) {

    public static final String DEFAULT_RANGE = "30d";
    private static final List<String> ALLOWED = List.of("7d", "30d", "90d");

    /** V-ANA-002：null → 缺省 30d；非法（含空串/大小写变体）→ 422001 {field:"range", allowed} */
    public static RangeWindow parse(String raw) {
        String range = raw == null ? DEFAULT_RANGE : raw;
        if (!ALLOWED.contains(range)) {
            throw new AnalyticsException(AnalyticsErrorCode.INVALID_RANGE,
                    Map.of("field", "range", "allowed", ALLOWED));
        }
        int days = Integer.parseInt(range.substring(0, range.length() - 1));
        return of(range, days);
    }

    /** 固定 N 日窗口（dashboard gmv_trend 固定近 30 天，DEC-ANA-4） */
    public static RangeWindow of(String range, int days) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime from = now.toLocalDate().minusDays(days - 1L).atStartOfDay();
        return new RangeWindow(range, days, from, now);
    }

    /** 窗口首日（UTC 日历，MAP-ANA-002 补零基准） */
    public LocalDate firstDay() {
        return from.toLocalDate();
    }

    /** GA4 dateRange startDate：`(N−1)daysAgo`（E-ANA-03 STEP-ANA-02） */
    public String ga4StartDate() {
        return (days - 1) + "daysAgo";
    }
}
