package com.dreamy.identity.dto;

import com.dreamy.identity.domain.enums.AdminStatus;

import java.time.LocalDateTime;

public record AdminDTO(
        Long id,
        String name,
        String email,
        Long roleId,
        String roleName,
        AdminStatus status,
        LocalDateTime lastLoginAt
) {
}
