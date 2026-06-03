package com.dreamy.identity.dto;

import java.time.LocalDateTime;

/**
 * 管理员出参（MAP-004）。隐藏 password_hash；附 role_name。
 */
public record AdminDTO(
        Long id,
        String name,
        String email,
        Long roleId,
        String roleName,
        String status,
        LocalDateTime lastLoginAt
) {
}
