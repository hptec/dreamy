package com.dreamy.identity.domain.role.repository;

/**
 * PermissionEntity 表列名常量。
 * 用于 @Column(name)、@TableField、QueryWrapper，禁止硬编码列名字符串。
 * 注: GROUP 列名为 SQL 保留字，使用反引号形式。
 */
public interface PermissionDBConst {

    String PERM_CODE = "perm_code";
    String GROUP     = "`group`";
    String LABEL     = "label";
}
