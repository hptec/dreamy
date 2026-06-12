package com.dreamy.domain.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.question.entity.ProductQuestion;
import org.apache.ibatis.annotations.Mapper;

/** ProductQuestionMapper。表 product_question。 */
@Mapper
public interface ProductQuestionMapper extends BaseMapper<ProductQuestion> {
}
