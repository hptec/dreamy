package com.dreamy.domain.banner.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.banner.entity.Banner;
import org.apache.ibatis.annotations.Mapper;

/** BannerMapper。表 banner（RM-MKT-001~008 由 BannerRepository 封装）。 */
@Mapper
public interface BannerMapper extends BaseMapper<Banner> {
}
