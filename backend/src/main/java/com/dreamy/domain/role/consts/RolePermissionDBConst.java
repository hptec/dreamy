package com.dreamy.domain.role.consts;

import com.dreamy.consts.CommonDBConst;

/**
 * RolePermission 表列名常量。
 * 用于 @Column(name)、@TableField、QueryWrapper，禁止硬编码列名字符串。
 * D: 继承 CommonDBConst 基类列。
 */
public interface RolePermissionDBConst extends CommonDBConst {

    String ROLE_ID       = "role_id";
    String PERMISSION_ID = "permission_id";
}
