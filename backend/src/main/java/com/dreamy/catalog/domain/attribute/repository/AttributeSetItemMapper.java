package com.dreamy.catalog.domain.attribute.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.catalog.domain.attribute.entity.AttributeSetItem;
import org.apache.ibatis.annotations.Mapper;

/** AttributeSetItemMapper。表 attribute_set_item。 */
@Mapper
public interface AttributeSetItemMapper extends BaseMapper<AttributeSetItem> {
}
