package com.dreamy.support;

import com.dreamy.enums.CouponStatus;
import com.dreamy.enums.FlashSaleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 时间窗状态翻转纯函数单元测试（SCHED-MKT-01 内核）。
 * L2 TRACE: TC-MKT-011（now 边界 =start_at、=end_at、end-72h 判定）/ TC-MKT-023（券三段链）/ DEC-MKT-3。
 */
class PromoWindowTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 10, 12, 0);
    private static final Duration H72 = Duration.ofHours(72);

    @Test
    @DisplayName("TC-MKT-011 [P0]: coupon scheduled→active 边界——now=start_at 即翻转；start_at>now 不动")
    void couponScheduledToActive() {
        assertThat(PromoWindow.couponTarget(CouponStatus.SCHEDULED, NOW, NOW.plusDays(30), NOW, H72))
                .isEqualTo(CouponStatus.ACTIVE);
        assertThat(PromoWindow.couponTarget(CouponStatus.SCHEDULED, NOW.plusMinutes(1), NOW.plusDays(30), NOW, H72))
                .isNull();
        // 无 end_at：scheduled→active（长期券）
        assertThat(PromoWindow.couponTarget(CouponStatus.SCHEDULED, NOW.minusDays(1), null, NOW, H72))
                .isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    @DisplayName("TC-MKT-011 [P0]: coupon active→expiring 边界——end-now=72h 恰命中阈值（≤）；>72h 不动")
    void couponActiveToExpiring() {
        assertThat(PromoWindow.couponTarget(CouponStatus.ACTIVE, NOW.minusDays(1), NOW.plusHours(72), NOW, H72))
                .isEqualTo(CouponStatus.EXPIRING);
        assertThat(PromoWindow.couponTarget(CouponStatus.ACTIVE, NOW.minusDays(1),
                NOW.plusHours(72).plusMinutes(1), NOW, H72)).isNull();
    }

    @Test
    @DisplayName("TC-MKT-011 [P0]: coupon expired 边界——now=end_at 不过期（end_at<now 严格）；过 end 即 expired")
    void couponToExpired() {
        assertThat(PromoWindow.couponTarget(CouponStatus.ACTIVE, NOW.minusDays(2), NOW, NOW, H72))
                .isEqualTo(CouponStatus.EXPIRING);
        assertThat(PromoWindow.couponTarget(CouponStatus.ACTIVE, NOW.minusDays(2), NOW.minusMinutes(1), NOW, H72))
                .isEqualTo(CouponStatus.EXPIRED);
        assertThat(PromoWindow.couponTarget(CouponStatus.EXPIRING, NOW.minusDays(2), NOW.minusMinutes(1), NOW, H72))
                .isEqualTo(CouponStatus.EXPIRED);
        // draft/expired 恒不动
        assertThat(PromoWindow.couponTarget(CouponStatus.DRAFT, NOW.minusDays(2), NOW.minusDays(1), NOW, H72))
                .isNull();
        assertThat(PromoWindow.couponTarget(CouponStatus.EXPIRED, NOW.minusDays(2), NOW.minusDays(1), NOW, H72))
                .isNull();
    }

    @Test
    @DisplayName("TC-MKT-023 [P0]: 同 tick 链式推进——scheduled 越过 start 且窗口已过 → 直接 expired")
    void couponChainedPromotion() {
        assertThat(PromoWindow.couponTarget(CouponStatus.SCHEDULED, NOW.minusDays(5), NOW.minusDays(1), NOW, H72))
                .isEqualTo(CouponStatus.EXPIRED);
        assertThat(PromoWindow.couponTarget(CouponStatus.SCHEDULED, NOW.minusDays(5), NOW.plusHours(10), NOW, H72))
                .isEqualTo(CouponStatus.EXPIRING);
    }

    @Test
    @DisplayName("TC-MKT-011 [P0]: flash scheduled→active（start≤now）/ active→ended（end<now 严格，s-761）")
    void flashFlip() {
        assertThat(PromoWindow.flashTarget(FlashSaleStatus.SCHEDULED, NOW, NOW.plusDays(1), NOW))
                .isEqualTo(FlashSaleStatus.ACTIVE);
        assertThat(PromoWindow.flashTarget(FlashSaleStatus.SCHEDULED, NOW.plusMinutes(1), NOW.plusDays(1), NOW))
                .isNull();
        // now=end_at 不下线；过 end 即 ended
        assertThat(PromoWindow.flashTarget(FlashSaleStatus.ACTIVE, NOW.minusDays(1), NOW, NOW)).isNull();
        assertThat(PromoWindow.flashTarget(FlashSaleStatus.ACTIVE, NOW.minusDays(1), NOW.minusMinutes(1), NOW))
                .isEqualTo(FlashSaleStatus.ENDED);
        // scheduled 越过 start 且窗口已过 → 直接 ended
        assertThat(PromoWindow.flashTarget(FlashSaleStatus.SCHEDULED, NOW.minusDays(2), NOW.minusDays(1), NOW))
                .isEqualTo(FlashSaleStatus.ENDED);
        // draft/ended 恒不动
        assertThat(PromoWindow.flashTarget(FlashSaleStatus.DRAFT, NOW.minusDays(2), NOW.minusDays(1), NOW)).isNull();
        assertThat(PromoWindow.flashTarget(FlashSaleStatus.ENDED, NOW.minusDays(2), NOW.minusDays(1), NOW)).isNull();
    }
}
