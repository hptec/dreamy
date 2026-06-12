package com.dreamy.port;

/**
 * identity 领域用户查询端口（进程内直调，决策 3；review-data-detail §8.3）。
 * 用途：customer_name/asker 姓名快照（E-REV-02 STEP-REV-03 / E-REV-04 STEP-REV-01，CV-REV-010 提交时一次性快照）。
 * identity 域未提供同名 bean 时由 ReviewPortConfig 基于 identity UserMapper 的只读适配实现兜底。
 */
public interface ReviewIdentityQueryPort {

    /** 用户展示名；用户不存在/无姓名返回 null（脱敏输出时回退 Guest，MAP-REV-001） */
    String getUserName(Long userId);
}
