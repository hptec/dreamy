package com.dreamy.domain.refund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.enums.RefundStatus;
import com.dreamy.domain.refund.consts.RefundDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 表 refund（退款工单——决策 24/31；returnTrackingNo 退货物流单号登记字段，无 RMA 节点）。
 * 并发双审防护：casApprove/casReject 条件更新 WHERE status='pending'（RM-TRD-054/055，409604）。
 * reject_reason 独立列（MAP-TRD-008：reason 保留申请原文，回执邮件取 reject_reason）。
 * L2 TRACE: trading-data-detail §9 DDL-7 / IDX-TRD-010~012 / TASK-020/041。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "refund", comment = "退款工单（决策24/31）", indexes = {
        @Index(name = "uk_refund_no", columns = {"refund_no"}, unique = true, local = false),
        @Index(name = "idx_refund_order_status", columns = {"order_id", "status"}, unique = false, local = false),
        @Index(name = "idx_refund_status_applied", columns = {"status", "applied_at"}, unique = false, local = false)
})
@TableName(value = "refund", autoResultMap = true)
public class Refund extends LongAuditableEntity {

    @Column(name = RefundDBConst.REFUND_NO, definition = "varchar(20) NOT NULL COMMENT 'RFD-YYYYMMDD-NNNN'")
    private String refundNo;

    @Column(name = RefundDBConst.ORDER_ID, definition = "bigint NOT NULL")
    private Long orderId;

    @Column(name = RefundDBConst.CUSTOMER_ID, definition = "bigint NOT NULL")
    private Long customerId;

    @Column(name = RefundDBConst.AMOUNT, definition = "decimal(12,2) NOT NULL COMMENT '<= orders.total_amount 含礼品包装费（决策28）'")
    private BigDecimal amount;

    @Column(name = RefundDBConst.CURRENCY, definition = "char(3) NOT NULL COMMENT '原币种原金额退款（决策14）'")
    private String currency;

    @Column(name = RefundDBConst.REASON, definition = "varchar(255) NULL COMMENT '申请原因'")
    private String reason;

    @Column(name = RefundDBConst.REJECT_REASON, definition = "varchar(255) NULL COMMENT '拒绝原因（回执邮件与消费端）'")
    private String rejectReason;

    @Column(name = RefundDBConst.STATUS, definition = "varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected'")
    private RefundStatus status;

    @Column(name = RefundDBConst.STRIPE_REFUND_ID, definition = "varchar(64) NULL COMMENT '审核通过后写入'")
    private String stripeRefundId;

    @Column(name = RefundDBConst.RETURN_TRACKING_NO, definition = "varchar(64) NULL COMMENT '退货物流单号登记（决策31，无 RMA 节点）'")
    private String returnTrackingNo;

    @Column(name = RefundDBConst.APPLIED_AT, definition = "datetime(3) NOT NULL")
    private LocalDateTime appliedAt;
}
