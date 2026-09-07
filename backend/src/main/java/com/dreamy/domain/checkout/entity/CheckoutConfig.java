package com.dreamy.domain.checkout.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.checkout.consts.CheckoutConfigDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 表 checkout_config（结算配置单例——决策 24/28；id=1，种子初始化 gift_wrap_fee_usd=15.00 / grace=24）。
 * CV-TRD-010：gift_wrap_fee_usd ≥0；custom_refund_grace_hours ∈ [1,168]。
 * L2 TRACE: trading-data-detail §9 DDL-11 / RM-TRD-090/091。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "checkout_config", comment = "结算配置单例（决策24/28）")
@TableName(value = "checkout_config", autoResultMap = true)
public class CheckoutConfig extends LongAuditableEntity {

    /** 单例行主键固定值 */
    public static final long SINGLETON_ID = 1L;

    @Column(name = CheckoutConfigDBConst.GIFT_WRAP_FEE_USD,
            definition = "decimal(12,2) NOT NULL DEFAULT 15.00 COMMENT '礼品包装固定费 USD 基准（决策28）'")
    private BigDecimal giftWrapFeeUsd;

    @Column(name = CheckoutConfigDBConst.CUSTOM_REFUND_GRACE_HOURS,
            definition = "int NOT NULL DEFAULT 24 COMMENT '定制款退款宽限期小时 1..168（决策24）'")
    private Integer customRefundGraceHours;

    @Column(name = CheckoutConfigDBConst.AUTO_COMPLETE_DAYS,
            definition = "int NOT NULL DEFAULT 7 COMMENT '签收后自动完成天数 1..60'")
    private Integer autoCompleteDays;

    @Column(name = CheckoutConfigDBConst.AUTO_DELIVER_DAYS,
            definition = "int NOT NULL DEFAULT 30 COMMENT '发货后无签收自动视为送达天数 1..120'")
    private Integer autoDeliverDays;

    @Column(name = CheckoutConfigDBConst.PENDING_TIMEOUT_MINUTES,
            definition = "int NOT NULL DEFAULT 30 COMMENT '待支付超时分钟 5..1440'")
    private Integer pendingTimeoutMinutes;

    @Column(name = CheckoutConfigDBConst.EXCHANGE_RATE_SPREAD_SCALED,
            definition = "int NOT NULL DEFAULT 0 COMMENT '汇率加价率 ×10000（100=1%）0..2000'")
    private Integer exchangeRateSpreadScaled;

    @Column(name = CheckoutConfigDBConst.PRODUCTION_DAYS_DEFAULT,
            definition = "int NOT NULL DEFAULT 21 COMMENT '默认制作周期天数（商品未配置时）1..180'")
    private Integer productionDaysDefault;
}
