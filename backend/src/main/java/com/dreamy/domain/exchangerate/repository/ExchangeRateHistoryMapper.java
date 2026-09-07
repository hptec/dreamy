package com.dreamy.domain.exchangerate.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.exchangerate.entity.ExchangeRateHistory;
import org.apache.ibatis.annotations.Mapper;

/** ExchangeRateHistoryMapper —— 表 exchange_rate_history。 */
@Mapper
public interface ExchangeRateHistoryMapper extends BaseMapper<ExchangeRateHistory> {
}
