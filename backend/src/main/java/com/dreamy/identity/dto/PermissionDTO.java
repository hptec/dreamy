package com.dreamy.identity.dto;

/**
 * 权限点出参（RM-080）。{key,group,label}。
 */
public record PermissionDTO(
        String key,
        String group,
        String label
) {
}
