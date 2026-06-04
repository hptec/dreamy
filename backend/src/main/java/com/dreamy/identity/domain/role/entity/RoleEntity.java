package com.dreamy.identity.domain.role.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.dreamy.identity.domain.role.consts.RoleDBConst;

/**
 * 表 role（角色 / 聚合根）。
 * 约束: RM-060/061、MAP-005、is_locked 超管保护（FLOW-11/EDGE-014）、乐观锁 version。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "role", comment = "角色", indexes = {
        @Index(name = "uk_role_name", columns = {"name"}, unique = true)
})
@TableName(value = "role", autoResultMap = true)
public class RoleEntity extends LongAuditableEntity {

    @Column(name = RoleDBConst.NAME, definition = "varchar(64) NOT NULL COMMENT '角色名'")
    private String name;

    /** type: preset/custom（ck_role_type） */
    @Column(name = RoleDBConst.TYPE, definition = "varchar(16) NOT NULL DEFAULT 'custom' COMMENT '类型 preset/custom'")
    private String type;

    @Column(name = RoleDBConst.IS_LOCKED, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '锁定（超管保护）'")
    private Boolean isLocked;

    @Version
    @Column(name = RoleDBConst.VERSION, definition = "int NOT NULL DEFAULT 0 COMMENT '乐观锁版本'")
    @TableField("version")
    private Integer version;
}
