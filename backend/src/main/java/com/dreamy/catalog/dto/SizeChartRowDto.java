package com.dreamy.catalog.dto;

import java.math.BigDecimal;

/**
 * 尺码表行 DTO（请求/响应共用；MAP-CAT-011 recommended_row 原样行）。
 * L2 TRACE: openapi SizeChartRow。
 */
public record SizeChartRowDto(
        Long id,
        String us,
        String uk,
        String au,
        BigDecimal bust,
        BigDecimal waist,
        BigDecimal hips,
        BigDecimal hollowToFloor
) {
}
