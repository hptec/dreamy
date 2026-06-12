package com.dreamy.domain.tag.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.tag.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

/** TagMapper。表 tag。 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
