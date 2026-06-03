package com.dreamy.identity.dto;

import java.time.LocalDateTime;

/**
 * 凭证出参（MAP-002）。暴露安全字段，隐藏 provider_uid（防枚举）。
 */
public record IdentityDTO(
        Long id,
        String provider,
        String identifier,
        Boolean isPrimary,
        Boolean verified,
        Boolean hiddenEmail,
        Boolean relayValid,
        LocalDateTime lastLoginAt
) {
}
