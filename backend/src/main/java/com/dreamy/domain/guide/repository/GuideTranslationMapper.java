package com.dreamy.domain.guide.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.guide.entity.GuideTranslation;
import org.apache.ibatis.annotations.Mapper;

/** GuideTranslationMapper（RM-MKT-080~089 由 GuideRepository 封装）。 */
@Mapper
public interface GuideTranslationMapper extends BaseMapper<GuideTranslation> {
}
