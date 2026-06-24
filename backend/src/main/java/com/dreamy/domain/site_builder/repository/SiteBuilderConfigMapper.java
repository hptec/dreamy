package com.dreamy.domain.site_builder.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.site_builder.entity.SiteBuilderConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SiteBuilderConfigMapper extends BaseMapper<SiteBuilderConfig> {
}
