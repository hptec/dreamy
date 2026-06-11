package com.dreamy.catalog.domain.category.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.catalog.domain.category.entity.CategoryTranslation;
import org.apache.ibatis.annotations.Mapper;

/** CategoryTranslationMapper。表 category_translation（RM-CAT-010~012 由 CategoryRepository 封装）。 */
@Mapper
public interface CategoryTranslationMapper extends BaseMapper<CategoryTranslation> {
}
