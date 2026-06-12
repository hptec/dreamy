package com.dreamy.domain.payment.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.enums.PaymentStatus;
import com.dreamy.domain.payment.entity.Payment;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 支付单仓储（RM-TRD-040~044）。payment_lifecycle guard 经条件更新 CAS。
 * L2 TRACE: trading-data-detail §1 PaymentRepository / IDX-TRD-008/009。
 */
@Repository
public class PaymentRepository {

    private final PaymentMapper mapper;

    public PaymentRepository(PaymentMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-TRD-040 insert */
    public void insert(Payment payment) {
        mapper.insert(payment);
    }

    /** RM-TRD-041 findByOrderId（一单一活跃支付单） */
    public Payment findByOrderId(Long orderId) {
        List<Payment> rows = mapper.selectList(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId)
                .orderByDesc(Payment::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 订单详情批量联取（防 N+1） */
    public List<Payment> listByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<Payment>().in(Payment::getOrderId, orderIds));
    }

    /** RM-TRD-042 findByPaymentIntentId（uk_payment_intent，webhook 热路径） */
    public Payment findByPaymentIntentId(String paymentIntentId) {
        return mapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getPaymentIntentId, paymentIntentId));
    }

    /** RM-TRD-043 payment_lifecycle guard 条件更新（fromStatuses 任一 → to） */
    public int casUpdateStatus(Long id, List<PaymentStatus> fromStatuses, PaymentStatus to,
                               LocalDateTime paidAt, String cardSummary) {
        LambdaUpdateWrapper<Payment> uw = new LambdaUpdateWrapper<Payment>()
                .eq(Payment::getId, id)
                .in(Payment::getStatus, fromStatuses)
                .set(Payment::getStatus, to);
        if (paidAt != null) {
            uw.set(Payment::getPaidAt, paidAt);
        }
        if (cardSummary != null) {
            uw.set(Payment::getCardSummary, cardSummary);
        }
        return mapper.update(null, uw);
    }

    public int casUpdateStatus(Long id, PaymentStatus from, PaymentStatus to) {
        return casUpdateStatus(id, Arrays.asList(from), to, null, null);
    }

    /** RM-TRD-044 retryOrderPayment 重建凭据（status 复位 created） */
    public void rebindPaymentIntent(Long id, String newPaymentIntentId) {
        mapper.update(null, new LambdaUpdateWrapper<Payment>()
                .eq(Payment::getId, id)
                .set(Payment::getPaymentIntentId, newPaymentIntentId)
                .set(Payment::getStatus, PaymentStatus.CREATED));
    }
}
