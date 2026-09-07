package com.dreamy.domain.exchangerate.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.exchangerate.consts.ExchangeRateDBConst;
import com.dreamy.enums.ExchangeRateSource;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @Column(name = ExchangeRateDBConst.SOURCE, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '1=手工 2=供应商'")
    private ExchangeRateSource source;

    @Column(name = ExchangeRateDBConst.SYNCED_AT, definition = "datetime(3) NULL COMMENT '供应商最近同步时间'")
    private LocalDateTime syncedAt;

    @Column(name = ExchangeRateDBConst.MANUAL_OVERRIDE, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '1=手工锁定，供应商刷新跳过'")
    private Boolean manualOverride;
}
