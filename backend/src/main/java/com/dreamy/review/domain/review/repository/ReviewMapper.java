package com.dreamy.review.domain.review.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.review.domain.review.entity.Review;
import org.apache.ibatis.annotations.Mapper;

/** ReviewMapper。表 review。 */
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {
}
