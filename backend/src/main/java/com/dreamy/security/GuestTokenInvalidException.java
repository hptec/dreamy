package com.dreamy.security;

/**
 * guest JWT 无效/过期（showroom 域码 401101 GUEST_TOKEN_INVALID 的过滤器层载体）。
 * 触发面（showroom-api-detail 0.2-④）：guest token 过期（ExpiredJwtException 且未验签 claims typ=guest）、
 * guest claims 不完整。由 StoreJwtFilter 捕获并写 401 {code:401101}。
 * 日志脱敏：token 原文一律 [REDACTED]，不入异常 message。
 */
public class GuestTokenInvalidException extends RuntimeException {

    public GuestTokenInvalidException(String message) {
        super(message);
    }
}
