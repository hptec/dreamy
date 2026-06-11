package com.dreamy.marketing.support;

/**
 * 翻译回退合并工具（决策 13：ES/FR 命中 translation 附表逐字段覆盖，缺翻译字段回退 EN 主表）。
 * L2 TRACE: MAP-MKT-001~011 / TC-MKT-006。
 */
public final class Translations {

    private Translations() {
    }

    /** 逐字段回退：译文非空白取译文，否则回退 EN 基准值 */
    public static String coalesce(String translated, String fallbackEn) {
        if (translated != null && !translated.isBlank()) {
            return translated;
        }
        return fallbackEn;
    }

    /** 是否需要查询翻译附表（仅 es/fr，EN 直读主表） */
    public static boolean needsTranslation(String locale) {
        return "es".equals(locale) || "fr".equals(locale);
    }
}
