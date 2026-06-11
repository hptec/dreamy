package com.dreamy.catalog.domain.attribute.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.catalog.domain.attribute.entity.AttributeDef;
import org.apache.ibatis.annotations.Mapper;

/** AttributeDefMapper。表 attribute_def。 */
@Mapper
public interface AttributeDefMapper extends BaseMapper<AttributeDef> {
}
