package com.dreamy.dto;

import com.dreamy.enums.UserStatus;
import com.dreamy.enums.UserTier;

import java.time.LocalDateTime;

public record UserProfileDTO(
        Long id,
        String email,
        Boolean emailVerified,
        String name,
        String phone,
        UserTier tier,
        String avatar,
        LocalDateTime joinedAt,
        UserStatus status
) {
}
