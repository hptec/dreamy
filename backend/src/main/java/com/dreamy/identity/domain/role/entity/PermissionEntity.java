package com.dreamy.identity.domain.role.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 permission（菜单权限点字典，22 项）。
 * 约束: RM-080。原业务主键 String key 改为 Long 代理主键（基类提供），
 * 原 key 改名为 permCode（列名 perm_code），唯一索引 uk_permission_perm_code。
 * 注: group 为 SQL 保留字，列名 `group` 加反引号（@Column definition + @TableField）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "permission", comment = "菜单权限点字典", indexes = {
        @Index(name = "uk_permission_perm_code", columns = {"perm_code"}, unique = true)
})
@TableName(value = "permission", autoResultMap = true)
public class PermissionEntity extends LongAuditableEntity {

    /** 业务码（原 key，如 /system/admins），改唯一索引 */
    @Column(name = "perm_code", definition = "varchar(128) NOT NULL COMMENT '权限业务码（原 key）'")
    private String permCode;

    /** 列名 group（SQL 保留字）。@Column.name 用裸名（huihao SchemaBuilder 自动加反引号建表）；
     *  @TableField 用反引号（MyBatis-Plus DML 需显式转义）。 */
    @Column(name = "group", definition = "varchar(64) NOT NULL COMMENT '分组'")
    @TableField("`group`")
    private String group;

    @Column(name = "label", definition = "varchar(128) NOT NULL COMMENT '展示名'")
    private String label;
}
