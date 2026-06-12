package com.dreamy.domain.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.enums.PaymentStatus;
import com.dreamy.domain.payment.consts.PaymentDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 表 payment（支付单，Stripe PaymentIntent，webhook 驱动状态——TASK-019/039）。
 * 一单一活跃支付单（RM-TRD-041），重建 PI 原地 UPDATE（RM-TRD-044）。
 * 脱敏（MAP-TRD-006）：payment_intent_id 可落库（非敏感引用）；client_secret 永不落库。
 * L2 TRACE: trading-data-detail §9 DDL-6 / IDX-TRD-008/009。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "payment", comment = "支付单", indexes = {
        @Index(name = "uk_payment_intent", columns = {"payment_intent_id"}, unique = true, local = false),
        @Index(name = "idx_payment_order", columns = {"order_id"}, unique = false, local = false)
})
@TableName(value = "payment", autoResultMap = true)
public class Payment extends LongAuditableEntity {

    @Column(name = PaymentDBConst.ORDER_ID, definition = "bigint NOT NULL")
    private Long orderId;

    @Column(name = PaymentDBConst.PROVIDER, definition = "varchar(16) NOT NULL DEFAULT 'stripe'")
    private String provider;

    @Column(name = PaymentDBConst.PAYMENT_INTENT_ID, definition = "varchar(64) NULL COMMENT '非敏感引用；client_secret 永不落库'")
    private String paymentIntentId;

    @Column(name = PaymentDBConst.AMOUNT, definition = "decimal(12,2) NOT NULL COMMENT '订单币种'")
    private BigDecimal amount;

    @Column(name = PaymentDBConst.CURRENCY, definition = "char(3) NOT NULL")
    private String currency;

    @Column(name = PaymentDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT 'payment_lifecycle 五态：1=已创建 2=处理中 3=支付成功 4=支付失败 5=已退款'")
    private PaymentStatus status;

    @Column(name = PaymentDBConst.CARD_SUMMARY, definition = "varchar(64) NULL COMMENT '如 Stripe · Visa ···4242'")
    private String cardSummary;

    @Column(name = PaymentDBConst.PAID_AT, definition = "datetime(3) NULL")
    private LocalDateTime paidAt;
}
