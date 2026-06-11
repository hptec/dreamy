package com.dreamy.trading.domain.checkout.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.trading.domain.checkout.entity.CheckoutConfig;
import org.apache.ibatis.annotations.Mapper;

/** CheckoutConfigMapper。表 checkout_config。 */
@Mapper
public interface CheckoutConfigMapper extends BaseMapper<CheckoutConfig> {
}
