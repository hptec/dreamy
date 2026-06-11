package com.dreamy.shipping.domain.carrier.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.shipping.domain.carrier.consts.CarrierDBConst;
import com.dreamy.shipping.domain.enums.CarrierStatus;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 物流承运方（ALIGN-015）。name 为 Order.carrier 快照取值源（不强外键，CP-010）。
 * L2 TRACE: shipping-data-detail §8.1 DDL-1 / IDX-SHP-002 / CV-SHP-001·002·007。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = CarrierDBConst.TABLE, comment = "物流承运方（ALIGN-015）", indexes = {
        @Index(name = "idx_carrier_status", columns = {CarrierDBConst.STATUS})
})
@TableName(value = CarrierDBConst.TABLE, autoResultMap = true)
public class Carrier extends LongAuditableEntity {

    @Column(name = CarrierDBConst.NAME, definition = "varchar(64) NOT NULL COMMENT '承运方名称（Order.carrier 快照取值源）'")
    private String name;

    @Column(name = CarrierDBConst.ZONES, definition = "varchar(255) NULL COMMENT '覆盖区域描述（纯展示）'")
    private String zones;

    @Column(name = CarrierDBConst.LEAD_TIME, definition = "varchar(64) NULL COMMENT '时效描述（进入报价项 lead_time）'")
    private String leadTime;

    @Column(name = CarrierDBConst.STATUS, definition = "varchar(16) NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled/disabled'")
    private CarrierStatus status;
}
