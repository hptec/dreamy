package com.dreamy.identity.domain.role.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.domain.role.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * RoleMapper —— RM-060/061。表 role。
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
