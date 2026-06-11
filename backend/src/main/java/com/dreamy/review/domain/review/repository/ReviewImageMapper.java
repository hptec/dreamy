package com.dreamy.review.domain.review.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.review.domain.review.entity.ReviewImage;
import org.apache.ibatis.annotations.Mapper;

/** ReviewImageMapper。表 review_image。 */
@Mapper
public interface ReviewImageMapper extends BaseMapper<ReviewImage> {
}
