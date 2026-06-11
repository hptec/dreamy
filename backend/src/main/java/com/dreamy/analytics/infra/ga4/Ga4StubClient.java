package com.dreamy.analytics.infra.ga4;

import com.dreamy.analytics.domain.dashboard.service.RangeWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * GA4 stub 客户端（DEC-ANA-7：dreamy.ga4.mode=stub，dev/CI 默认）。
 * 返回确定样本（原型 mock 数值）：来源占比 自然搜索38/IG24/Pinterest16/直接14/邮件8；
 * 设备 68/27/5；漏斗 10万→2.84万→1.26万→8200（purchase 取 3400 补齐五事件）。
 * 无凭证环境流量 tab 不空白阻塞验收；联调与 UI 回归可复现。
 */
@Component
@ConditionalOnProperty(name = "dreamy.ga4.mode", havingValue = "stub", matchIfMissing = true)
public class Ga4StubClient implements Ga4TrafficPort {

    private static final Logger log = LoggerFactory.getLogger(Ga4StubClient.class);

    @Override
    public Ga4TrafficRaw fetch(RangeWindow range) {
        log.debug("[GA4-STUB] fetch range={}", range.range());
        return new Ga4TrafficRaw(
                List.of(
                        new Ga4TrafficRaw.SourceRow("google", "organic", 3800),
                        new Ga4TrafficRaw.SourceRow("instagram", "social", 2400),
                        new Ga4TrafficRaw.SourceRow("pinterest", "social", 1600),
                        new Ga4TrafficRaw.SourceRow("(direct)", "(none)", 1400),
                        new Ga4TrafficRaw.SourceRow("newsletter", "email", 800)),
                List.of(
                        new Ga4TrafficRaw.DeviceRow("mobile", 6800),
                        new Ga4TrafficRaw.DeviceRow("desktop", 2700),
                        new Ga4TrafficRaw.DeviceRow("tablet", 500)),
                Map.of(
                        "page_view", 100000L,
                        "view_item", 28400L,
                        "add_to_cart", 12600L,
                        "begin_checkout", 8200L,
                        "purchase", 3400L));
    }
}
