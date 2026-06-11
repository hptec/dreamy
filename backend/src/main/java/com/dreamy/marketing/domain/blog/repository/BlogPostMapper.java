package com.dreamy.marketing.domain.blog.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.marketing.domain.blog.entity.BlogPost;
import org.apache.ibatis.annotations.Mapper;

/** BlogPostMapper。表 blog_post（RM-MKT-020~029 由 BlogPostRepository 封装）。 */
@Mapper
public interface BlogPostMapper extends BaseMapper<BlogPost> {
}
