package com.dreamy.identity.domain.authconfig.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.domain.authconfig.entity.AuthConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * AuthConfigMapper —— RM-110/111。表 auth_config（单例 id=1）。
 */
@Mapper
public interface AuthConfigMapper extends BaseMapper<AuthConfig> {
}
