package com.dreamy.security;

/**
 * showroom guest 旁路上下文（showroom-api-detail 0.2-e）。
 * StoreJwtFilter guest 旁路校验通过后写入，服务层据此取 my_member 身份
 * （不查 nickname、不信任请求体身份字段）；请求结束由过滤器 finally 清理。
 */
public record GuestContext(long showroomId, long memberId) {

    private static final ThreadLocal<GuestContext> HOLDER = new ThreadLocal<>();

    public static void set(GuestContext context) {
        HOLDER.set(context);
    }

    public static GuestContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
