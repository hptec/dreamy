package com.dreamy.domain.tax.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.tax.consts.TaxDestinationPolicyDBConst;
import com.dreamy.enums.Incoterm;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 tax_destination_policy（order-flow-complete §2.2）：目的国一国一策 DDP/DDU + 关税提示。
 * 无记录默认 DDU + 提示。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = TaxDestinationPolicyDBConst.TABLE, comment = "目的国税费承担政策（DDP/DDU）", indexes = {
        @Index(name = "uk_tax_policy_country", columns = {TaxDestinationPolicyDBConst.COUNTRY_CODE}, unique = true,
                local = false)
})
@TableName(value = TaxDestinationPolicyDBConst.TABLE, autoResultMap = true)
public class TaxDestinationPolicy extends LongAuditableEntity {

    @Column(name = TaxDestinationPolicyDBConst.COUNTRY_CODE, definition = "char(2) NOT NULL")
    private String countryCode;

    @Column(name = TaxDestinationPolicyDBConst.INCOTERM, definition = "tinyint NOT NULL DEFAULT 2 COMMENT '1=DDP 2=DDU'")
    private Incoterm incoterm;

    @Column(name = TaxDestinationPolicyDBConst.DUTIES_NOTICE, definition = "tinyint(1) NOT NULL DEFAULT 1 COMMENT '结算页是否显示关税提示'")
    private Boolean dutiesNotice;

    @Column(name = TaxDestinationPolicyDBConst.NOTICE_TEXT, definition = "varchar(255) NULL")
    private String noticeText;
}
