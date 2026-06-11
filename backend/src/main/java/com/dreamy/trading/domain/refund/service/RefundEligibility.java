package com.dreamy.trading.domain.refund.service;

import com.dreamy.trading.domain.enums.OrderStatus;

import java.time.LocalDateTime;

/**
 * 退款资格判定纯函数（决策 24；getStoreOrder.STEP-TRD-03/04 与 applyStoreRefund.STEP-TRD-04 /
 * createAdminRefund.STEP-TRD-03 三处一致口径——TC-TRD-004/005）。
 * - 定制行（custom_size_data 非空）：refundable = (paid_at == null) || now <= paid_at + grace_hours
 *   （边界含等号：now == deadline 可退，now == deadline+1s 不可退）。
 * - 现货行：refundable = status ∈ {paid, shipped}（未发货全额退 / 已发货退货后退审核制）。
 * - 整单：status ∈ {paid, shipped} 且无进行中工单且不含「已投产定制行」。
 */
public final class RefundEligibility {

    private RefundEligibility() {
    }

    /** 定制行投产判定（true=已投产不可退 → 422602） */
    public static boolean customProduced(LocalDateTime paidAt, int graceHours, LocalDateTime now) {
        if (paidAt == null) {
            return false;
        }
        return now.isAfter(graceDeadline(paidAt, graceHours));
    }

    /** 宽限截止时刻（422602 details.grace_deadline） */
    public static LocalDateTime graceDeadline(LocalDateTime paidAt, int graceHours) {
        return paidAt.plusHours(graceHours);
    }

    /** 行级 refundable 派生（getStoreOrder.STEP-TRD-03） */
    public static boolean lineRefundable(boolean customLine, OrderStatus orderStatus,
                                         LocalDateTime paidAt, int graceHours, LocalDateTime now) {
        if (customLine) {
            return !customProduced(paidAt, graceHours, now);
        }
        return orderStatus == OrderStatus.PAID || orderStatus == OrderStatus.SHIPPED;
    }

    /** 整单 refund_eligible 派生（getStoreOrder.STEP-TRD-04） */
    public static boolean orderEligible(OrderStatus status, boolean hasPendingRefund, boolean hasCustomLine,
                                        LocalDateTime paidAt, int graceHours, LocalDateTime now) {
        if (status != OrderStatus.PAID && status != OrderStatus.SHIPPED) {
            return false;
        }
        if (hasPendingRefund) {
            return false;
        }
        return !(hasCustomLine && customProduced(paidAt, graceHours, now));
    }
}
