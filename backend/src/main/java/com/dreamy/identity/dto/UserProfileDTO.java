package com.dreamy.identity.dto;

import com.dreamy.identity.domain.enums.UserStatus;
import com.dreamy.identity.domain.enums.UserTier;

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
