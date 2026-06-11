package com.dreamy.analytics.domain.dashboard.service;

import com.dreamy.analytics.error.AnalyticsErrorCode;
import com.dreamy.analytics.error.AnalyticsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TC-ANA-010：range 解析（V-ANA-002/003 + DEC-ANA-2 UTC 窗口口径；422001 含 details.allowed）。
 */
class RangeWindowTest {

    @Test
    @DisplayName("7d/30d/90d → 窗口 [今日−(N−1)d 00:00 UTC, now]，含今日共 N 桶")
    void parseValidRanges() {
        for (var expected : new Object[][]{{"7d", 7}, {"30d", 30}, {"90d", 90}}) {
            RangeWindow window = RangeWindow.parse((String) expected[0]);
            assertThat(window.range()).isEqualTo(expected[0]);
            assertThat(window.days()).isEqualTo(expected[1]);
            LocalDateTime todayStart = LocalDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay();
            assertThat(window.from())
                    .isEqualTo(todayStart.minusDays(((int) expected[1]) - 1L));
            assertThat(window.from().truncatedTo(ChronoUnit.DAYS)).isEqualTo(window.from());
            assertThat(window.to()).isAfter(window.from());
        }
    }

    @Test
    @DisplayName("null → 缺省 30d")
    void nullDefaultsTo30d() {
        RangeWindow window = RangeWindow.parse(null);
        assertThat(window.range()).isEqualTo("30d");
        assertThat(window.days()).isEqualTo(30);
    }

    @Test
    @DisplayName("非法值（'30D'/'7'/空串）→ 422001 INVALID_RANGE + details.allowed")
    void invalidValuesThrow422001() {
        for (String bad : new String[]{"30D", "7", "", "1y", "7天"}) {
            assertThatThrownBy(() -> RangeWindow.parse(bad))
                    .isInstanceOf(AnalyticsException.class)
                    .satisfies(ex -> {
                        AnalyticsException ae = (AnalyticsException) ex;
                        assertThat(ae.getErrorCode()).isEqualTo(AnalyticsErrorCode.INVALID_RANGE);
                        assertThat(ae.getErrorCode().getCode()).isEqualTo(422001);
                        assertThat(ae.getDetails()).containsEntry("field", "range");
                        assertThat(ae.getDetails().get("allowed"))
                                .isEqualTo(java.util.List.of("7d", "30d", "90d"));
                    });
        }
    }

    @Test
    @DisplayName("GA4 dateRange startDate 格式：(N−1)daysAgo")
    void ga4StartDateFormat() {
        assertThat(RangeWindow.parse("7d").ga4StartDate()).isEqualTo("6daysAgo");
        assertThat(RangeWindow.parse("30d").ga4StartDate()).isEqualTo("29daysAgo");
        assertThat(RangeWindow.parse("90d").ga4StartDate()).isEqualTo("89daysAgo");
    }
}
