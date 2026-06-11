package com.dreamy.catalog.domain.tag.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.catalog.domain.tag.entity.TagTranslation;
import org.apache.ibatis.annotations.Mapper;

/** TagTranslationMapper。表 tag_translation。 */
@Mapper
public interface TagTranslationMapper extends BaseMapper<TagTranslation> {
}
