package com.dreamy.domain.exchangerate.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.exchangerate.entity.ExchangeRate;
import org.apache.ibatis.annotations.Mapper;

/** ExchangeRateMapper。表 exchange_rate。 */
@Mapper
public interface ExchangeRateMapper extends BaseMapper<ExchangeRate> {
}
