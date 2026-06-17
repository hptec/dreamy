package com.dreamy.domain.collection.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.collection.entity.Collection;
import org.apache.ibatis.annotations.Mapper;

/** CollectionMapper。表 collection。 */
@Mapper
public interface CollectionMapper extends BaseMapper<Collection> {
}
