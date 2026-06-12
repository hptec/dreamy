package com.dreamy.support;

/**
 * 消费端姓名脱敏（MAP-REV-001/004 规则）：按空格切分，输出「首段 + 末段首字母.」
 * （如 `Madison Reyes` → `Madison R.`；多段取首段+末段首字母；单段名原样；空快照输出 `Guest`）。
 * L2 TRACE: MAP-REV-001 / MAP-REV-004 / TC-REV-001。
 */
public final class NameMasker {

    private static final String FALLBACK = "Guest";

    private NameMasker() {
    }

    public static String mask(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return FALLBACK;
        }
        String[] segments = fullName.trim().split("\\s+");
        if (segments.length == 1) {
            return segments[0];
        }
        String last = segments[segments.length - 1];
        return segments[0] + " " + last.charAt(0) + ".";
    }
}
