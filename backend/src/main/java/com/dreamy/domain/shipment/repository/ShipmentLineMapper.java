package com.dreamy.domain.shipment.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.shipment.entity.ShipmentLine;
import org.apache.ibatis.annotations.Mapper;

/** ShipmentLineMapper。 */
@Mapper
public interface ShipmentLineMapper extends BaseMapper<ShipmentLine> {
}
