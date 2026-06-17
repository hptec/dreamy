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
        UserStatus status,
        /** 用户偏好语言 en/es/fr（决策13 / FUNC-019） */
        String localePref
) {
}
