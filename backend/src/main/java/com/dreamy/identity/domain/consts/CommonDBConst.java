package com.dreamy.identity.domain.consts;

/**
 * 公共数据库列名常量。
 * 包含：基类审计列 + 跨表共享的语义相同字段定义（避免各表重复定义不一致）。
 */
public interface CommonDBConst {

    String ID = "id";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";

    // --- 跨表共享字段（相同语义的列在此统一定义） ---

    /** 状态列：tinyint NOT NULL，各表通过各自枚举赋予具体语义 */
    String STATUS = "status";

    /** 乐观锁版本列 */
    String VERSION = "version";

    /** 登录方式：tinyint NOT NULL，对应 AuthProvider 枚举（1=email/2=google/3=apple） */
    String METHOD = "method";

    /** 设备信息 */
    String DEVICE = "device";

    /** IP 地址 */
    String IP = "ip";

    /** 登录地点 */
    String LOCATION = "location";

    /** 是否新设备 */
    String IS_NEW_DEVICE = "is_new_device";

    /** 最近活跃时间 */
    String LAST_ACTIVE_AT = "last_active_at";
}
