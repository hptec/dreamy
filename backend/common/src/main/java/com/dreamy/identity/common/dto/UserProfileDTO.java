package com.dreamy.identity.common.dto;

import java.time.OffsetDateTime;

/**
 * 用户资料出参（MAP-001）。snake_case 由 Jackson 配置统一转换；匿名化态 email 返回 null；不含密码类字段。
 */
public record UserProfileDTO(
        String id,
        String email,
        Boolean emailVerified,
        String name,
        String phone,
        String tier,
        String avatar,
        OffsetDateTime joinedAt
) {
}
