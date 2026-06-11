package com.dreamy.shipping.domain.rate.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.shipping.domain.rate.entity.ShippingRate;
import org.apache.ibatis.annotations.Mapper;

/** ShippingRateMapper —— 表 shipping_rate（RM-SHP-010~015 经 ShippingRateRepository 落地）。 */
@Mapper
public interface ShippingRateMapper extends BaseMapper<ShippingRate> {
}
