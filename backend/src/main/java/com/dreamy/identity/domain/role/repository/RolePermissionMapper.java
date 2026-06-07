package com.dreamy.identity.domain.role.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.domain.role.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * RolePermissionMapper —— RM-070/071。表 role_permission（关联表，Long 代理主键 + 唯一索引）。
 * permission_id(Long FK) 内部存储；listKeysByRoleId 已迁移至 RoleService（分步查询 + 内存聚合，A3）。
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}
