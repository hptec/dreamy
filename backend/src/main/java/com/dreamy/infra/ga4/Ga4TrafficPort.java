package com.dreamy.infra.ga4;

import com.dreamy.domain.dashboard.service.RangeWindow;

/**
 * GA4 流量拉取端口（SVC-ANA §5.1；BE-DIM-5 防腐层）。
 * 实现选择（@ConditionalOnProperty dreamy.ga4.mode）：Ga4Client（real）/ Ga4StubClient（stub，DEC-ANA-7）。
 */
public interface Ga4TrafficPort {

    /**
     * 单次 batchRunReports 拉取三报表（≤5 报表限额内用 3 个），一回合往返，不重试
     * （读路径由缓存与 stale 兜底；重试只放大尾延迟）。
     *
     * @throws Ga4FetchException 失败（含 timeout 标记，§5.3 分类）
     */
    Ga4TrafficRaw fetch(RangeWindow range);
}
