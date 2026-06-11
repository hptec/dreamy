package com.dreamy.showroom.port;

/**
 * identity 领域用户查询端口（进程内直调，决策 3；showroom-data-detail §8.4）。
 * 用途：owner 首次互动自动建 member 的昵称来源（E-SHR-10/11 STEP-SHR-03）。
 * identity 域未提供同名 bean 时由 ShowroomPortConfig 基于 identity UserMapper 的只读适配实现兜底。
 */
public interface IdentityQueryPort {

    /** 用户展示名；用户不存在/无姓名返回 null（调用方回退确定性缺省昵称） */
    String getUserName(Long customerId);
}
