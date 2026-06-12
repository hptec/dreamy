package com.dreamy.domain.dashboard.service;

import com.dreamy.dto.AnalyticsDtos.CategorySalesItem;
import com.dreamy.dto.AnalyticsDtos.Kpis;
import com.dreamy.dto.AnalyticsDtos.TopProductItem;
import com.dreamy.dto.AnalyticsDtos.TrendSeries;
import com.dreamy.domain.dashboard.repository.readmodel.CategorySalesRow;
import com.dreamy.domain.dashboard.repository.readmodel.DailyGmvRow;
import com.dreamy.domain.dashboard.repository.readmodel.TopProductRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聚合行 → DTO 纯映射（MAP-ANA-001~004）。纯函数，独立可单测（TC-ANA-003/004/005/006）。
 */
public final class AnalyticsAssembler {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private AnalyticsAssembler() {
    }

    /**
     * MAP-ANA-001 KPI 组装：gmv HALF_UP 2 位；avg_order_value 派生（分母 0→0.00）；
     * refund_rate = approved 退款数 ÷ 支付订单数 × 100（DEC-ANA-3，1 位小数，分母 0→0）。
     */
    public static Kpis buildKpis(BigDecimal gmvUsd, long orderCount, long approvedRefundCount) {
        BigDecimal gmv = (gmvUsd == null ? BigDecimal.ZERO : gmvUsd).setScale(2, RoundingMode.HALF_UP);
        BigDecimal avg = orderCount == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : gmv.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
        BigDecimal refundRate = orderCount == 0
                ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(approvedRefundCount).multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(orderCount), 1, RoundingMode.HALF_UP);
        return new Kpis(gmv, orderCount, avg, refundRate);
    }

    /**
     * MAP-ANA-002 趋势补零：以窗口日历（UTC，N 桶）左连接聚合结果，缺日 value=0；
     * labels `M-D` 格式（DEC-ANA-2，如 5-29）；labels.length == values.length == N。
     */
    public static TrendSeries fillTrend(LocalDate firstDay, int days, List<DailyGmvRow> rows) {
        Map<LocalDate, BigDecimal> byDay = new HashMap<>();
        if (rows != null) {
            for (DailyGmvRow row : rows) {
                if (row.getDay() != null) {
                    byDay.put(row.getDay(), row.getGmvUsd());
                }
            }
        }
        List<String> labels = new ArrayList<>(days);
        List<BigDecimal> values = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate day = firstDay.plusDays(i);
            labels.add(day.getMonthValue() + "-" + day.getDayOfMonth());
            BigDecimal gmv = byDay.get(day);
            values.add((gmv == null ? BigDecimal.ZERO : gmv).setScale(2, RoundingMode.HALF_UP));
        }
        return new TrendSeries(labels, values);
    }

    /**
     * MAP-ANA-003 category_sales：share = amount/Σamount×100（1 位小数，尾差并入最大项使 Σ=100.0）；
     * Σamount==0 → 空数组；溯根断链行（root NULL）并入 {category_id:0, category_name:"Other"} 桶；按 amount DESC。
     */
    public static List<CategorySalesItem> buildCategorySales(List<CategorySalesRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        // 断链行归并 Other 桶（SQL 已按 root 分组，NULL root 至多一行，防御性求和）
        Map<Long, String> names = new HashMap<>();
        Map<Long, BigDecimal> amounts = new HashMap<>();
        for (CategorySalesRow row : rows) {
            long id = row.getRootCategoryId() == null ? 0L : row.getRootCategoryId();
            String name = row.getRootCategoryId() == null ? "Other" : row.getRootCategoryName();
            names.putIfAbsent(id, name);
            amounts.merge(id, nvl(row.getAmountUsd()), BigDecimal::add);
        }
        BigDecimal total = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            return List.of();
        }
        List<Map.Entry<Long, BigDecimal>> sorted = amounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .toList();
        List<CategorySalesItem> items = new ArrayList<>(sorted.size());
        BigDecimal shareSum = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> entry : sorted) {
            BigDecimal amount = entry.getValue().setScale(2, RoundingMode.HALF_UP);
            BigDecimal share = entry.getValue().multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP);
            shareSum = shareSum.add(share);
            items.add(new CategorySalesItem(entry.getKey(), names.get(entry.getKey()), amount, share));
        }
        // 尾差并入最大项（首项，排序 amount DESC）使 Σ=100.0
        BigDecimal diff = HUNDRED.setScale(1, RoundingMode.HALF_UP).subtract(shareSum);
        if (diff.signum() != 0) {
            CategorySalesItem top = items.get(0);
            items.set(0, new CategorySalesItem(top.categoryId(), top.categoryName(), top.amount(),
                    top.share().add(diff)));
        }
        return items;
    }

    /** MAP-ANA-004 top_products：maxItems=6 由 SQL LIMIT 保证；快照 img 空串 → image_url=null */
    public static List<TopProductItem> buildTopProducts(List<TopProductRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<TopProductItem> items = new ArrayList<>(rows.size());
        for (TopProductRow row : rows) {
            String imageUrl = row.getImg() == null || row.getImg().isBlank() ? null : row.getImg();
            items.add(new TopProductItem(
                    row.getProductId() == null ? 0L : row.getProductId(),
                    row.getProductName(), imageUrl,
                    row.getSales() == null ? 0L : row.getSales(),
                    nvl(row.getAmountUsd()).setScale(2, RoundingMode.HALF_UP)));
        }
        return items;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
