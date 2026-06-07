package com.dreamy.identity.dto;

import com.dreamy.identity.domain.enums.RoleType;

import java.util.List;

public record RoleDTO(
        Long id,
        String name,
        RoleType type,
        Boolean isLocked,
        long memberCount,
        List<String> permissionKeys
) {
}
