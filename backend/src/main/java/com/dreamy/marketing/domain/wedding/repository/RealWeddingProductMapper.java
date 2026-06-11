package com.dreamy.marketing.domain.wedding.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.marketing.domain.wedding.entity.RealWeddingProduct;
import org.apache.ibatis.annotations.Mapper;

/** RealWeddingProductMapper（RM-MKT-040~054 由 RealWeddingRepository 封装）。 */
@Mapper
public interface RealWeddingProductMapper extends BaseMapper<RealWeddingProduct> {
}
