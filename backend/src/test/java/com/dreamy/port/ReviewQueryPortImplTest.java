package com.dreamy.port;

import com.dreamy.port.ReviewQueryPort;
import com.dreamy.domain.review.repository.ReviewRepository;
import com.dreamy.support.RatingSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ReviewQueryPort 口径一致性单元测试（catalog EVT-CAT-002 回查与 E-REV-01 聚合展示同源 RM-REV-002）。
 * L2 TRACE: TC-REV-023 [P1]。
 */
@ExtendWith(MockitoExtension.class)
class ReviewQueryPortImplTest {

    @Mock
    ReviewRepository reviewRepository;

    @Test
    @DisplayName("TC-REV-023 [P1]: approvedRatingSummary 与展示聚合同源相等（同一 GROUP BY 计数输入）")
    void sameSourceConsistency() {
        Map<Integer, Long> counts = Map.of(5, 2L, 4, 1L, 3, 1L, 1, 1L);
        when(reviewRepository.countApprovedByRating(11L)).thenReturn(counts);
        ReviewQueryPortImpl port = new ReviewQueryPortImpl(reviewRepository);

        ReviewQueryPort.RatingSummary portSummary = port.approvedRatingSummary(11L);
        RatingSummary displaySummary = RatingSummary.fromCounts(counts);

        assertThat(portSummary.avg()).isEqualByComparingTo(displaySummary.avg());
        assertThat(portSummary.count()).isEqualTo(displaySummary.count());
        assertThat(portSummary.avg()).isEqualByComparingTo(new BigDecimal("3.60"));
    }

    @Test
    @DisplayName("TC-REV-023 [P1]: 零评价（rejected/pending 不计入聚合源）→ avg=0 / count=0")
    void zeroApproved() {
        when(reviewRepository.countApprovedByRating(12L)).thenReturn(Map.of());
        ReviewQueryPortImpl port = new ReviewQueryPortImpl(reviewRepository);
        ReviewQueryPort.RatingSummary summary = port.approvedRatingSummary(12L);
        assertThat(summary.avg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.count()).isZero();
    }
}
