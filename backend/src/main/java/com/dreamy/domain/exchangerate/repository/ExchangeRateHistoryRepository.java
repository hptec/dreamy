package com.dreamy.domain.exchangerate.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.exchangerate.entity.ExchangeRateHistory;
import com.dreamy.enums.ExchangeRateSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 汇率历史仓储（insertIgnore 以 uk_rate_history 冲突捕获实现——InnoDB 冲突不毒化事务）。 */
@Repository
public class ExchangeRateHistoryRepository {

    private final ExchangeRateHistoryMapper mapper;

    public ExchangeRateHistoryRepository(ExchangeRateHistoryMapper mapper) {
        this.mapper = mapper;
    }

    /** affected=0 = 同日同源已有记录（幂等） */
    public int insertIgnore(String currency, BigDecimal rate, ExchangeRateSource source, LocalDate quoteDate) {
        ExchangeRateHistory row = new ExchangeRateHistory();
        row.setBaseCurrency("USD");
        row.setCurrency(currency);
        row.setRate(rate);
        row.setSource(source);
        row.setQuoteDate(quoteDate);
        row.setRecordedAt(LocalDateTime.now());
        try {
            return mapper.insert(row);
        } catch (DuplicateKeyException ex) {
            return 0;
        }
    }

    /** 最近 days 天（recorded_at DESC） */
    public List<ExchangeRateHistory> listRecent(String currency, int days) {
        return mapper.selectList(new LambdaQueryWrapper<ExchangeRateHistory>()
                .eq(ExchangeRateHistory::getCurrency, currency)
                .ge(ExchangeRateHistory::getRecordedAt, LocalDateTime.now().minusDays(days))
                .orderByDesc(ExchangeRateHistory::getRecordedAt)
                .orderByDesc(ExchangeRateHistory::getId)
                .last("LIMIT 500"));
    }
}
