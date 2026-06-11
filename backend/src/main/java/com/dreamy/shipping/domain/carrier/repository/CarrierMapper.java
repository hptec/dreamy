package com.dreamy.shipping.domain.carrier.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.shipping.domain.carrier.entity.Carrier;
import org.apache.ibatis.annotations.Mapper;

/** CarrierMapper —— 表 carrier（RM-SHP-001~008 经 CarrierRepository 落地）。 */
@Mapper
public interface CarrierMapper extends BaseMapper<Carrier> {
}
