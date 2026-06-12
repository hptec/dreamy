package com.dreamy.port;

import java.math.BigDecimal;

/**
 * review 领域查询端口（进程内直调；EVT-CAT-002 评分回写数据源）。
 * review 域未实现前由 StubReviewQueryPort 兜底（@ConditionalOnMissingBean）。
 */
public interface ReviewQueryPort {

    /** 已通过（approved）评价聚合 {avg, count} */
    RatingSummary approvedRatingSummary(Long productId);

    record RatingSummary(BigDecimal avg, int count) {
        public static RatingSummary empty() {
            return new RatingSummary(BigDecimal.ZERO, 0);
        }
    }
}
