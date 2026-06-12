package com.dreamy.domain.coupon.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.coupon.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;

/** CouponMapper（RM-MKT-100~112 由 CouponRepository 封装）。 */
@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {
}
