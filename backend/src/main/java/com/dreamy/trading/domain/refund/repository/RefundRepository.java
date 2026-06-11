package com.dreamy.trading.domain.refund.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.trading.domain.enums.RefundStatus;
import com.dreamy.trading.domain.refund.entity.Refund;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 退款工单仓储（RM-TRD-050~057）。
 * L2 TRACE: trading-data-detail §1 RefundRepository / IDX-TRD-010~012。
 */
@Repository
public class RefundRepository {

    private final RefundMapper mapper;

    public RefundRepository(RefundMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-TRD-050 insert（uk_refund_no 兜底，冲突向上抛） */
    public void insert(Refund refund) {
        mapper.insert(refund);
    }

    /** RM-TRD-051 findById（404605） */
    public Refund findById(Long id) {
        return mapper.selectById(id);
    }

    /** RM-TRD-052 listByOrderId（applied_at DESC） */
    public List<Refund> listByOrderId(Long orderId) {
        return mapper.selectList(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderId, orderId)
                .orderByDesc(Refund::getAppliedAt)
                .orderByDesc(Refund::getId));
    }

    /** RM-TRD-053 进行中工单判定（idx_refund_order_status，409605） */
    public boolean existsPendingByOrderId(Long orderId) {
        return mapper.selectCount(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderId, orderId)
                .eq(Refund::getStatus, RefundStatus.PENDING)) > 0;
    }

    /** RM-TRD-054 casApprove：WHERE status='pending'（409604 并发双审防护）；COALESCE 登记单号 */
    public int casApprove(Long id, String returnTrackingNo) {
        LambdaUpdateWrapper<Refund> uw = new LambdaUpdateWrapper<Refund>()
                .eq(Refund::getId, id)
                .eq(Refund::getStatus, RefundStatus.PENDING)
                .set(Refund::getStatus, RefundStatus.APPROVED);
        if (returnTrackingNo != null) {
            uw.set(Refund::getReturnTrackingNo, returnTrackingNo);
        }
        return mapper.update(null, uw);
    }

    /** RM-TRD-055 casReject：WHERE status='pending'；拒绝理由入独立列 reject_reason（MAP-TRD-008） */
    public int casReject(Long id, String rejectReason) {
        return mapper.update(null, new LambdaUpdateWrapper<Refund>()
                .eq(Refund::getId, id)
                .eq(Refund::getStatus, RefundStatus.PENDING)
                .set(Refund::getStatus, RefundStatus.REJECTED)
                .set(Refund::getRejectReason, rejectReason));
    }

    /** 审核通过后写入 stripe_refund_id（TX-TRD-003 ③） */
    public void updateStripeRefundId(Long id, String stripeRefundId) {
        mapper.update(null, new LambdaUpdateWrapper<Refund>()
                .eq(Refund::getId, id)
                .set(Refund::getStripeRefundId, stripeRefundId));
    }

    /** RM-TRD-056 patchAdminRefund 登记退货单号 */
    public void updateReturnTrackingNo(Long id, String trackingNo) {
        mapper.update(null, new LambdaUpdateWrapper<Refund>()
                .eq(Refund::getId, id)
                .set(Refund::getReturnTrackingNo, trackingNo));
    }

    /**
     * RM-TRD-057 后台分页（applied_at DESC；搜索条件：refund_no LIKE / order_ids IN（order_no 命中）/
     * customer_ids IN（identity 邮箱命中）——跨域 join 拆分由调用方先解析 id 集合）。
     */
    public Page<Refund> pageByAdminFilter(RefundStatus status, String keyword, List<Long> orderIds,
                                          List<Long> customerIds, int page, int pageSize) {
        LambdaQueryWrapper<Refund> qw = new LambdaQueryWrapper<Refund>()
                .orderByDesc(Refund::getAppliedAt)
                .orderByDesc(Refund::getId);
        if (status != null) {
            qw.eq(Refund::getStatus, status);
        }
        if (keyword != null) {
            boolean hasOrders = orderIds != null && !orderIds.isEmpty();
            boolean hasCustomers = customerIds != null && !customerIds.isEmpty();
            qw.and(w -> {
                w.like(Refund::getRefundNo, keyword);
                if (hasOrders) {
                    w.or().in(Refund::getOrderId, orderIds);
                }
                if (hasCustomers) {
                    w.or().in(Refund::getCustomerId, customerIds);
                }
            });
        }
        return mapper.selectPage(new Page<>(page, pageSize), qw);
    }

    /** 订单详情批量联取（防 N+1） */
    public List<Refund> listByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<Refund>()
                .in(Refund::getOrderId, orderIds)
                .orderByDesc(Refund::getAppliedAt));
    }

    /** webhook charge.refunded 幂等汇合：是否存在该订单 approved 工单 */
    public boolean existsApprovedByOrderId(Long orderId) {
        return mapper.selectCount(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderId, orderId)
                .eq(Refund::getStatus, RefundStatus.APPROVED)) > 0;
    }
}
