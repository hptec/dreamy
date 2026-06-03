package com.dreamy.identity.dto;

import java.util.List;

/**
 * 角色出参（MAP-005）。附 member_count + permission_keys。
 */
public record RoleDTO(
        Long id,
        String name,
        String type,
        Boolean isLocked,
        long memberCount,
        List<String> permissionKeys
) {
}
