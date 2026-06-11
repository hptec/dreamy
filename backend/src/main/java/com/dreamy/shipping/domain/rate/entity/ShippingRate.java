package com.dreamy.shipping.domain.rate.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.shipping.domain.rate.consts.ShippingRateDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 国际邮费分区运费规则行（F-036）。zone 唯一（DEC-SHP-1 规范化后存储，
 * utf8mb4_0900_ai_ci 排序规则天然忽略大小写比较——IDX-SHP-001 为 409901 权威闸）。
 * 费用 NULL 语义见 DEC-SHP-3：threshold NULL→恒收 fee_under；fee NULL→计费按 0.00。
 * L2 TRACE: shipping-data-detail §8.1 DDL-2 / CV-SHP-003·004。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = ShippingRateDBConst.TABLE, comment = "国际邮费分区运费规则（结算报价数据源）", indexes = {
        @Index(name = "uk_shipping_rate_zone", columns = {ShippingRateDBConst.ZONE}, unique = true)
})
@TableName(value = ShippingRateDBConst.TABLE, autoResultMap = true)
public class ShippingRate extends LongAuditableEntity {

    @Column(name = ShippingRateDBConst.ZONE,
            definition = "varchar(128) NOT NULL COMMENT '规则行标识（唯一）：<地理区域> 或 <地理区域> / <承运商名>（F-036）'")
    private String zone;

    @Column(name = ShippingRateDBConst.FEE_UNDER,
            definition = "decimal(10,2) NULL COMMENT '基础邮费 USD（subtotal<threshold；NULL 计费按 0）'")
    private BigDecimal feeUnder;

    @Column(name = ShippingRateDBConst.FEE_OVER,
            definition = "decimal(10,2) NULL COMMENT '满额邮费 USD（subtotal>=threshold；0 即包邮；NULL 计费按 0）'")
    private BigDecimal feeOver;

    @Column(name = ShippingRateDBConst.THRESHOLD,
            definition = "decimal(10,2) NULL COMMENT '满额门槛 USD（NULL=无满额档恒收 fee_under，DEC-SHP-3）'")
    private BigDecimal threshold;
}
