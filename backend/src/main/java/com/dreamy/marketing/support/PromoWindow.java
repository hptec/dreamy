package com.dreamy.marketing.support;

import com.dreamy.marketing.domain.enums.CouponStatus;
import com.dreamy.marketing.domain.enums.FlashSaleStatus;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 营销时间窗状态翻转判定（纯函数，SCHED-MKT-01 / RM-MKT-109/126 决策内核）。
 * 口径（DEC-MKT-3 / FLOW-P15）：
 * - coupon：scheduled→active（start_at≤now）；active→expiring（end_at−now≤阈值）；active|expiring→expired（end_at<now）；
 *   同一 tick 内链式推进（scheduled 越过 start 后直接落到窗口对应终点态）。
 * - flash：scheduled→active（start_at≤now）；active→ended（end_at<now，s-761 自动下线）。
 * L2 TRACE: RM-MKT-109 / RM-MKT-126 / TC-MKT-011 / TC-MKT-023。
 */
public final class PromoWindow {

    private PromoWindow() {
    }

    /** coupon 目标状态；无翻转返回 null（draft/expired 恒不动） */
    public static CouponStatus couponTarget(CouponStatus status, LocalDateTime startAt, LocalDateTime endAt,
                                            LocalDateTime now, Duration expiringThreshold) {
        if (status == null || now == null) {
            return null;
        }
        switch (status) {
            case SCHEDULED -> {
                if (startAt != null && !startAt.isAfter(now)) {
                    CouponStatus target = activeWindowTarget(endAt, now, expiringThreshold);
                    return target == null ? CouponStatus.ACTIVE : target;
                }
                return null;
            }
            case ACTIVE -> {
                return activeWindowTarget(endAt, now, expiringThreshold);
            }
            case EXPIRING -> {
                if (endAt != null && now.isAfter(endAt)) {
                    return CouponStatus.EXPIRED;
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    /** active 视角的窗口终点态：过期→EXPIRED；临期（end−now≤阈值）→EXPIRING；否则 null（保持 active） */
    private static CouponStatus activeWindowTarget(LocalDateTime endAt, LocalDateTime now, Duration threshold) {
        if (endAt == null) {
            return null;
        }
        if (now.isAfter(endAt)) {
            return CouponStatus.EXPIRED;
        }
        if (threshold != null && Duration.between(now, endAt).compareTo(threshold) <= 0) {
            return CouponStatus.EXPIRING;
        }
        return null;
    }

    /** flash 目标状态；无翻转返回 null（draft/ended 恒不动） */
    public static FlashSaleStatus flashTarget(FlashSaleStatus status, LocalDateTime startAt, LocalDateTime endAt,
                                              LocalDateTime now) {
        if (status == null || now == null) {
            return null;
        }
        switch (status) {
            case SCHEDULED -> {
                if (startAt != null && !startAt.isAfter(now)) {
                    return (endAt != null && now.isAfter(endAt)) ? FlashSaleStatus.ENDED : FlashSaleStatus.ACTIVE;
                }
                return null;
            }
            case ACTIVE -> {
                if (endAt != null && now.isAfter(endAt)) {
                    return FlashSaleStatus.ENDED;
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }
}
