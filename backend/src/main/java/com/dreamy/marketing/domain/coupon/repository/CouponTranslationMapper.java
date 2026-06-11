package com.dreamy.marketing.domain.coupon.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.marketing.domain.coupon.entity.CouponTranslation;
import org.apache.ibatis.annotations.Mapper;

/** CouponTranslationMapper（RM-MKT-100~112 由 CouponRepository 封装）。 */
@Mapper
public interface CouponTranslationMapper extends BaseMapper<CouponTranslation> {
}
