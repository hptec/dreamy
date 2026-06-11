package com.dreamy.trading.domain.rate.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.trading.domain.rate.entity.ExchangeRate;
import org.apache.ibatis.annotations.Mapper;

/** ExchangeRateMapper。表 exchange_rate。 */
@Mapper
public interface ExchangeRateMapper extends BaseMapper<ExchangeRate> {
}
