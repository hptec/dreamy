package com.dreamy.identity.dto;

import java.time.LocalDateTime;

/**
 * 会话出参（MAP-003）。附 is_current（jti 匹配）；隐藏 token_id/refresh_token_id。
 */
public record SessionDTO(
        Long id,
        String device,
        String browser,
        String ip,
        String location,
        Boolean isNewDevice,
        Boolean isCurrent,
        LocalDateTime lastActiveAt,
        LocalDateTime createdAt
) {
}
