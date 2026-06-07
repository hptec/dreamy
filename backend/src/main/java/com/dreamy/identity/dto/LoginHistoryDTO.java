package com.dreamy.identity.dto;

import com.dreamy.identity.domain.enums.AuthProvider;
import com.dreamy.identity.domain.enums.LoginOutcome;

import java.time.LocalDateTime;

public record LoginHistoryDTO(
        Long id,
        String email,
        AuthProvider method,
        String ip,
        String device,
        String location,
        LoginOutcome result,
        Boolean isNewDevice,
        LocalDateTime createdAt
) {
}
