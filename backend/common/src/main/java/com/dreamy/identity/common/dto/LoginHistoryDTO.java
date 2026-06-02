package com.dreamy.identity.common.dto;

import java.time.OffsetDateTime;

/**
 * 登录记录出参（MAP-007）。用户详情页登录历史展示，按需脱敏。
 */
public record LoginHistoryDTO(
        String id,
        String email,
        String method,
        String ip,
        String device,
        String location,
        String result,
        Boolean isNewDevice,
        OffsetDateTime createdAt
) {
}
