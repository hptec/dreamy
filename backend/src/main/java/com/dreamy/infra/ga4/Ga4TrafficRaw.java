package com.dreamy.infra.ga4;

import java.util.List;
import java.util.Map;

/**
 * GA4 batchRunReports 三报表原始结果（SVC-ANA §5.1；归一化组装见 Ga4Normalizer MAP-ANA-005~007）。
 *
 * @param sourceRows  报表①：sessions by [sessionSource, sessionMedium]
 * @param deviceRows  报表②：sessions by [deviceCategory]
 * @param eventCounts 报表③：eventCount by [eventName]（五标准电商事件过滤）
 */
public record Ga4TrafficRaw(
        List<SourceRow> sourceRows,
        List<DeviceRow> deviceRows,
        Map<String, Long> eventCounts
) {

    /** sessionSource × sessionMedium 行 */
    public record SourceRow(String source, String medium, long sessions) {
    }

    /** deviceCategory 行 */
    public record DeviceRow(String device, long sessions) {
    }
}
