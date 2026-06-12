package com.dreamy.domain.tag.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.tag.entity.TagDimension;
import org.apache.ibatis.annotations.Mapper;

/** TagDimensionMapper。表 tag_dimension。 */
@Mapper
public interface TagDimensionMapper extends BaseMapper<TagDimension> {
}
