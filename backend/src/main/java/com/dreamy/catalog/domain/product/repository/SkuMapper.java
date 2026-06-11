package com.dreamy.catalog.domain.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.catalog.domain.product.entity.Sku;
import org.apache.ibatis.annotations.Mapper;

/** SkuMapper。表 sku。 */
@Mapper
public interface SkuMapper extends BaseMapper<Sku> {
}
