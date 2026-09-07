package com.dreamy.domain.shipment.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.shipment.entity.Shipment;
import org.apache.ibatis.annotations.Mapper;

/** ShipmentMapper。 */
@Mapper
public interface ShipmentMapper extends BaseMapper<Shipment> {
}
