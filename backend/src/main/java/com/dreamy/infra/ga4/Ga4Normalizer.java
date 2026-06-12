package com.dreamy.infra.ga4;

import com.dreamy.dto.AnalyticsDtos.AnalyticsTrafficResponse;
import com.dreamy.dto.AnalyticsDtos.DeviceShareItem;
import com.dreamy.dto.AnalyticsDtos.FunnelStage;
import com.dreamy.dto.AnalyticsDtos.TrafficSourceItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * GA4 原始报表归一化（MAP-ANA-005~007，DEC-ANA-6；E-ANA-03 STEP-ANA-03）。
 * 纯函数，独立可单测（TC-ANA-007/008/009）。
 */
public final class Ga4Normalizer {

    /** 归一化桶（契约 source 取值与示例对齐） */
    static final String BUCKET_ORGANIC = "organic";
    static final String BUCKET_DIRECT = "direct";
    static final String BUCKET_SOCIAL = "social";
    static final String BUCKET_REFERRAL = "referral";
    static final String BUCKET_PAID = "paid";
    static final String BUCKET_EMAIL = "email";

    /** MAP-ANA-007 固定五 stage 顺序 */
    static final List<String> FUNNEL_STAGES =
            List.of("page_view", "view_item", "add_to_cart", "begin_checkout", "purchase");

    private static final Set<String> SOCIAL_SOURCES =
            Set.of("instagram", "facebook", "pinterest", "tiktok", "twitter");
    private static final Set<String> DIRECT_MEDIUMS = Set.of("(none)", "(not set)");
    private static final List<String> DEVICE_ORDER = List.of("mobile", "desktop", "tablet");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Ga4Normalizer() {
    }

    /** ok 形态出参组装（source_status=ok, fetched_at=now ISO8601 UTC） */
    public static AnalyticsTrafficResponse normalize(Ga4TrafficRaw raw, String fetchedAtIso) {
        return new AnalyticsTrafficResponse(AnalyticsTrafficResponse.STATUS_OK, fetchedAtIso,
                normalizeSources(raw.sourceRows()),
                normalizeDevices(raw.deviceRows()),
                normalizeFunnel(raw.eventCounts()));
    }

    /**
     * MAP-ANA-005 traffic_sources 归一化（STEP-ANA-03 映射表）：
     * medium ∈ {cpc,ppc,paid*} → paid；medium=organic → organic；
     * medium ∈ {social,sm} 或 source ∈ {instagram,facebook,pinterest,tiktok,twitter} → social；
     * source=(direct) 且 medium ∈ {(none),(not set)} → direct；medium=email → email；其余 → referral。
     * 同桶 sessions 求和，share=桶/总×100（1 位小数，尾差并最大桶），按 sessions DESC。
     */
    public static List<TrafficSourceItem> normalizeSources(List<Ga4TrafficRaw.SourceRow> rows) {
        Map<String, Long> buckets = new LinkedHashMap<>();
        if (rows != null) {
            for (Ga4TrafficRaw.SourceRow row : rows) {
                buckets.merge(bucketOf(row.source(), row.medium()), row.sessions(), Long::sum);
            }
        }
        long total = buckets.values().stream().mapToLong(Long::longValue).sum();
        List<Map.Entry<String, Long>> sorted = buckets.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .toList();
        List<TrafficSourceItem> items = new ArrayList<>(sorted.size());
        BigDecimal shareSum = BigDecimal.ZERO;
        for (Map.Entry<String, Long> entry : sorted) {
            BigDecimal share = share(entry.getValue(), total);
            shareSum = shareSum.add(share);
            items.add(new TrafficSourceItem(entry.getKey(), entry.getValue(), share));
        }
        // 尾差并入最大桶（首项）使 Σ=100.0
        if (!items.isEmpty() && total > 0) {
            BigDecimal diff = HUNDRED.setScale(1, RoundingMode.HALF_UP).subtract(shareSum);
            if (diff.signum() != 0) {
                TrafficSourceItem top = items.get(0);
                items.set(0, new TrafficSourceItem(top.source(), top.sessions(), top.share().add(diff)));
            }
        }
        return items;
    }

    /** 单行 source/medium → 归一化桶 */
    static String bucketOf(String sourceRaw, String mediumRaw) {
        String source = sourceRaw == null ? "" : sourceRaw.trim().toLowerCase(Locale.ROOT);
        String medium = mediumRaw == null ? "" : mediumRaw.trim().toLowerCase(Locale.ROOT);
        if (medium.equals("cpc") || medium.equals("ppc") || medium.startsWith("paid")) {
            return BUCKET_PAID;
        }
        if (medium.equals("organic")) {
            return BUCKET_ORGANIC;
        }
        if (medium.equals("social") || medium.equals("sm") || SOCIAL_SOURCES.contains(source)) {
            return BUCKET_SOCIAL;
        }
        if (source.equals("(direct)") && DIRECT_MEDIUMS.contains(medium)) {
            return BUCKET_DIRECT;
        }
        if (medium.equals("email")) {
            return BUCKET_EMAIL;
        }
        return BUCKET_REFERRAL;
    }

    /**
     * MAP-ANA-006 device_share：deviceCategory 小写化 → {mobile, desktop, tablet}（枚举外并入 desktop）；
     * GA4 空行集 → 三桶 share=0 不视为失败。
     */
    public static List<DeviceShareItem> normalizeDevices(List<Ga4TrafficRaw.DeviceRow> rows) {
        Map<String, Long> buckets = new LinkedHashMap<>();
        for (String device : DEVICE_ORDER) {
            buckets.put(device, 0L);
        }
        if (rows != null) {
            for (Ga4TrafficRaw.DeviceRow row : rows) {
                String device = row.device() == null ? "" : row.device().trim().toLowerCase(Locale.ROOT);
                if (!buckets.containsKey(device)) {
                    device = "desktop";
                }
                buckets.merge(device, row.sessions(), Long::sum);
            }
        }
        long total = buckets.values().stream().mapToLong(Long::longValue).sum();
        List<DeviceShareItem> items = new ArrayList<>(DEVICE_ORDER.size());
        BigDecimal shareSum = BigDecimal.ZERO;
        String maxDevice = null;
        long maxSessions = -1;
        for (String device : DEVICE_ORDER) {
            long sessions = buckets.get(device);
            BigDecimal share = share(sessions, total);
            shareSum = shareSum.add(share);
            items.add(new DeviceShareItem(device, share));
            if (sessions > maxSessions) {
                maxSessions = sessions;
                maxDevice = device;
            }
        }
        if (total > 0) {
            BigDecimal diff = HUNDRED.setScale(1, RoundingMode.HALF_UP).subtract(shareSum);
            if (diff.signum() != 0) {
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).device().equals(maxDevice)) {
                        items.set(i, new DeviceShareItem(maxDevice, items.get(i).share().add(diff)));
                        break;
                    }
                }
            }
        }
        return items;
    }

    /** MAP-ANA-007 funnel：固定五 stage 顺序对位填 value（缺事件补 0；value 取整由 long 承载） */
    public static List<FunnelStage> normalizeFunnel(Map<String, Long> eventCounts) {
        List<FunnelStage> stages = new ArrayList<>(FUNNEL_STAGES.size());
        for (String stage : FUNNEL_STAGES) {
            Long value = eventCounts == null ? null : eventCounts.get(stage);
            stages.add(new FunnelStage(stage, value == null ? 0L : value));
        }
        return stages;
    }

    private static BigDecimal share(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
