package com.dreamy.analytics.domain.dashboard.service;

import com.dreamy.analytics.dto.AnalyticsDtos.CategorySalesItem;
import com.dreamy.analytics.dto.AnalyticsDtos.Kpis;
import com.dreamy.analytics.dto.AnalyticsDtos.TopProductItem;
import com.dreamy.analytics.dto.AnalyticsDtos.TrendSeries;
import com.dreamy.analytics.repository.readmodel.CategorySalesRow;
import com.dreamy.analytics.repository.readmodel.DailyGmvRow;
import com.dreamy.analytics.repository.readmodel.TopProductRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-ANA-003/004/005/006：聚合纯映射（MAP-ANA-001~004）。
 */
class AnalyticsAssemblerTest {

    @Test
    @DisplayName("TC-ANA-003 KPI 派生：分母 0 → avg/refund_rate=0；refund_rate=3/130 → 2.3（1 位小数）")
    void kpiDerivation() {
        Kpis zero = AnalyticsAssembler.buildKpis(BigDecimal.ZERO, 0, 0);
        assertThat(zero.avgOrderValue()).isEqualByComparingTo("0.00");
        assertThat(zero.refundRate()).isEqualByComparingTo("0.0");

        Kpis kpis = AnalyticsAssembler.buildKpis(new BigDecimal("26000.005"), 130, 3);
        assertThat(kpis.gmvMonth()).isEqualByComparingTo("26000.01"); // HALF_UP 2 位
        assertThat(kpis.avgOrderValue()).isEqualByComparingTo("200.00");
        assertThat(kpis.refundRate()).isEqualByComparingTo("2.3");    // 3/130*100=2.307→2.3
    }

    @Test
    @DisplayName("TC-ANA-004 趋势补零：30 桶仅 3 天有单 → 30 项、缺日 0、label M-D")
    void trendFillZero() {
        LocalDate firstDay = LocalDate.of(2026, 5, 12);
        TrendSeries series = AnalyticsAssembler.fillTrend(firstDay, 30, List.of(
                row(LocalDate.of(2026, 5, 29), "120.00"),
                row(LocalDate.of(2026, 6, 1), "80.00"),
                row(LocalDate.of(2026, 6, 10), "40.00")));
        assertThat(series.labels()).hasSize(30);
        assertThat(series.values()).hasSize(30);
        // label 格式 M-D（无前导零）
        assertThat(series.labels().get(0)).isEqualTo("5-12");
        assertThat(series.labels().get(17)).isEqualTo("5-29");
        assertThat(series.labels().get(20)).isEqualTo("6-1");
        // 有单日落位，缺日补零
        assertThat(series.values().get(17)).isEqualByComparingTo("120.00");
        assertThat(series.values().get(20)).isEqualByComparingTo("80.00");
        assertThat(series.values().get(0)).isEqualByComparingTo("0.00");
        BigDecimal sum = series.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("240.00");
    }

    @Test
    @DisplayName("TC-ANA-005 share 归一：1 位小数 Σ=100.0（尾差并最大项）；总额 0 → 空数组；断链行落 Other")
    void categoryShareNormalization() {
        List<CategorySalesItem> items = AnalyticsAssembler.buildCategorySales(List.of(
                categoryRow(1L, "Wedding Dresses", "52.34"),
                categoryRow(2L, "Bridesmaids", "31.33"),
                categoryRow(3L, "Accessories", "16.33")));
        assertThat(items).hasSize(3);
        BigDecimal sum = items.stream().map(CategorySalesItem::share).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.0");
        // 排序 amount DESC
        assertThat(items.get(0).categoryName()).isEqualTo("Wedding Dresses");

        // 总额 0 → 空数组
        assertThat(AnalyticsAssembler.buildCategorySales(List.of(categoryRow(1L, "X", "0")))).isEmpty();
        assertThat(AnalyticsAssembler.buildCategorySales(List.of())).isEmpty();

        // 溯根断链行（root NULL）落 {0, Other} 桶
        List<CategorySalesItem> withOrphan = AnalyticsAssembler.buildCategorySales(List.of(
                categoryRow(1L, "Wedding Dresses", "90.00"),
                categoryRow(null, null, "10.00")));
        assertThat(withOrphan).hasSize(2);
        CategorySalesItem other = withOrphan.get(1);
        assertThat(other.categoryId()).isZero();
        assertThat(other.categoryName()).isEqualTo("Other");
        assertThat(other.share()).isEqualByComparingTo("10.0");
    }

    @Test
    @DisplayName("TC-ANA-006 top_products 组装：快照列映射；img 空串 → image_url=null")
    void topProductsMapping() {
        TopProductRow row = new TopProductRow();
        row.setProductId(7L);
        row.setProductName("Aurora Gown");
        row.setImg("");
        row.setSales(12L);
        row.setAmountUsd(new BigDecimal("3588.004"));
        List<TopProductItem> items = AnalyticsAssembler.buildTopProducts(List.of(row));
        assertThat(items).hasSize(1);
        assertThat(items.get(0).productId()).isEqualTo(7L);
        assertThat(items.get(0).name()).isEqualTo("Aurora Gown");
        assertThat(items.get(0).imageUrl()).isNull();
        assertThat(items.get(0).sales()).isEqualTo(12L);
        assertThat(items.get(0).amount()).isEqualByComparingTo("3588.00");
        assertThat(AnalyticsAssembler.buildTopProducts(List.of())).isEmpty();
    }

    private DailyGmvRow row(LocalDate day, String gmv) {
        DailyGmvRow row = new DailyGmvRow();
        row.setDay(day);
        row.setGmvUsd(new BigDecimal(gmv));
        return row;
    }

    private CategorySalesRow categoryRow(Long id, String name, String amount) {
        CategorySalesRow row = new CategorySalesRow();
        row.setRootCategoryId(id);
        row.setRootCategoryName(name);
        row.setAmountUsd(new BigDecimal(amount));
        return row;
    }
}
