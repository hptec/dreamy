package com.dreamy.identity.domain.role.consts;

import com.dreamy.identity.domain.consts.CommonDBConst;

/**
 * PermissionEntity 表列名常量。
 * 用于 @Column(name)、QueryWrapper，禁止硬编码列名字符串。
 * 注: GROUP 为 SQL 保留字，常量存裸名（@Column.name 用裸名，huihao SchemaBuilder 建表自动加反引号）；
 *     DML 转义由 @TableField("`group`") 单独负责，不引用本常量。
 * D: 继承 CommonDBConst 基类列。
 */
public interface PermissionDBConst extends CommonDBConst {

    String PERM_CODE = "perm_code";
    String GROUP     = "group";
    String LABEL     = "label";
}
