package com.dreamy.domain.attribute.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.attribute.entity.AttributeSet;
import org.apache.ibatis.annotations.Mapper;

/** AttributeSetMapper。表 attribute_set。 */
@Mapper
public interface AttributeSetMapper extends BaseMapper<AttributeSet> {
}
