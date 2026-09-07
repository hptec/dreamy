package com.dreamy.domain.tax.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.tax.entity.TaxRule;
import org.apache.ibatis.annotations.Mapper;

/** TaxRuleMapper —— 表 tax_rule。 */
@Mapper
public interface TaxRuleMapper extends BaseMapper<TaxRule> {
}
