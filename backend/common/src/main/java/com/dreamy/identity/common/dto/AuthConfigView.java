package com.dreamy.identity.common.dto;

/**
 * 认证配置出参（MAP-009）。仅暴露业务配置字段，隐藏 MyBatis 内部字段（id 单例/updatedAt）。
 * 对应 AuthConfigEntity 业务字段，供后台 AuthSettings 页与消费端登录页读取。
 */
public record AuthConfigView(
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
