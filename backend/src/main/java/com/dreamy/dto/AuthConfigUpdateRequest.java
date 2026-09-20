package com.dreamy.dto;

/**
 * 认证配置更新入参（FUNC-023）。后台 AuthSettings 提交的可变配置字段。
 * email/google/apple 登录方式均可开闭（允许全关，不做最少保留一种校验）；id 为单例固定，不接受入参。
 */
public record AuthConfigUpdateRequest(
        Boolean emailEnabled,
        Boolean googleEnabled,
        Boolean appleEnabled,
        Integer otpLength,
        Integer otpTtlMinutes,
        Integer otpResendSeconds,
        Integer otpMaxAttempts,
        Integer minMethods,
        String googleClientId,
        String appleServiceId
) {
}
