package com.dreamy.identity.common.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.common.repository.entity.RoleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * RoleMapper —— RM-060/061。表 role。
 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {
}
