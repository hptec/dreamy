package com.dreamy.domain.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.product.entity.ProductFabricComposition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * ProductFabricComposition Mapper。
 * L2 TRACE: catalog-fabric-care-data-detail §2 ProductFabricCompositionRepository。
 */
@Mapper
public interface ProductFabricCompositionMapper extends BaseMapper<ProductFabricComposition> {

    /** RM-FC-005 validatePercentageSum —— 返回指定 product_id + layer 的 percentage 总和 */
    @Select("SELECT COALESCE(SUM(percentage), 0) FROM product_fabric_composition WHERE product_id = #{productId} AND layer = #{layer}")
    BigDecimal sumPercentageByProductAndLayer(@Param("productId") Long productId, @Param("layer") Integer layer);
}
