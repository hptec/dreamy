package com.dreamy.identity.domain.role.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.domain.role.entity.RolePermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * RolePermissionMapper —— RM-070/071。表 role_permission（关联表，Long 代理主键 + 唯一索引）。
 * permission_id(Long FK) 内部存储；对外通过 join permission 返回业务权限码（perm_code）。
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionEntity> {

    /** RM-070 listKeysByRoleId：join permission 返回权限码（FLOW-09 登录加载权限） */
    @Select("SELECT p.perm_code FROM role_permission rp "
            + "JOIN permission p ON rp.permission_id = p.id "
            + "WHERE rp.role_id = #{roleId}")
    List<String> listKeysByRoleId(@Param("roleId") Long roleId);
}
