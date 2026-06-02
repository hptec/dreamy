package com.dreamy.identity.common.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 表 role_permission（角色-权限关联，复合主键防重复）。对应 identity-ddl.sql 表 9。
 * 约束: RM-070/071（FLOW-11 全量重写 DELETE+批量 INSERT，TX-004）、rolepermission_pk[role_id,permission_key]。
 * 注: 复合主键无单一 @TableId，使用 BaseMapper 时按字段条件操作。
 */
@Data
@TableName("role_permission")
public class RolePermissionEntity {

    private String roleId;

    private String permissionKey;
}
