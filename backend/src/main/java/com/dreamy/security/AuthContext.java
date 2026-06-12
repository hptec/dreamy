package com.dreamy.security;

/**
 * 当前请求鉴权主体上下文（ThreadLocal）。
 * 约束: store/admin 过滤器解析 JWT 后写入，Controller/Service 读取（BE-DIM-6）。
 */
public final class AuthContext {

    private static final ThreadLocal<AuthPrincipal> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthPrincipal principal) {
        HOLDER.set(principal);
    }

    public static AuthPrincipal get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
