package com.dreamy.identity.common.dto;

import java.time.OffsetDateTime;

/**
 * 会话出参（MAP-003）。附 is_current（jti 匹配）；隐藏 token_id/refresh_token_id。
 */
public record SessionDTO(
        String id,
        String device,
        String browser,
        String ip,
        String location,
        Boolean isNewDevice,
        Boolean isCurrent,
        OffsetDateTime lastActiveAt,
        OffsetDateTime createdAt
) {
}
