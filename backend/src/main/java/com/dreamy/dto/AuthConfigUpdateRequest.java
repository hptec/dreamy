package com.dreamy.dto;

/**
 * 认证配置更新入参（FUNC-023）。后台 AuthSettings 提交的可变配置字段。
 * email_enabled 由 Service 强制 true（V-CFG），不接受客户端覆盖；id 为单例固定，不接受入参。
 */
public record AuthConfigUpdateRequest(
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
