package com.dreamy.trading.domain.refund.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.trading.domain.refund.entity.Refund;
import org.apache.ibatis.annotations.Mapper;

/** RefundMapper。表 refund。 */
@Mapper
public interface RefundMapper extends BaseMapper<Refund> {
}
