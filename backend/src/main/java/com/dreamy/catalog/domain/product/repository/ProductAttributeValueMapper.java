package com.dreamy.catalog.domain.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.catalog.domain.product.entity.ProductAttributeValue;
import org.apache.ibatis.annotations.Mapper;

/** ProductAttributeValueMapper。表 product_attribute_value。 */
@Mapper
public interface ProductAttributeValueMapper extends BaseMapper<ProductAttributeValue> {
}
