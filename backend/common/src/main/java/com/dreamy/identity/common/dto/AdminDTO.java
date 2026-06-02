package com.dreamy.identity.common.dto;

import java.time.OffsetDateTime;

/**
 * 管理员出参（MAP-004）。隐藏 password_hash；附 role_name。
 */
public record AdminDTO(
        String id,
        String name,
        String email,
        String roleId,
        String roleName,
        String status,
        OffsetDateTime lastLoginAt
) {
}
