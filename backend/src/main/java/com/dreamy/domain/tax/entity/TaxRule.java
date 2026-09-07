package com.dreamy.domain.tax.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.tax.consts.TaxRuleDBConst;
import com.dreamy.enums.TaxType;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 表 tax_rule（order-flow-complete §2.2）：country_code × region × tax_type 税率规则。
 * region 空串 = 国家级（避免 NULL 唯一键失效）；uk (country_code, region, tax_type)。
 * 匹配：同 tax_type 下 (country, region 精确) 覆盖 (country, '')；仅 enabled 且 today ∈ [effective_from, effective_to]。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = TaxRuleDBConst.TABLE, comment = "税率规则（国家/州 × 税种）", indexes = {
        @Index(name = "uk_tax_rule_key", columns = {TaxRuleDBConst.COUNTRY_CODE, TaxRuleDBConst.REGION,
                TaxRuleDBConst.TAX_TYPE}, unique = true, local = false)
})
@TableName(value = TaxRuleDBConst.TABLE, autoResultMap = true)
public class TaxRule extends LongAuditableEntity {

    @Column(name = TaxRuleDBConst.COUNTRY_CODE, definition = "char(2) NOT NULL COMMENT 'ISO-3166-1 alpha-2'")
    private String countryCode;

    @Column(name = TaxRuleDBConst.REGION, definition = "varchar(8) NOT NULL DEFAULT '' COMMENT '州/省码；空串=国家级'")
    private String region;

    @Column(name = TaxRuleDBConst.TAX_TYPE, definition = "tinyint NOT NULL COMMENT '1=VAT 2=GST 3=SALES_TAX 4=DUTY'")
    private TaxType taxType;

    @Column(name = TaxRuleDBConst.RATE_SCALED, definition = "int NOT NULL COMMENT '税率×10000（10000=100%）'")
    private Integer rateScaled;

    @Column(name = TaxRuleDBConst.APPLIES_TO_SHIPPING, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '运费是否计税'")
    private Boolean appliesToShipping;

    @Column(name = TaxRuleDBConst.THRESHOLD_USD, definition = "decimal(12,2) NULL COMMENT '起征额 USD（base < threshold 不计税）'")
    private BigDecimal thresholdUsd;

    @Column(name = TaxRuleDBConst.EFFECTIVE_FROM, definition = "date NULL")
    private LocalDate effectiveFrom;

    @Column(name = TaxRuleDBConst.EFFECTIVE_TO, definition = "date NULL")
    private LocalDate effectiveTo;

    @Column(name = TaxRuleDBConst.ENABLED, definition = "tinyint(1) NOT NULL DEFAULT 1")
    private Boolean enabled;

    @Column(name = TaxRuleDBConst.LABEL, definition = "varchar(64) NULL COMMENT '展示标签，如 VAT 20%'")
    private String label;
}
