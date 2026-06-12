package com.dreamy.domain.dashboard.service;

import com.dreamy.dto.AnalyticsDtos.AnalyticsTrafficResponse;
import com.dreamy.error.AnalyticsErrorCode;
import com.dreamy.error.AnalyticsException;
import com.dreamy.error.Ga4TimeoutException;
import com.dreamy.error.Ga4UnavailableException;
import com.dreamy.infra.AnalyticsCacheService;
import com.dreamy.infra.ga4.Ga4FetchException;
import com.dreamy.infra.ga4.Ga4TrafficPort;
import com.dreamy.infra.ga4.Ga4TrafficRaw;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DEC-ANA-5 三级降级链单测（TC-ANA-016~019 单测形态 + TC-ANA-038 失败分类）。
 */
@ExtendWith(MockitoExtension.class)
class TrafficServiceTest {

    @Mock
    private Ga4TrafficPort ga4Port;
    @Mock
    private AnalyticsCacheService cache;

    private TrafficService service;

    private final Ga4TrafficRaw stubRaw = new Ga4TrafficRaw(
            List.of(new Ga4TrafficRaw.SourceRow("google", "organic", 3800)),
            List.of(new Ga4TrafficRaw.DeviceRow("mobile", 6800)),
            Map.of("page_view", 100000L));

    @BeforeEach
    void setUp() {
        service = new TrafficService(ga4Port, cache);
    }

    @Test
    @DisplayName("① fresh 缓存命中 → 直接返回，零 GA4 调用")
    void freshCacheHit() {
        AnalyticsTrafficResponse cached = AnalyticsTrafficResponse.unavailable();
        when(cache.getTraffic("30d")).thenReturn(cached);
        assertThat(service.traffic("30d")).isSameAs(cached);
        verify(ga4Port, never()).fetch(any());
    }

    @Test
    @DisplayName("TC-ANA-016 ② 成功链：GA4 成功 → 200 ok + fresh/stale 双写")
    void successWritesFreshAndStale() {
        when(cache.getTraffic("7d")).thenReturn(null);
        when(ga4Port.fetch(any())).thenReturn(stubRaw);
        AnalyticsTrafficResponse response = service.traffic("7d");
        assertThat(response.sourceStatus()).isEqualTo("ok");
        assertThat(response.fetchedAt()).isNotNull();
        assertThat(response.trafficSources()).isNotEmpty();
        verify(cache, times(1)).putTrafficFresh(eq("7d"), any());
        verify(cache, times(1)).putStale(eq("7d"), any());
    }

    @Test
    @DisplayName("TC-ANA-017 ③ stale 兜底：GA4 失败 + stale 存在 → 200 ok 快照原样（含快照 fetched_at），不回写 fresh")
    void staleFallback() {
        when(cache.getTraffic("30d")).thenReturn(null);
        when(ga4Port.fetch(any())).thenThrow(new Ga4FetchException("down", false));
        AnalyticsTrafficResponse stale = new AnalyticsTrafficResponse("ok", "2026-06-09T00:00:00Z",
                List.of(), List.of(), List.of());
        when(cache.getStale("30d")).thenReturn(stale);
        AnalyticsTrafficResponse response = service.traffic("30d");
        assertThat(response).isSameAs(stale);
        assertThat(response.fetchedAt()).isEqualTo("2026-06-09T00:00:00Z");
        verify(cache, never()).putTrafficFresh(anyString(), any());
        verify(cache, never()).putTrafficDegraded(anyString(), any());
    }

    @Test
    @DisplayName("TC-ANA-018 ④ 彻底降级：双缺失 → 200 unavailable + 三字段 null + 降级体 60s 短缓存")
    void fullDegrade() {
        when(cache.getTraffic("30d")).thenReturn(null);
        when(ga4Port.fetch(any())).thenThrow(new Ga4FetchException("down", false));
        when(cache.getStale("30d")).thenReturn(null);
        AnalyticsTrafficResponse response = service.traffic("30d");
        assertThat(response.sourceStatus()).isEqualTo("unavailable");
        assertThat(response.fetchedAt()).isNull();
        assertThat(response.trafficSources()).isNull();
        assertThat(response.deviceShare()).isNull();
        assertThat(response.funnel()).isNull();
        ArgumentCaptor<AnalyticsTrafficResponse> captor = ArgumentCaptor.forClass(AnalyticsTrafficResponse.class);
        verify(cache).putTrafficDegraded(eq("30d"), captor.capture());
        assertThat(captor.getValue().sourceStatus()).isEqualTo("unavailable");
    }

    @Test
    @DisplayName("TC-ANA-019 ⑤ 兜底链失效：读 stale 抛缓存异常 → 502001 / 超时形态 → 504001")
    void fallbackChainFailureMapsTo502And504() {
        when(cache.getTraffic("30d")).thenReturn(null);
        when(ga4Port.fetch(any())).thenThrow(new Ga4FetchException("down", false));
        when(cache.getStale("30d")).thenThrow(new AnalyticsCacheService.CacheAccessException("redis down"));
        assertThatThrownBy(() -> service.traffic("30d"))
                .isInstanceOf(Ga4UnavailableException.class)
                .satisfies(ex -> assertThat(((AnalyticsException) ex).getErrorCode().getCode()).isEqualTo(502001));

        // timeout 形态 → 504001（TC-ANA-038 失败分类联动）
        doThrow(new Ga4FetchException("timeout", true)).when(ga4Port).fetch(any());
        assertThatThrownBy(() -> service.traffic("30d"))
                .isInstanceOf(Ga4TimeoutException.class)
                .satisfies(ex -> assertThat(((AnalyticsException) ex).getErrorCode().getCode()).isEqualTo(504001));
    }

    @Test
    @DisplayName("⑤ 写降级体失败同样进入兜底码映射")
    void degradedBodyPutFailureMapsToCode() {
        when(cache.getTraffic("30d")).thenReturn(null);
        when(ga4Port.fetch(any())).thenThrow(new Ga4FetchException("down", false));
        when(cache.getStale("30d")).thenReturn(null);
        doThrow(new AnalyticsCacheService.CacheAccessException("redis down"))
                .when(cache).putTrafficDegraded(eq("30d"), any());
        assertThatThrownBy(() -> service.traffic("30d")).isInstanceOf(Ga4UnavailableException.class);
    }

    @Test
    @DisplayName("V-ANA-003 range 非法 → 422001（缓存/GA4 均不触达）")
    void invalidRangeRejectedBeforeCache() {
        assertThatThrownBy(() -> service.traffic("7"))
                .isInstanceOf(AnalyticsException.class)
                .satisfies(ex -> assertThat(((AnalyticsException) ex).getErrorCode())
                        .isEqualTo(AnalyticsErrorCode.INVALID_RANGE));
        verify(cache, never()).getTraffic(anyString());
        verify(ga4Port, never()).fetch(any());
    }
}
