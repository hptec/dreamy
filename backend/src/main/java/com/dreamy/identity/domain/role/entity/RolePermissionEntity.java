package com.dreamy.identity.domain.role.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 role_permission（角色-权限关联）。
 * 约束: RM-070/071（FLOW-11 全量重写 DELETE+批量 INSERT，TX-004）。
 * 原复合主键(roleId, permissionKey) 改为 Long 代理主键（基类提供），
 * permissionKey(String) 改名改型为 permissionId(Long)，
 * 唯一索引 uk_role_permission(role_id, permission_id) 防重复。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "role_permission", comment = "角色-权限关联", indexes = {
        @Index(name = "uk_role_permission", columns = {"role_id", "permission_id"}, unique = true)
})
@TableName(value = "role_permission", autoResultMap = true)
public class RolePermissionEntity extends LongAuditableEntity {

    @Column(name = "role_id", definition = "bigint NOT NULL COMMENT '角色 id（FK role.id）'")
    private Long roleId;

    @Column(name = "permission_id", definition = "bigint NOT NULL COMMENT '权限 id（FK permission.id，原 permissionKey）'")
    private Long permissionId;
}
