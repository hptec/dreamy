package com.dreamy.infra.ga4;

import com.dreamy.dto.AnalyticsDtos.DeviceShareItem;
import com.dreamy.dto.AnalyticsDtos.FunnelStage;
import com.dreamy.dto.AnalyticsDtos.TrafficSourceItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-ANA-007/008/009：GA4 归一化矩阵（MAP-ANA-005~007 / DEC-ANA-6）。
 */
class Ga4NormalizerTest {

    @Test
    @DisplayName("TC-ANA-007 来源归一化矩阵 + 同桶求和 + share Σ=100 + sessions DESC")
    void sourceBucketMatrix() {
        assertThat(Ga4Normalizer.bucketOf("google", "organic")).isEqualTo("organic");
        assertThat(Ga4Normalizer.bucketOf("(direct)", "(none)")).isEqualTo("direct");
        assertThat(Ga4Normalizer.bucketOf("(direct)", "(not set)")).isEqualTo("direct");
        assertThat(Ga4Normalizer.bucketOf("instagram", "social")).isEqualTo("social");
        assertThat(Ga4Normalizer.bucketOf("pinterest", "referral")).isEqualTo("social"); // source 命中社媒表
        assertThat(Ga4Normalizer.bucketOf("newsletter", "email")).isEqualTo("email");
        assertThat(Ga4Normalizer.bucketOf("google", "cpc")).isEqualTo("paid");
        assertThat(Ga4Normalizer.bucketOf("bing", "paid-search")).isEqualTo("paid");
        assertThat(Ga4Normalizer.bucketOf("partner-site", "unknown")).isEqualTo("referral");

        List<TrafficSourceItem> items = Ga4Normalizer.normalizeSources(List.of(
                new Ga4TrafficRaw.SourceRow("google", "organic", 3800),
                new Ga4TrafficRaw.SourceRow("instagram", "social", 2400),
                new Ga4TrafficRaw.SourceRow("pinterest", "social", 1600),
                new Ga4TrafficRaw.SourceRow("(direct)", "(none)", 1400),
                new Ga4TrafficRaw.SourceRow("newsletter", "email", 800)));
        // 同桶求和：instagram+pinterest → social 4000，居首（sessions DESC）
        assertThat(items.get(0).source()).isEqualTo("social");
        assertThat(items.get(0).sessions()).isEqualTo(4000);
        assertThat(items.get(1).source()).isEqualTo("organic");
        // share Σ=100.0（尾差并最大桶）
        BigDecimal sum = items.stream().map(TrafficSourceItem::share).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.0");
    }

    @Test
    @DisplayName("TC-ANA-008 device 归一：大小写归位三桶；枚举外并入 desktop；空行集三桶 share=0 不报错")
    void deviceNormalization() {
        List<DeviceShareItem> items = Ga4Normalizer.normalizeDevices(List.of(
                new Ga4TrafficRaw.DeviceRow("Mobile", 680),
                new Ga4TrafficRaw.DeviceRow("desktop", 200),
                new Ga4TrafficRaw.DeviceRow("TABLET", 50),
                new Ga4TrafficRaw.DeviceRow("smarttv", 70)));
        assertThat(items).extracting(DeviceShareItem::device)
                .containsExactly("mobile", "desktop", "tablet");
        // smarttv 并入 desktop：desktop=270/1000=27.0%
        assertThat(items.get(1).share()).isEqualByComparingTo("27.0");
        BigDecimal sum = items.stream().map(DeviceShareItem::share).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.0");

        // GA4 空行集 → 三桶 share=0
        List<DeviceShareItem> empty = Ga4Normalizer.normalizeDevices(List.of());
        assertThat(empty).hasSize(3);
        assertThat(empty).allSatisfy(d -> assertThat(d.share()).isEqualByComparingTo("0.0"));
    }

    @Test
    @DisplayName("TC-ANA-009 funnel 对位：乱序/缺 begin_checkout → 固定五 stage 顺序、缺位补 0")
    void funnelAlignment() {
        List<FunnelStage> stages = Ga4Normalizer.normalizeFunnel(Map.of(
                "purchase", 3400L,
                "page_view", 100000L,
                "add_to_cart", 12600L,
                "view_item", 28400L));
        assertThat(stages).extracting(FunnelStage::stage)
                .containsExactly("page_view", "view_item", "add_to_cart", "begin_checkout", "purchase");
        assertThat(stages).extracting(FunnelStage::value)
                .containsExactly(100000L, 28400L, 12600L, 0L, 3400L);
    }

    @Test
    @DisplayName("ok 形态组装：source_status=ok + fetched_at 透传")
    void normalizeOkShape() {
        var response = Ga4Normalizer.normalize(new Ga4TrafficRaw(List.of(), List.of(), Map.of()),
                "2026-06-10T00:00:00Z");
        assertThat(response.sourceStatus()).isEqualTo("ok");
        assertThat(response.fetchedAt()).isEqualTo("2026-06-10T00:00:00Z");
        assertThat(response.trafficSources()).isEmpty();
        assertThat(response.deviceShare()).hasSize(3);
        assertThat(response.funnel()).hasSize(5);
    }
}
