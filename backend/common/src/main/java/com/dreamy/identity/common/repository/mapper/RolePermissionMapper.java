package com.dreamy.identity.common.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.common.repository.entity.RolePermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * RolePermissionMapper —— RM-070/071。表 role_permission（复合主键）。
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionEntity> {

    /** RM-070 listKeysByRoleId：PK(role_id,...)（FLOW-09 登录加载权限） */
    @Select("SELECT permission_key FROM role_permission WHERE role_id = #{roleId}")
    List<String> listKeysByRoleId(@Param("roleId") String roleId);
}
