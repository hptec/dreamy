package com.dreamy.domain.category.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.category.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/** CategoryMapper。表 category（RM-CAT-001~008 由 CategoryRepository 封装）。 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
