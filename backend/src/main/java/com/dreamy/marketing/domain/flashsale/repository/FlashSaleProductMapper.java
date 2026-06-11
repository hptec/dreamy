package com.dreamy.marketing.domain.flashsale.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.marketing.domain.flashsale.entity.FlashSaleProduct;
import org.apache.ibatis.annotations.Mapper;

/** FlashSaleProductMapper（RM-MKT-120~133 由 FlashSaleRepository 封装）。 */
@Mapper
public interface FlashSaleProductMapper extends BaseMapper<FlashSaleProduct> {
}
