package com.dreamy.domain.shippingrate.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.shippingrate.consts.ShippingOptionDBConst;
import com.dreamy.enums.ShippingServiceLevel;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 表 shipping_option（order-flow-complete §2.3）：zone × carrier_code × service_level 运费选项，
 * 含运输天数区间（预计送达 = 制作周期 + transit_days）。uk (zone, carrier_code, service_level)。
 * shipping_rate 表原样保留（expand/contract 的 expand 阶段），启动迁移器在本表为空时按旧表生成 STANDARD 选项。
 * carrier_code='ANY' 表示适用于任意启用承运商（兜底行）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = ShippingOptionDBConst.TABLE, comment = "运费选项（分区×承运商×服务等级）", indexes = {
        @Index(name = "uk_shipping_option", columns = {ShippingOptionDBConst.ZONE, ShippingOptionDBConst.CARRIER_CODE,
                ShippingOptionDBConst.SERVICE_LEVEL}, unique = true, local = false)
})
@TableName(value = ShippingOptionDBConst.TABLE, autoResultMap = true)
public class ShippingOption extends LongAuditableEntity {

    @Column(name = ShippingOptionDBConst.ZONE,
            definition = "varchar(32) NOT NULL COMMENT 'NORTH_AMERICA/EUROPE/UK/OCEANIA/ASIA/LATAM/MEA/REST'")
    private String zone;

    @Column(name = ShippingOptionDBConst.CARRIER_CODE,
            definition = "varchar(32) NOT NULL COMMENT 'carrier.code；ANY=任意启用承运商'")
    private String carrierCode;

    @Column(name = ShippingOptionDBConst.SERVICE_LEVEL,
            definition = "tinyint NOT NULL DEFAULT 1 COMMENT '1=STANDARD 2=EXPRESS'")
    private ShippingServiceLevel serviceLevel;

    @Column(name = ShippingOptionDBConst.FEE_UNDER,
            definition = "decimal(12,2) NULL COMMENT '基础邮费 USD（subtotal<threshold；NULL 计 0）'")
    private BigDecimal feeUnder;

    @Column(name = ShippingOptionDBConst.FEE_OVER,
            definition = "decimal(12,2) NULL COMMENT '满额邮费 USD（subtotal>=threshold；NULL 计 0）'")
    private BigDecimal feeOver;

    @Column(name = ShippingOptionDBConst.THRESHOLD,
            definition = "decimal(12,2) NULL COMMENT '满额门槛 USD（NULL=恒收 fee_under）'")
    private BigDecimal threshold;

    @Column(name = ShippingOptionDBConst.TRANSIT_DAYS_MIN, definition = "int NOT NULL DEFAULT 5 COMMENT '运输天数下限'")
    private Integer transitDaysMin;

    @Column(name = ShippingOptionDBConst.TRANSIT_DAYS_MAX, definition = "int NOT NULL DEFAULT 10 COMMENT '运输天数上限'")
    private Integer transitDaysMax;

    @Column(name = ShippingOptionDBConst.ENABLED, definition = "tinyint(1) NOT NULL DEFAULT 1")
    private Boolean enabled;
}
