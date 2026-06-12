package com.dreamy.domain.role.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.role.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

/**
 * PermissionMapper —— RM-080（22 项字典，按 group）。表 permission。
 * findIdByPermCode 已迁至 RoleService 用 LambdaQueryWrapper 实现（DEC-004/A4）。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
