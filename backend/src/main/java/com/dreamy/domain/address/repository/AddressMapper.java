package com.dreamy.domain.address.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.address.entity.Address;
import org.apache.ibatis.annotations.Mapper;

/** AddressMapper。表 address。 */
@Mapper
public interface AddressMapper extends BaseMapper<Address> {
}
