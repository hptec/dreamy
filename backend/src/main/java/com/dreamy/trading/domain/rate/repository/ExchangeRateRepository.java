package com.dreamy.trading.domain.rate.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.trading.domain.rate.entity.ExchangeRate;
import com.dreamy.trading.support.TradingParams;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 汇率仓储（RM-TRD-080~082）。listAll 固定币序 USD,EUR,CAD,AUD,GBP（内存排序等价 FIELD()）。
 */
@Repository
public class ExchangeRateRepository {

    private final ExchangeRateMapper mapper;

    public ExchangeRateRepository(ExchangeRateMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-TRD-080 listAll（五行，固定币序） */
    public List<ExchangeRate> listAll() {
        return mapper.selectList(null).stream()
                .sorted(Comparator.comparingInt(r -> {
                    int idx = TradingParams.CURRENCIES.indexOf(r.getCurrency());
                    return idx < 0 ? Integer.MAX_VALUE : idx;
                }))
                .toList();
    }

    /** RM-TRD-081 findByCurrency（uk_rate_currency） */
    public ExchangeRate findByCurrency(String currency) {
        return mapper.selectOne(new LambdaQueryWrapper<ExchangeRate>()
                .eq(ExchangeRate::getCurrency, currency));
    }

    /** RM-TRD-082 updateRate（TX-TRD-011） */
    public int updateRate(String currency, BigDecimal rate, Long updatedBy) {
        return mapper.update(null, new LambdaUpdateWrapper<ExchangeRate>()
                .eq(ExchangeRate::getCurrency, currency)
                .set(ExchangeRate::getRate, rate)
                .set(ExchangeRate::getUpdatedBy, updatedBy));
    }

    /** 种子插入（决策 21，TradingSeedInitializer） */
    public void insert(ExchangeRate rate) {
        mapper.insert(rate);
    }

    public long count() {
        return mapper.selectCount(null);
    }
}
