package com.dreamy.identity.dto;

import java.time.LocalDateTime;

/**
 * 登录记录出参（MAP-007）。用户详情页登录历史展示，按需脱敏。
 */
public record LoginHistoryDTO(
        Long id,
        String email,
        String method,
        String ip,
        String device,
        String location,
        String result,
        Boolean isNewDevice,
        LocalDateTime createdAt
) {
}
