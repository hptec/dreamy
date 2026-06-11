package com.dreamy.shipping.domain.rate.service;

import java.util.Locale;
import java.util.Map;

/**
 * 收货国家 → 地理区域映射（shipping-api-detail §10.2 提供侧权威表）。
 * 输入规范化：trim + 大写；先按 alpha-2 码精确匹配；非两位码时按国家英文名别名表二次匹配；
 * 仍未识别 → Rest of World。映射表为代码内常量（配置化收益低，变更走发版）。
 * 纯函数静态映射，独立可单测（TC-SHP-001/002）。
 */
public final class GeoZoneResolver {

    public static final String NORTH_AMERICA = "North America";
    public static final String EUROPE = "Europe";
    public static final String OCEANIA = "Oceania";
    public static final String REST_OF_WORLD = "Rest of World";

    /** alpha-2 码 → 区域（§10.2 权威全量展开） */
    private static final Map<String, String> CODE_TO_REGION = Map.ofEntries(
            // North America
            Map.entry("US", NORTH_AMERICA), Map.entry("CA", NORTH_AMERICA), Map.entry("MX", NORTH_AMERICA),
            // Europe
            Map.entry("GB", EUROPE), Map.entry("IE", EUROPE), Map.entry("FR", EUROPE), Map.entry("ES", EUROPE),
            Map.entry("DE", EUROPE), Map.entry("IT", EUROPE), Map.entry("PT", EUROPE), Map.entry("NL", EUROPE),
            Map.entry("BE", EUROPE), Map.entry("LU", EUROPE), Map.entry("AT", EUROPE), Map.entry("CH", EUROPE),
            Map.entry("SE", EUROPE), Map.entry("NO", EUROPE), Map.entry("DK", EUROPE), Map.entry("FI", EUROPE),
            Map.entry("IS", EUROPE), Map.entry("PL", EUROPE), Map.entry("CZ", EUROPE), Map.entry("SK", EUROPE),
            Map.entry("HU", EUROPE), Map.entry("RO", EUROPE), Map.entry("BG", EUROPE), Map.entry("GR", EUROPE),
            Map.entry("HR", EUROPE), Map.entry("SI", EUROPE), Map.entry("EE", EUROPE), Map.entry("LV", EUROPE),
            Map.entry("LT", EUROPE), Map.entry("MT", EUROPE), Map.entry("CY", EUROPE),
            // Oceania
            Map.entry("AU", OCEANIA), Map.entry("NZ", OCEANIA));

    /** 国家英文名别名 → alpha-2 码（覆盖 Address.country 自由文本来源，§10.2 别名表） */
    private static final Map<String, String> NAME_TO_CODE = Map.ofEntries(
            Map.entry("UNITED STATES", "US"), Map.entry("USA", "US"),
            Map.entry("UNITED STATES OF AMERICA", "US"),
            Map.entry("CANADA", "CA"), Map.entry("MEXICO", "MX"),
            Map.entry("UNITED KINGDOM", "GB"), Map.entry("GREAT BRITAIN", "GB"), Map.entry("ENGLAND", "GB"),
            Map.entry("IRELAND", "IE"), Map.entry("FRANCE", "FR"), Map.entry("SPAIN", "ES"),
            Map.entry("GERMANY", "DE"), Map.entry("ITALY", "IT"), Map.entry("PORTUGAL", "PT"),
            Map.entry("NETHERLANDS", "NL"), Map.entry("BELGIUM", "BE"), Map.entry("LUXEMBOURG", "LU"),
            Map.entry("AUSTRIA", "AT"), Map.entry("SWITZERLAND", "CH"), Map.entry("SWEDEN", "SE"),
            Map.entry("NORWAY", "NO"), Map.entry("DENMARK", "DK"), Map.entry("FINLAND", "FI"),
            Map.entry("ICELAND", "IS"), Map.entry("POLAND", "PL"), Map.entry("CZECHIA", "CZ"),
            Map.entry("CZECH REPUBLIC", "CZ"), Map.entry("SLOVAKIA", "SK"), Map.entry("HUNGARY", "HU"),
            Map.entry("ROMANIA", "RO"), Map.entry("BULGARIA", "BG"), Map.entry("GREECE", "GR"),
            Map.entry("CROATIA", "HR"), Map.entry("SLOVENIA", "SI"), Map.entry("ESTONIA", "EE"),
            Map.entry("LATVIA", "LV"), Map.entry("LITHUANIA", "LT"), Map.entry("MALTA", "MT"),
            Map.entry("CYPRUS", "CY"),
            Map.entry("AUSTRALIA", "AU"), Map.entry("NEW ZEALAND", "NZ"));

    private GeoZoneResolver() {
    }

    /** 收货国家（alpha-2 码或英文名）→ 区域文本（zone 前缀，与种子一致）；无法识别 → Rest of World */
    public static String resolve(String country) {
        if (country == null) {
            return REST_OF_WORLD;
        }
        String normalized = country.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return REST_OF_WORLD;
        }
        if (normalized.length() == 2) {
            return CODE_TO_REGION.getOrDefault(normalized, REST_OF_WORLD);
        }
        String code = NAME_TO_CODE.get(normalized);
        if (code != null) {
            return CODE_TO_REGION.getOrDefault(code, REST_OF_WORLD);
        }
        return REST_OF_WORLD;
    }
}
