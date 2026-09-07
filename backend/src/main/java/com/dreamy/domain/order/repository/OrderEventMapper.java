package com.dreamy.domain.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.order.entity.OrderEvent;
import org.apache.ibatis.annotations.Mapper;

/** OrderEventMapper。表 order_event。 */
@Mapper
public interface OrderEventMapper extends BaseMapper<OrderEvent> {
}
