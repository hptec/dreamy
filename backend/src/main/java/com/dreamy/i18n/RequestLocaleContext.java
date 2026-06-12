package com.dreamy.i18n;

import java.util.Locale;

/**
 * 请求语言上下文（ThreadLocal）。
 * 约束: store 过滤器按 Accept-Language 设置 en/es/fr；admin 过滤器固定 zh。
 * GlobalExceptionHandler 据此本地化 message。
 */
public final class RequestLocaleContext {

    private static final ThreadLocal<Locale> HOLDER = ThreadLocal.withInitial(() -> Locale.ENGLISH);

    private RequestLocaleContext() {
    }

    public static void set(Locale locale) {
        HOLDER.set(locale == null ? Locale.ENGLISH : locale);
    }

    public static Locale get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
