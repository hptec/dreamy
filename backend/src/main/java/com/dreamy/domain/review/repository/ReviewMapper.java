package com.dreamy.domain.review.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.review.entity.Review;
import org.apache.ibatis.annotations.Mapper;

/** ReviewMapper。表 review。 */
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {
}
