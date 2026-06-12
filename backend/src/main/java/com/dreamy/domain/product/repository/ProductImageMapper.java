package com.dreamy.domain.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.product.entity.ProductImage;
import org.apache.ibatis.annotations.Mapper;

/** ProductImageMapper。表 product_image。 */
@Mapper
public interface ProductImageMapper extends BaseMapper<ProductImage> {
}
