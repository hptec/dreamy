package com.dreamy.trading.domain.order.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.dreamy.trading.domain.enums.OrderStatus;
import com.dreamy.trading.domain.order.consts.OrderDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 表 orders（订单主单，表名规避 MySQL 保留字 ORDER）。
 * 幂等：uk_order_idem(idempotency_key)（IDX-TRD-001 强制，409603）；订单号 Redis 预生成 + uk_order_no 兜底。
 * 金额恒等式（CV-TRD-003）：total_amount = subtotal + shipping_fee + gift_wrap_fee - discount_amount。
 * 状态机推进一律条件更新 CAS（RM-TRD-026，guard 失败 affected=0 → 409602）。
 * L2 TRACE: trading-data-detail §9 DDL-4 / IDX-TRD-001~005 / TASK-017/038。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "orders", comment = "订单主单", indexes = {
        @Index(name = "uk_order_idem", columns = {"idempotency_key"}, unique = true, local = false),
        @Index(name = "uk_order_no", columns = {"order_no"}, unique = true, local = false),
        @Index(name = "idx_order_customer_created", columns = {"customer_id", "created_at"}, unique = false, local = false),
        @Index(name = "idx_order_status_expires", columns = {"status", "expires_at"}, unique = false, local = false),
        @Index(name = "idx_order_status_created", columns = {"status", "created_at"}, unique = false, local = false)
})
@TableName(value = "orders", autoResultMap = true)
public class Order extends LongAuditableEntity {

    @Column(name = OrderDBConst.ORDER_NO, definition = "varchar(20) NOT NULL COMMENT 'DRM-YYYYMMDD-NNNN（预生成）'")
    private String orderNo;

    @Column(name = OrderDBConst.CUSTOMER_ID, definition = "bigint NOT NULL")
    private Long customerId;

    @Column(name = OrderDBConst.STATUS, definition = "varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'order_lifecycle 七态'")
    private OrderStatus status;

    @Column(name = OrderDBConst.CURRENCY, definition = "char(3) NOT NULL COMMENT 'USD/EUR/CAD/AUD/GBP（决策14）'")
    private String currency;

    @Column(name = OrderDBConst.EXCHANGE_RATE, definition = "decimal(12,6) NOT NULL COMMENT '下单锁定 USD→订单币种汇率（决策14）'")
    private BigDecimal exchangeRate;

    @Column(name = OrderDBConst.WEDDING_DATE, definition = "date NULL COMMENT '婚期（交期复核，决策20.6）'")
    private LocalDate weddingDate;

    @Column(name = OrderDBConst.SUBTOTAL, definition = "decimal(12,2) NOT NULL COMMENT '订单币种行小计求和'")
    private BigDecimal subtotal;

    @Column(name = OrderDBConst.SHIPPING_FEE, definition = "decimal(12,2) NOT NULL DEFAULT 0 COMMENT '所选承运商报价快照（F-036）'")
    private BigDecimal shippingFee;

    @Column(name = OrderDBConst.GIFT_WRAP, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '礼品包装（决策28）'")
    private Boolean giftWrap;

    @Column(name = OrderDBConst.GIFT_WRAP_FEE, definition = "decimal(12,2) NOT NULL DEFAULT 0 COMMENT '礼品包装费快照（决策28）'")
    private BigDecimal giftWrapFee;

    @Column(name = OrderDBConst.DISCOUNT_AMOUNT, definition = "decimal(12,2) NOT NULL DEFAULT 0 COMMENT '券减免（订单币种）'")
    private BigDecimal discountAmount;

    @Column(name = OrderDBConst.TOTAL_AMOUNT, definition = "decimal(12,2) NOT NULL COMMENT '= subtotal+shipping_fee+gift_wrap_fee-discount_amount'")
    private BigDecimal totalAmount;

    @Column(name = OrderDBConst.COUPON_ID, definition = "bigint NULL COMMENT '逻辑外键 coupon.id（marketing）'")
    private Long couponId;

    @Column(name = OrderDBConst.PAYMENT_METHOD, definition = "varchar(32) NULL COMMENT 'Stripe/Apple Pay/Google Pay/Klarna/Afterpay（决策25）'")
    private String paymentMethod;

    @Column(name = OrderDBConst.ADDRESS_SNAPSHOT, definition = "json NOT NULL COMMENT '下单地址快照（删地址不波及）'")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> addressSnapshot;

    @Column(name = OrderDBConst.CARRIER, definition = "varchar(64) NULL COMMENT '承运商快照枚举三值（F-036）'")
    private String carrier;

    @Column(name = OrderDBConst.TRACKING_NO, definition = "varchar(64) NULL COMMENT '物流单号（手填，BE-DIM-5）'")
    private String trackingNo;

    @Column(name = OrderDBConst.IDEMPOTENCY_KEY, definition = "varchar(64) NOT NULL COMMENT '客户端 UUID 防重（BE-DIM-4）'")
    private String idempotencyKey;

    @Column(name = OrderDBConst.EXPIRES_AT, definition = "datetime(3) NOT NULL COMMENT 'created_at+30min 超时取消（BE-DIM-4）'")
    private LocalDateTime expiresAt;

    @Column(name = OrderDBConst.PAID_AT, definition = "datetime(3) NULL")
    private LocalDateTime paidAt;

    @Column(name = OrderDBConst.SHIPPED_AT, definition = "datetime(3) NULL")
    private LocalDateTime shippedAt;

    @Column(name = OrderDBConst.COMPLETED_AT, definition = "datetime(3) NULL")
    private LocalDateTime completedAt;
}
