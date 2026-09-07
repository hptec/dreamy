package com.dreamy.domain.shippingrate.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.shippingrate.entity.ShippingOption;
import org.apache.ibatis.annotations.Mapper;

/** ShippingOptionMapper —— 表 shipping_option。 */
@Mapper
public interface ShippingOptionMapper extends BaseMapper<ShippingOption> {
}
