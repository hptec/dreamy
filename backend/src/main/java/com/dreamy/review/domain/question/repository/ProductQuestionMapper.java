package com.dreamy.review.domain.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.review.domain.question.entity.ProductQuestion;
import org.apache.ibatis.annotations.Mapper;

/** ProductQuestionMapper。表 product_question。 */
@Mapper
public interface ProductQuestionMapper extends BaseMapper<ProductQuestion> {
}
