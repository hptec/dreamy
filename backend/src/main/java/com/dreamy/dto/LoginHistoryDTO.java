package com.dreamy.dto;

import com.dreamy.enums.AuthProvider;
import com.dreamy.enums.LoginOutcome;

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
