package com.dreamy.domain.exchangerate.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.exchangerate.consts.ExchangeRateHistoryDBConst;
import com.dreamy.enums.ExchangeRateSource;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 表 exchange_rate_history（order-flow-complete §2.2）：uk (currency, source, quote_date) —— 同日同源重复刷新
 * insertIgnore 追加语义幂等；手工修改同日多次仅保留首条历史（现值在 exchange_rate 表）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = ExchangeRateHistoryDBConst.TABLE, comment = "汇率历史（供应商日频/手工变更）", indexes = {
        @Index(name = "uk_rate_history", columns = {ExchangeRateHistoryDBConst.CURRENCY, ExchangeRateHistoryDBConst.SOURCE,
                ExchangeRateHistoryDBConst.QUOTE_DATE}, unique = true, local = false),
        @Index(name = "idx_rate_history_currency_recorded", columns = {ExchangeRateHistoryDBConst.CURRENCY,
                ExchangeRateHistoryDBConst.RECORDED_AT}, unique = false, local = false)
})
@TableName(value = ExchangeRateHistoryDBConst.TABLE, autoResultMap = true)
public class ExchangeRateHistory extends LongAuditableEntity {

    @Column(name = ExchangeRateHistoryDBConst.BASE_CURRENCY, definition = "char(3) NOT NULL DEFAULT 'USD'")
    private String baseCurrency;

    @Column(name = ExchangeRateHistoryDBConst.CURRENCY, definition = "char(3) NOT NULL")
    private String currency;

    @Column(name = ExchangeRateHistoryDBConst.RATE, definition = "decimal(12,6) NOT NULL")
    private BigDecimal rate;

    @Column(name = ExchangeRateHistoryDBConst.SOURCE, definition = "tinyint NOT NULL COMMENT '1=MANUAL 2=PROVIDER'")
    private ExchangeRateSource source;

    @Column(name = ExchangeRateHistoryDBConst.QUOTE_DATE, definition = "date NOT NULL COMMENT '供应商报价日 / 手工=当日'")
    private LocalDate quoteDate;

    @Column(name = ExchangeRateHistoryDBConst.RECORDED_AT, definition = "datetime(3) NOT NULL")
    private LocalDateTime recordedAt;
}
