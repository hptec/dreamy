package com.dreamy.domain.guide.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.guide.entity.Guide;
import org.apache.ibatis.annotations.Mapper;

/** GuideMapper（RM-MKT-080~089 由 GuideRepository 封装）。 */
@Mapper
public interface GuideMapper extends BaseMapper<Guide> {
}
