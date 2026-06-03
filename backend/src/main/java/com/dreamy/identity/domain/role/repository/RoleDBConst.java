package com.dreamy.identity.domain.role.repository;

/**
 * RoleEntity 表列名常量。
 * 用于 @Column(name)、@TableField、QueryWrapper，禁止硬编码列名字符串。
 */
public interface RoleDBConst {

    String NAME      = "name";
    String TYPE      = "type";
    String IS_LOCKED = "is_locked";
    String VERSION   = "version";
}
