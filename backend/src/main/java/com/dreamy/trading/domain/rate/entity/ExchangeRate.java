package com.dreamy.trading.domain.rate.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.trading.domain.rate.consts.ExchangeRateDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 表 exchange_rate（汇率表——决策 14；五币种种子，USD 恒 1 不可改 CV-TRD-009）。
 * L2 TRACE: trading-data-detail §9 DDL-10 / IDX-TRD-021 / RM-TRD-080~082。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "exchange_rate", comment = "汇率表（决策14）", indexes = {
        @Index(name = "uk_rate_currency", columns = {"currency"}, unique = true, local = false)
})
@TableName(value = "exchange_rate", autoResultMap = true)
public class ExchangeRate extends LongAuditableEntity {

    @Column(name = ExchangeRateDBConst.CURRENCY, definition = "char(3) NOT NULL")
    private String currency;

    @Column(name = ExchangeRateDBConst.RATE, definition = "decimal(12,6) NOT NULL COMMENT '相对 USD；USD 恒 1'")
    private BigDecimal rate;

    @Column(name = ExchangeRateDBConst.UPDATED_BY, definition = "bigint NULL COMMENT '逻辑外键 admin_user.id'")
    private Long updatedBy;
}
