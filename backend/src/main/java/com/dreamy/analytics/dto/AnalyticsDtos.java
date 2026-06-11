package com.dreamy.analytics.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * analytics 域响应 DTO 集（MAP-ANA-008：不可变 record；JSON 全局 SNAKE_CASE：gmvMonth→gmv_month）。
 * 全部实现 Serializable（JetCache java 序列化载荷，CACHE-ANA-001~005）。
 * er-diagram AnalyticsDashboard 十字段 → 三端点承载（DEC-ANA-1 虚拟聚合视图，不落库）。
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /** 契约 Kpis（聚合自 orders/refund，USD 基准口径；MAP-ANA-001） */
    public record Kpis(BigDecimal gmvMonth, long orderCount, BigDecimal avgOrderValue,
                       BigDecimal refundRate) implements Serializable {
    }

    /** 契约 DashboardResponse.todos（全量现态计数，RM-ANA-005~007） */
    public record Todos(long pendingRefundCount, long pendingReviewCount,
                        long unshippedOrderCount) implements Serializable {
    }

    /** 契约 TrendSeries（labels `M-D` + values；MAP-ANA-002 补零齐桶） */
    public record TrendSeries(List<String> labels, List<BigDecimal> values) implements Serializable {
    }

    /** E-ANA-01 出参 */
    public record DashboardResponse(Kpis kpis, Todos todos, TrendSeries gmvTrend) implements Serializable {
    }

    /** 契约 category_sales 项（MAP-ANA-003；溯根断链行落 {0, "Other"} 桶） */
    public record CategorySalesItem(long categoryId, String categoryName, BigDecimal amount,
                                    BigDecimal share) implements Serializable {
    }

    /** 契约 top_products 项（MAP-ANA-004；name/image_url 取 order_line 快照，DEC-ANA-8） */
    public record TopProductItem(long productId, String name, String imageUrl, long sales,
                                 BigDecimal amount) implements Serializable {
    }

    /** E-ANA-02 出参 */
    public record AnalyticsOverviewResponse(Kpis kpis, TrendSeries gmvTrend,
                                            List<CategorySalesItem> categorySales,
                                            List<TopProductItem> topProducts) implements Serializable {
    }

    /** 契约 traffic_sources 项（MAP-ANA-005 归一化桶：organic/direct/social/referral/paid/email） */
    public record TrafficSourceItem(String source, long sessions, BigDecimal share) implements Serializable {
    }

    /** 契约 device_share 项（MAP-ANA-006：mobile/desktop/tablet） */
    public record DeviceShareItem(String device, BigDecimal share) implements Serializable {
    }

    /** 契约 funnel 项（MAP-ANA-007 固定五 stage 顺序） */
    public record FunnelStage(String stage, long value) implements Serializable {
    }

    /**
     * E-ANA-03 出参。sourceStatus ∈ {ok, unavailable}（契约 required 仅 source_status）；
     * unavailable 形态三流量字段与 fetchedAt 为 null（DEC-ANA-5 ④）。
     */
    public record AnalyticsTrafficResponse(String sourceStatus, String fetchedAt,
                                           List<TrafficSourceItem> trafficSources,
                                           List<DeviceShareItem> deviceShare,
                                           List<FunnelStage> funnel) implements Serializable {

        public static final String STATUS_OK = "ok";
        public static final String STATUS_UNAVAILABLE = "unavailable";

        public static AnalyticsTrafficResponse unavailable() {
            return new AnalyticsTrafficResponse(STATUS_UNAVAILABLE, null, null, null, null);
        }
    }
}
