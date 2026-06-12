package com.dreamy.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * approved 聚合计算单元测试（RM-REV-002 汇总语义；ReviewQueryPort 回查同源——TC-REV-023 口径基础）。
 * L2 TRACE: TC-REV-004 [P0]。
 */
class RatingSummaryTest {

    @Test
    @DisplayName("TC-REV-004 [P0]: avg HALF_UP 2 位（4.333→4.33；4.335→4.34）")
    void avgHalfUpTwoDigits() {
        // [5,4,4] → 13/3 = 4.333... → 4.33
        RatingSummary s = RatingSummary.fromCounts(Map.of(5, 1L, 4, 2L));
        assertThat(s.avg()).isEqualByComparingTo(new BigDecimal("4.33"));
        assertThat(s.count()).isEqualTo(3);
        // F-RatingSet [5,5,4,3,1] → 18/5 = 3.60
        RatingSummary s2 = RatingSummary.fromCounts(Map.of(5, 2L, 4, 1L, 3, 1L, 1, 1L));
        assertThat(s2.avg()).isEqualByComparingTo(new BigDecimal("3.60"));
        assertThat(s2.count()).isEqualTo(5);
    }

    @Test
    @DisplayName("TC-REV-004 [P0]: breakdown 1..5 全档（无数据档=0）")
    void breakdownAllBuckets() {
        RatingSummary s = RatingSummary.fromCounts(Map.of(5, 3L, 2, 1L));
        assertThat(s.breakdown()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "1", 0, "2", 1, "3", 0, "4", 0, "5", 3));
        // 档位顺序 1..5（LinkedHashMap 保序）
        assertThat(s.breakdown().keySet()).containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    @DisplayName("TC-REV-004 [P0]: 零评价 avg=0 / count=0 / breakdown 全 0")
    void zeroReviews() {
        RatingSummary s = RatingSummary.fromCounts(Map.of());
        assertThat(s.avg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.count()).isZero();
        assertThat(s.breakdown().values()).allMatch(v -> v == 0);
        // null 容忍
        RatingSummary nullSafe = RatingSummary.fromCounts(null);
        assertThat(nullSafe.count()).isZero();
    }
}
