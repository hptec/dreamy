package com.dreamy.identity.security;

/**
 * Showroom guest 会话有效性校验扩展点（showroom-api-detail 0.2-d）。
 * 由 showroom 域实现注入（按 PK 点查 showroom 行，轻量主键查询不缓存）：
 * 行不存在（Showroom 已删除）或 invite_version != claims.inv_ver（邀请链接已重置）→ 无效。
 * StoreJwtFilter 经 ObjectProvider 可选注入：实现缺席时 guest 请求一律按无效处理（fail-closed，
 * 401 401101），保证 showroom 域未装配前 guest 旁路不可被利用。
 */
public interface ShowroomGuestValidator {

    /**
     * @param showroomId    guest JWT claims.showroom_id
     * @param inviteVersion guest JWT claims.inv_ver（签发时 Showroom.invite_version 快照）
     * @return true=会话有效（行存在且 invite_version 等值）
     */
    boolean isGuestSessionValid(long showroomId, long inviteVersion);
}
