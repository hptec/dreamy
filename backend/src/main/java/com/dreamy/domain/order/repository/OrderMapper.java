package com.dreamy.domain.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/** OrderMapper。表 orders。 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
