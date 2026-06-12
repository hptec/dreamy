package com.dreamy.dto;

import com.dreamy.enums.RoleType;

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
