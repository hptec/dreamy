package com.dreamy.domain.shipment.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.shipment.entity.ShipmentEvent;
import org.apache.ibatis.annotations.Mapper;

/** ShipmentEventMapper。 */
@Mapper
public interface ShipmentEventMapper extends BaseMapper<ShipmentEvent> {
}
