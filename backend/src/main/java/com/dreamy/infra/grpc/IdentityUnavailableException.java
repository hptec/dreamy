package com.dreamy.infra.grpc;

/**
 * IdentityGate 不可用异常(gRPC UNAVAILABLE/DEADLINE_EXCEEDED)。
 * 契约:调用方(过滤器/切面)捕获后返回 HTTP 503 {code:50001},绝不映射 401(避免误踢登录态)。
 */
public class IdentityUnavailableException extends RuntimeException {

    public IdentityUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
