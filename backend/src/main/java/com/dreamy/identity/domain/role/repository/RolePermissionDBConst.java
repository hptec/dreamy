package com.dreamy.identity.domain.role.repository;

/**
 * RolePermissionEntity 表列名常量。
 * 用于 @Column(name)、@TableField、QueryWrapper，禁止硬编码列名字符串。
 */
public interface RolePermissionDBConst {

    String ROLE_ID       = "role_id";
    String PERMISSION_ID = "permission_id";
}
