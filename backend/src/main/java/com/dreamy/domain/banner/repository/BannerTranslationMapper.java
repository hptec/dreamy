package com.dreamy.domain.banner.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.banner.entity.BannerTranslation;
import org.apache.ibatis.annotations.Mapper;

/** BannerTranslationMapper。表 banner_translation（RM-MKT-010~012 由 BannerRepository 封装）。 */
@Mapper
public interface BannerTranslationMapper extends BaseMapper<BannerTranslation> {
}
