package com.dreamy.identity.admin.dto;

import com.dreamy.identity.common.dto.AdminDTO;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 管理员当前身份出参（adminMe，FUNC-021 守卫数据源）。组合 AdminDTO + 角色名 + 超管标识 + 权限 key 集合。
 * 显式 snake_case 序列化，不依赖全局 Jackson 命名策略（前端契约稳定）。
 */
public record AdminMeView(
        @JsonProperty("admin") AdminDTO admin,
        @JsonProperty("role_name") String roleName,
        @JsonProperty("is_super") boolean isSuper,
        @JsonProperty("permission_keys") List<String> permissionKeys
) {
}
