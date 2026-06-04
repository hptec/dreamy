package com.dreamy.identity.domain.role.consts;

import com.dreamy.identity.domain.consts.CommonDBConst;

/**
 * RoleEntity 表列名常量。
 * 用于 @Column(name)、@TableField、QueryWrapper，禁止硬编码列名字符串。
 * D: 继承 CommonDBConst 基类列。
 */
public interface RoleDBConst extends CommonDBConst {

    String NAME      = "name";
    String TYPE      = "type";
    String IS_LOCKED = "is_locked";
    String VERSION   = "version";
}
