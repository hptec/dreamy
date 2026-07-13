package com.dreamy.domain.site_builder.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.site_builder.entity.SiteBuilderConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SiteBuilderConfigMapper extends BaseMapper<SiteBuilderConfig> {

    @Select("SELECT * FROM site_builder_config WHERE id = 1 FOR UPDATE")
    SiteBuilderConfig lockSingleton();
}
