package com.dreamy.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Find My Size 尺码推荐入参/出参（E-CAT-05，决策 20.3）。
 * L2 TRACE: openapi SizeRecommendationRequest/SizeRecommendationResponse。
 */
public final class SizeRecommendation {

    private SizeRecommendation() {
    }

    /** 入参（V-CAT-014~016 由 Service 校验；fit_preference ∈ {snug,regular,relaxed} 缺省 regular） */
    public record Request(
            BigDecimal height,
            BigDecimal bust,
            BigDecimal waist,
            BigDecimal hips,
            String fitPreference
    ) {
    }

    /** 出参（matched=false 时 explanation 给建议话术，不虚构买家占比） */
    public record Response(
            boolean matched,
            SizeChartRowDto recommendedRow,
            String explanation,
            List<DimensionNote> dimensionNotes
    ) {
    }

    /** 每维度落点说明（跨码段时透出取大码建议） */
    public record DimensionNote(String dimension, String matchedUs) {
    }
}
