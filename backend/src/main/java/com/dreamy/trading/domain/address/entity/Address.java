package com.dreamy.trading.domain.address.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.trading.domain.address.consts.AddressDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 address（收货地址簿；订单存 address_snapshot 快照，删除不波及——deleteAddress.STEP-TRD-02）。
 * 不变量：恒至多一个 is_default（TX-TRD-008 clearDefault + insert/update 同事务）。
 * L2 TRACE: trading-data-detail §9 DDL-3 / IDX-TRD-017 / TASK-015。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "address", comment = "收货地址簿（订单存快照）", indexes = {
        @Index(name = "idx_addr_customer", columns = {"customer_id", "is_default"}, unique = false, local = false)
})
@TableName(value = "address", autoResultMap = true)
public class Address extends LongAuditableEntity {

    @Column(name = AddressDBConst.CUSTOMER_ID, definition = "bigint NOT NULL")
    private Long customerId;

    @Column(name = AddressDBConst.RECEIVER, definition = "varchar(64) NOT NULL COMMENT '收件人'")
    private String receiver;

    @Column(name = AddressDBConst.PHONE, definition = "varchar(32) NULL")
    private String phone;

    @Column(name = AddressDBConst.LINE, definition = "varchar(255) NOT NULL COMMENT '街道地址'")
    private String line;

    @Column(name = AddressDBConst.CITY, definition = "varchar(64) NOT NULL")
    private String city;

    @Column(name = AddressDBConst.STATE, definition = "varchar(64) NULL")
    private String state;

    @Column(name = AddressDBConst.ZIP, definition = "varchar(16) NOT NULL")
    private String zip;

    @Column(name = AddressDBConst.COUNTRY, definition = "varchar(64) NOT NULL COMMENT '运费分区映射输入'")
    private String country;

    @Column(name = AddressDBConst.IS_DEFAULT, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '恒至多一个默认（TX-TRD-008）'")
    private Boolean isDefault;
}
