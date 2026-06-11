package com.dreamy.identity.security;

import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * method-aware 路径白名单匹配器（review-api-detail 0.1 定稿条目形式，showroom-api-detail 0.2-3 共用实现）。
 * 条目形式：`METHOD:pattern`（AntPath 风格）；无 METHOD 前缀缺省匹配全部 method（向后兼容
 * catalog/trading/marketing 既有无前缀条目）。
 * 一处实现两处配置：StoreJwtFilter 公开白名单（dreamy.security.store-public-paths）与
 * showroom guest 操作白名单（dreamy.security.showroom-guest-paths）共用本匹配器。
 */
public class MethodAwarePathMatcher {

    /** METHOD 前缀合法形态（HTTP method 全大写/全小写字母） */
    private static final Pattern METHOD_PREFIX = Pattern.compile("^[A-Za-z]+$");

    private record Entry(String method, String pattern) {
    }

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();
    private final List<Entry> entries = new ArrayList<>();

    public MethodAwarePathMatcher(List<String> rawEntries) {
        if (rawEntries == null) {
            return;
        }
        for (String raw : rawEntries) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String trimmed = raw.trim();
            int colon = trimmed.indexOf(':');
            if (colon > 0) {
                String prefix = trimmed.substring(0, colon);
                String rest = trimmed.substring(colon + 1);
                if (METHOD_PREFIX.matcher(prefix).matches() && rest.startsWith("/")) {
                    entries.add(new Entry(prefix.toUpperCase(Locale.ROOT), rest));
                    continue;
                }
            }
            // 无 method 前缀：缺省匹配全部 method
            entries.add(new Entry(null, trimmed));
        }
    }

    /** 请求 method+path 是否命中任一白名单条目 */
    public boolean matches(String method, String path) {
        if (method == null || path == null) {
            return false;
        }
        String upper = method.toUpperCase(Locale.ROOT);
        for (Entry entry : entries) {
            if (entry.method() != null && !entry.method().equals(upper)) {
                continue;
            }
            if (antPathMatcher.match(entry.pattern(), path)) {
                return true;
            }
        }
        return false;
    }
}
