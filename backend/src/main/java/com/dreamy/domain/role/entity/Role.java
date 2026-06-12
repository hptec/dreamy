package com.dreamy.domain.role.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.dreamy.enums.RoleType;
import com.dreamy.domain.role.consts.RoleDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "role", comment = "角色", indexes = {
        @Index(name = "uk_role_name", columns = {"name"}, unique = true)
})
@TableName(value = "role", autoResultMap = true)
public class Role extends LongAuditableEntity {

    @Column(name = RoleDBConst.NAME, definition = "varchar(64) NOT NULL COMMENT '角色名'")
    private String name;

    @Column(name = RoleDBConst.TYPE, definition = "tinyint NOT NULL DEFAULT 2 COMMENT '类型：1=系统预设 2=自定义'")
    private RoleType type;

    @Column(name = RoleDBConst.IS_LOCKED, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '锁定（超管保护）'")
    private Boolean isLocked;

    @Version
    @Column(name = RoleDBConst.VERSION, definition = "int NOT NULL DEFAULT 0 COMMENT '乐观锁版本'")
    @TableField("version")
    private Integer version;
}
