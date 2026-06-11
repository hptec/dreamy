package com.dreamy.marketing.domain.blog.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.marketing.domain.blog.entity.BlogPostTranslation;
import org.apache.ibatis.annotations.Mapper;

/** BlogPostTranslationMapper。表 blog_post_translation（RM-MKT-030~032 由 BlogPostRepository 封装）。 */
@Mapper
public interface BlogPostTranslationMapper extends BaseMapper<BlogPostTranslation> {
}
