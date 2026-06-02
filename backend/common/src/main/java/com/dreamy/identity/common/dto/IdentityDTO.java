package com.dreamy.identity.common.dto;

import java.time.OffsetDateTime;

/**
 * 凭证出参（MAP-002）。暴露安全字段，隐藏 provider_uid（防枚举）。
 */
public record IdentityDTO(
        String id,
        String provider,
        String identifier,
        Boolean isPrimary,
        Boolean verified,
        Boolean hiddenEmail,
        Boolean relayValid,
        OffsetDateTime lastLoginAt
) {
}
