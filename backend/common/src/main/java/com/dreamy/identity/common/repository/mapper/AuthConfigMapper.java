package com.dreamy.identity.common.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.common.repository.entity.AuthConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AuthConfigMapper —— RM-110/111。表 auth_config（单例 id=1）。
 */
@Mapper
public interface AuthConfigMapper extends BaseMapper<AuthConfigEntity> {
}
