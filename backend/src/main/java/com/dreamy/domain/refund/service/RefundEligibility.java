package com.dreamy.domain.refund.service;

import com.dreamy.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款资格判定纯函数（决策 24；getStoreOrder.STEP-TRD-03/04 与 applyStoreRefund.STEP-TRD-04 /
 * createAdminRefund.STEP-TRD-03 三处一致口径——TC-TRD-004/005）。
 * - 定制行（custom_size_data 非空）：refundable = (paid_at == null) || now <= paid_at + grace_hours
 *   （边界含等号：now == deadline 可退，now == deadline+1s 不可退）。
 * - 现货行：refundable = status ∈ {paid, shipped, delivered}（未发货全额退 / 已发货退货后退审核制）。
 * - 整单：status ∈ {paid, shipped, delivered}（OrderStatus.isPostPaymentActive）且无进行中工单且不含「已投产定制行」
 *   且剩余可退额 > 0（order-flow-complete H：剩余可退额 = total − refunded_amount）。
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
        return orderStatus != null && orderStatus.isPostPaymentActive();
    }

    /** 整单 refund_eligible 派生（getStoreOrder.STEP-TRD-04；兼容旧签名，不考虑累计已退额） */
    public static boolean orderEligible(OrderStatus status, boolean hasPendingRefund, boolean hasCustomLine,
                                        LocalDateTime paidAt, int graceHours, LocalDateTime now) {
        if (status == null || !status.isPostPaymentActive()) {
            return false;
        }
        if (hasPendingRefund) {
            return false;
        }
        return !(hasCustomLine && customProduced(paidAt, graceHours, now));
    }

    /** 整单 refund_eligible 派生（order-flow-complete：追加剩余可退额 > 0） */
    public static boolean orderEligible(OrderStatus status, boolean hasPendingRefund, boolean hasCustomLine,
                                        LocalDateTime paidAt, int graceHours, LocalDateTime now,
                                        BigDecimal totalAmount, BigDecimal refundedAmount) {
        if (!orderEligible(status, hasPendingRefund, hasCustomLine, paidAt, graceHours, now)) {
            return false;
        }
        return remainingRefundable(totalAmount, refundedAmount).signum() > 0;
    }

    /** 剩余可退额 = total_amount − refunded_amount（下限 0） */
    public static BigDecimal remainingRefundable(BigDecimal totalAmount, BigDecimal refundedAmount) {
        BigDecimal total = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        BigDecimal refunded = refundedAmount == null ? BigDecimal.ZERO : refundedAmount;
        BigDecimal remaining = total.subtract(refunded);
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }
}
