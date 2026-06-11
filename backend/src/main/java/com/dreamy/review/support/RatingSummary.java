package com.dreamy.review.support;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * approved 评价聚合（RM-REV-002 汇总语义）：avg 保留 2 位 HALF_UP，breakdown 1..5 全档（无数据档=0），
 * 零评价 avg=0/count=0/breakdown 全 0。
 * 同时是 ReviewQueryPort.approvedRatingSummary 的计算承载（catalog EVT-CAT-002 回查同源，口径强一致）。
 * L2 TRACE: RM-REV-002 / NP-REV-002 / TC-REV-004 / TC-REV-023。
 */
public record RatingSummary(
        BigDecimal avg,
        int count,
        Map<String, Integer> breakdown
) implements Serializable {

    /** GROUP BY rating 结果（rating → count）→ 聚合三元组 */
    public static RatingSummary fromCounts(Map<Integer, Long> counts) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        long total = 0;
        long weighted = 0;
        for (int star = 1; star <= 5; star++) {
            long c = counts == null ? 0 : counts.getOrDefault(star, 0L);
            breakdown.put(String.valueOf(star), (int) c);
            total += c;
            weighted += (long) star * c;
        }
        BigDecimal avg = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(weighted).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new RatingSummary(avg, (int) total, breakdown);
    }
}
