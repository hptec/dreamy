package com.dreamy.identity.domain.role.consts;

import com.dreamy.identity.domain.consts.CommonDBConst;

public interface RoleDBConst extends CommonDBConst {

    String NAME = "name";
    String TYPE = "type";
    String IS_LOCKED = "is_locked";
    // VERSION 继承自 CommonDBConst
}
