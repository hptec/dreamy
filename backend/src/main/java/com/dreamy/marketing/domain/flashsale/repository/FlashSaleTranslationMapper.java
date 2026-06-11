package com.dreamy.marketing.domain.flashsale.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.marketing.domain.flashsale.entity.FlashSaleTranslation;
import org.apache.ibatis.annotations.Mapper;

/** FlashSaleTranslationMapper（RM-MKT-120~133 由 FlashSaleRepository 封装）。 */
@Mapper
public interface FlashSaleTranslationMapper extends BaseMapper<FlashSaleTranslation> {
}
