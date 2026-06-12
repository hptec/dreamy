package com.dreamy.controller.pojo;

import com.dreamy.dto.AdminDTO;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 管理员登录会话出参（adminLogin，FLOW-09 FUNC-014）。token + 管理员 DTO + 权限 key 集合。
 * 显式 snake_case 序列化，不依赖全局 Jackson 命名策略（前端契约稳定）。
 */
public record AdminAuthSessionView(
        @JsonProperty("token") String token,
        @JsonProperty("admin") AdminDTO admin,
        @JsonProperty("permission_keys") List<String> permissionKeys,
        @JsonProperty("is_super") boolean isSuper
) {
}
