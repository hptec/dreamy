package com.dreamy.domain.shippingrate.service;

import com.dreamy.support.CountryCatalog;

import java.util.Locale;
import java.util.Map;

/**
 * 收货国家 → 地理区域映射（order-flow-complete D：ISO-3166 全量 8 区；权威表 {@link CountryCatalog}）。
 * 输入规范化：trim + 大写；country_code（alpha-2）精确匹配优先；非两位码时按国家英文名/别名兜底；
 * 仍未识别 → REST。zone 取值 NORTH_AMERICA/EUROPE/UK/OCEANIA/ASIA/LATAM/MEA/REST（与 shipping_option.zone 一致）。
 * 兼容旧 zone 名（shipping_rate 时代 "North America"/"Europe"/"Oceania"/"Rest of World"）：{@link #canonicalZone(String)}。
 * 纯函数静态映射，独立可单测。
 */
public final class GeoZoneResolver {

    public static final String NORTH_AMERICA = CountryCatalog.ZONE_NORTH_AMERICA;
    public static final String EUROPE = CountryCatalog.ZONE_EUROPE;
    public static final String UK = CountryCatalog.ZONE_UK;
    public static final String OCEANIA = CountryCatalog.ZONE_OCEANIA;
    public static final String ASIA = CountryCatalog.ZONE_ASIA;
    public static final String LATAM = CountryCatalog.ZONE_LATAM;
    public static final String MEA = CountryCatalog.ZONE_MEA;
    public static final String REST = CountryCatalog.ZONE_REST;
    /** 旧常量名保留（语义 = REST） */
    public static final String REST_OF_WORLD = REST;

    /** 旧 zone 名 / 前缀 → 新 zone 码（大小写不敏感） */
    private static final Map<String, String> LEGACY_ZONE_NAMES = Map.ofEntries(
            Map.entry("NORTH AMERICA", NORTH_AMERICA),
            Map.entry("EUROPE", EUROPE),
            Map.entry("UNITED KINGDOM", UK),
            Map.entry("UK", UK),
            Map.entry("OCEANIA", OCEANIA),
            Map.entry("ASIA", ASIA),
            Map.entry("ASIA PACIFIC", ASIA),
            Map.entry("LATIN AMERICA", LATAM),
            Map.entry("LATAM", LATAM),
            Map.entry("MIDDLE EAST", MEA),
            Map.entry("MIDDLE EAST & AFRICA", MEA),
            Map.entry("MEA", MEA),
            Map.entry("AFRICA", MEA),
            Map.entry("REST OF WORLD", REST),
            Map.entry("REST OF THE WORLD", REST),
            Map.entry("ROW", REST),
            Map.entry("REST", REST));

    private GeoZoneResolver() {
    }

    /** 收货国家（alpha-2 码或英文名/别名）→ zone 码；无法识别 → REST */
    public static String resolve(String country) {
        String code = CountryCatalog.resolveCode(country);
        return code == null ? REST : CountryCatalog.zoneOf(code);
    }

    /** country_code 优先、country 文本兜底 */
    public static String resolve(String countryCode, String countryText) {
        if (countryCode != null && CountryCatalog.isKnownCode(countryCode)) {
            return CountryCatalog.zoneOf(countryCode);
        }
        return resolve(countryText);
    }

    /**
     * zone 文本规范化为新 zone 码：新码直接通过；旧 zone 名（含 "<区域> / <承运商>" 前缀形式）映射；
     * 无法识别 → null。
     */
    public static String canonicalZone(String zone) {
        String normalized = ZoneNormalizer.normalize(zone);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (CountryCatalog.ZONES.contains(upper)) {
            return upper;
        }
        int slash = upper.indexOf(" / ");
        String prefix = slash > 0 ? upper.substring(0, slash).trim() : upper;
        return LEGACY_ZONE_NAMES.get(prefix);
    }

    /** 旧规则行 "<区域> / <承运商名>" 的承运商名后缀（无后缀 → null） */
    public static String legacyCarrierSuffix(String zone) {
        String normalized = ZoneNormalizer.normalize(zone);
        if (normalized == null) {
            return null;
        }
        int slash = normalized.indexOf(" / ");
        return slash > 0 ? normalized.substring(slash + 3).trim() : null;
    }

    public static boolean isValidZone(String zone) {
        return zone != null && CountryCatalog.ZONES.contains(zone);
    }
}
