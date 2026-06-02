package com.dreamy.identity.common.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 表 permission（菜单权限点字典，22 项）。对应 identity-ddl.sql 表 8。
 * 约束: RM-080、业务主键 key（shared-contracts id_field 例外）。
 * 注: key/group 为 SQL 保留字，列名用反引号；此处 Java 字段映射 menuKey/groupName。
 */
@Data
@TableName("permission")
public class PermissionEntity {

    /** 业务主键，列名 `key`（如 /system/admins） */
    @TableId(value = "`key`", type = IdType.INPUT)
    private String key;

    /** 列名 `group` */
    @com.baomidou.mybatisplus.annotation.TableField("`group`")
    private String group;

    private String label;
}
