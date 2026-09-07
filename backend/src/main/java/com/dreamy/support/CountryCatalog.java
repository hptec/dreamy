package com.dreamy.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ISO-3166-1 alpha-2 国家目录（order-flow-complete D/G）：全量 249 国 → 英文名 + 8 区 zone；
 * US/CA/AU 州/省字典（code + name + 别名）。纯静态数据，独立可单测；
 * GeoZoneResolver / AddressService / 地址回填器 / StoreShippingController 共用同一权威表。
 * zone 取值：NORTH_AMERICA / EUROPE / UK / OCEANIA / ASIA / LATAM / MEA / REST（与 shipping_option.zone 逐字一致）。
 */
public final class CountryCatalog {

    public static final String ZONE_NORTH_AMERICA = "NORTH_AMERICA";
    public static final String ZONE_EUROPE = "EUROPE";
    public static final String ZONE_UK = "UK";
    public static final String ZONE_OCEANIA = "OCEANIA";
    public static final String ZONE_ASIA = "ASIA";
    public static final String ZONE_LATAM = "LATAM";
    public static final String ZONE_MEA = "MEA";
    public static final String ZONE_REST = "REST";

    public static final List<String> ZONES = List.of(ZONE_NORTH_AMERICA, ZONE_EUROPE, ZONE_UK, ZONE_OCEANIA,
            ZONE_ASIA, ZONE_LATAM, ZONE_MEA, ZONE_REST);

    /** 国家条目（regions 仅 US/CA/AU 非空） */
    public record Country(String code, String name, String zone, List<Region> regions) {
    }

    /** 州/省条目（code = ISO-3166-2 后缀，如 CA / NY / ON / NSW） */
    public record Region(String code, String name, List<String> aliases) {
    }

    private static final Map<String, Country> BY_CODE = new LinkedHashMap<>();
    private static final Map<String, String> NAME_TO_CODE = new HashMap<>();
    private static final Map<String, Map<String, Region>> REGION_INDEX = new HashMap<>();

    static {
        // ===== North America =====
        add("US", "United States", ZONE_NORTH_AMERICA, "USA", "UNITED STATES OF AMERICA", "U.S.", "U.S.A.", "AMERICA");
        add("CA", "Canada", ZONE_NORTH_AMERICA);
        add("MX", "Mexico", ZONE_NORTH_AMERICA, "MÉXICO");
        // ===== UK =====
        add("GB", "United Kingdom", ZONE_UK, "UK", "GREAT BRITAIN", "ENGLAND", "SCOTLAND", "WALES", "NORTHERN IRELAND",
                "BRITAIN");
        add("GG", "Guernsey", ZONE_UK);
        add("JE", "Jersey", ZONE_UK);
        add("IM", "Isle of Man", ZONE_UK);
        // ===== Europe =====
        add("IE", "Ireland", ZONE_EUROPE, "REPUBLIC OF IRELAND");
        add("FR", "France", ZONE_EUROPE);
        add("ES", "Spain", ZONE_EUROPE, "ESPAÑA");
        add("DE", "Germany", ZONE_EUROPE, "DEUTSCHLAND");
        add("IT", "Italy", ZONE_EUROPE, "ITALIA");
        add("PT", "Portugal", ZONE_EUROPE);
        add("NL", "Netherlands", ZONE_EUROPE, "THE NETHERLANDS", "HOLLAND");
        add("BE", "Belgium", ZONE_EUROPE);
        add("LU", "Luxembourg", ZONE_EUROPE);
        add("AT", "Austria", ZONE_EUROPE);
        add("CH", "Switzerland", ZONE_EUROPE);
        add("SE", "Sweden", ZONE_EUROPE);
        add("NO", "Norway", ZONE_EUROPE);
        add("DK", "Denmark", ZONE_EUROPE);
        add("FI", "Finland", ZONE_EUROPE);
        add("IS", "Iceland", ZONE_EUROPE);
        add("PL", "Poland", ZONE_EUROPE);
        add("CZ", "Czechia", ZONE_EUROPE, "CZECH REPUBLIC");
        add("SK", "Slovakia", ZONE_EUROPE);
        add("HU", "Hungary", ZONE_EUROPE);
        add("RO", "Romania", ZONE_EUROPE);
        add("BG", "Bulgaria", ZONE_EUROPE);
        add("GR", "Greece", ZONE_EUROPE);
        add("HR", "Croatia", ZONE_EUROPE);
        add("SI", "Slovenia", ZONE_EUROPE);
        add("EE", "Estonia", ZONE_EUROPE);
        add("LV", "Latvia", ZONE_EUROPE);
        add("LT", "Lithuania", ZONE_EUROPE);
        add("MT", "Malta", ZONE_EUROPE);
        add("CY", "Cyprus", ZONE_EUROPE);
        add("LI", "Liechtenstein", ZONE_EUROPE);
        add("MC", "Monaco", ZONE_EUROPE);
        add("AD", "Andorra", ZONE_EUROPE);
        add("SM", "San Marino", ZONE_EUROPE);
        add("VA", "Holy See", ZONE_EUROPE, "VATICAN", "VATICAN CITY");
        add("AL", "Albania", ZONE_EUROPE);
        add("BA", "Bosnia and Herzegovina", ZONE_EUROPE, "BOSNIA");
        add("ME", "Montenegro", ZONE_EUROPE);
        add("MK", "North Macedonia", ZONE_EUROPE, "MACEDONIA");
        add("RS", "Serbia", ZONE_EUROPE);
        add("XK", "Kosovo", ZONE_EUROPE);
        add("UA", "Ukraine", ZONE_EUROPE);
        add("MD", "Moldova", ZONE_EUROPE, "REPUBLIC OF MOLDOVA");
        add("BY", "Belarus", ZONE_EUROPE);
        add("FO", "Faroe Islands", ZONE_EUROPE);
        add("GI", "Gibraltar", ZONE_EUROPE);
        add("AX", "Åland Islands", ZONE_EUROPE, "ALAND ISLANDS");
        add("SJ", "Svalbard and Jan Mayen", ZONE_EUROPE);
        add("GL", "Greenland", ZONE_EUROPE);
        // ===== Oceania =====
        add("AU", "Australia", ZONE_OCEANIA);
        add("NZ", "New Zealand", ZONE_OCEANIA);
        add("FJ", "Fiji", ZONE_OCEANIA);
        add("PG", "Papua New Guinea", ZONE_OCEANIA);
        add("WS", "Samoa", ZONE_OCEANIA);
        add("TO", "Tonga", ZONE_OCEANIA);
        add("VU", "Vanuatu", ZONE_OCEANIA);
        add("SB", "Solomon Islands", ZONE_OCEANIA);
        add("NC", "New Caledonia", ZONE_OCEANIA);
        add("PF", "French Polynesia", ZONE_OCEANIA);
        add("GU", "Guam", ZONE_OCEANIA);
        add("MP", "Northern Mariana Islands", ZONE_OCEANIA);
        add("AS", "American Samoa", ZONE_OCEANIA);
        add("CK", "Cook Islands", ZONE_OCEANIA);
        add("NU", "Niue", ZONE_OCEANIA);
        add("TK", "Tokelau", ZONE_OCEANIA);
        add("TV", "Tuvalu", ZONE_OCEANIA);
        add("KI", "Kiribati", ZONE_OCEANIA);
        add("NR", "Nauru", ZONE_OCEANIA);
        add("MH", "Marshall Islands", ZONE_OCEANIA);
        add("FM", "Micronesia", ZONE_OCEANIA, "FEDERATED STATES OF MICRONESIA");
        add("PW", "Palau", ZONE_OCEANIA);
        add("WF", "Wallis and Futuna", ZONE_OCEANIA);
        add("PN", "Pitcairn", ZONE_OCEANIA, "PITCAIRN ISLANDS");
        add("NF", "Norfolk Island", ZONE_OCEANIA);
        add("CX", "Christmas Island", ZONE_OCEANIA);
        add("CC", "Cocos (Keeling) Islands", ZONE_OCEANIA, "COCOS ISLANDS");
        add("HM", "Heard Island and McDonald Islands", ZONE_OCEANIA);
        add("UM", "United States Minor Outlying Islands", ZONE_OCEANIA);
        // ===== Asia =====
        add("CN", "China", ZONE_ASIA, "PEOPLE'S REPUBLIC OF CHINA", "PRC");
        add("HK", "Hong Kong", ZONE_ASIA, "HONG KONG SAR");
        add("MO", "Macao", ZONE_ASIA, "MACAU");
        add("TW", "Taiwan", ZONE_ASIA);
        add("JP", "Japan", ZONE_ASIA);
        add("KR", "South Korea", ZONE_ASIA, "KOREA", "REPUBLIC OF KOREA", "KOREA, REPUBLIC OF");
        add("KP", "North Korea", ZONE_ASIA, "DEMOCRATIC PEOPLE'S REPUBLIC OF KOREA");
        add("MN", "Mongolia", ZONE_ASIA);
        add("SG", "Singapore", ZONE_ASIA);
        add("MY", "Malaysia", ZONE_ASIA);
        add("TH", "Thailand", ZONE_ASIA);
        add("VN", "Vietnam", ZONE_ASIA, "VIET NAM");
        add("PH", "Philippines", ZONE_ASIA, "THE PHILIPPINES");
        add("ID", "Indonesia", ZONE_ASIA);
        add("BN", "Brunei", ZONE_ASIA, "BRUNEI DARUSSALAM");
        add("KH", "Cambodia", ZONE_ASIA);
        add("LA", "Laos", ZONE_ASIA, "LAO PEOPLE'S DEMOCRATIC REPUBLIC");
        add("MM", "Myanmar", ZONE_ASIA, "BURMA");
        add("TL", "Timor-Leste", ZONE_ASIA, "EAST TIMOR");
        add("IN", "India", ZONE_ASIA);
        add("PK", "Pakistan", ZONE_ASIA);
        add("BD", "Bangladesh", ZONE_ASIA);
        add("LK", "Sri Lanka", ZONE_ASIA);
        add("NP", "Nepal", ZONE_ASIA);
        add("BT", "Bhutan", ZONE_ASIA);
        add("MV", "Maldives", ZONE_ASIA);
        add("AF", "Afghanistan", ZONE_ASIA);
        add("KZ", "Kazakhstan", ZONE_ASIA);
        add("UZ", "Uzbekistan", ZONE_ASIA);
        add("KG", "Kyrgyzstan", ZONE_ASIA);
        add("TJ", "Tajikistan", ZONE_ASIA);
        add("TM", "Turkmenistan", ZONE_ASIA);
        add("RU", "Russia", ZONE_ASIA, "RUSSIAN FEDERATION");
        add("GE", "Georgia", ZONE_ASIA);
        add("AM", "Armenia", ZONE_ASIA);
        add("AZ", "Azerbaijan", ZONE_ASIA);
        add("IO", "British Indian Ocean Territory", ZONE_ASIA);
        // ===== Latin America & Caribbean =====
        add("BR", "Brazil", ZONE_LATAM, "BRASIL");
        add("AR", "Argentina", ZONE_LATAM);
        add("CL", "Chile", ZONE_LATAM);
        add("CO", "Colombia", ZONE_LATAM);
        add("PE", "Peru", ZONE_LATAM, "PERÚ");
        add("VE", "Venezuela", ZONE_LATAM);
        add("EC", "Ecuador", ZONE_LATAM);
        add("BO", "Bolivia", ZONE_LATAM);
        add("PY", "Paraguay", ZONE_LATAM);
        add("UY", "Uruguay", ZONE_LATAM);
        add("GY", "Guyana", ZONE_LATAM);
        add("SR", "Suriname", ZONE_LATAM);
        add("GF", "French Guiana", ZONE_LATAM);
        add("FK", "Falkland Islands", ZONE_LATAM, "MALVINAS");
        add("GS", "South Georgia and the South Sandwich Islands", ZONE_LATAM);
        add("GT", "Guatemala", ZONE_LATAM);
        add("BZ", "Belize", ZONE_LATAM);
        add("HN", "Honduras", ZONE_LATAM);
        add("SV", "El Salvador", ZONE_LATAM);
        add("NI", "Nicaragua", ZONE_LATAM);
        add("CR", "Costa Rica", ZONE_LATAM);
        add("PA", "Panama", ZONE_LATAM);
        add("CU", "Cuba", ZONE_LATAM);
        add("DO", "Dominican Republic", ZONE_LATAM);
        add("HT", "Haiti", ZONE_LATAM);
        add("JM", "Jamaica", ZONE_LATAM);
        add("PR", "Puerto Rico", ZONE_LATAM);
        add("BS", "Bahamas", ZONE_LATAM, "THE BAHAMAS");
        add("BB", "Barbados", ZONE_LATAM);
        add("TT", "Trinidad and Tobago", ZONE_LATAM);
        add("AG", "Antigua and Barbuda", ZONE_LATAM);
        add("DM", "Dominica", ZONE_LATAM);
        add("GD", "Grenada", ZONE_LATAM);
        add("KN", "Saint Kitts and Nevis", ZONE_LATAM, "ST KITTS AND NEVIS");
        add("LC", "Saint Lucia", ZONE_LATAM, "ST LUCIA");
        add("VC", "Saint Vincent and the Grenadines", ZONE_LATAM, "ST VINCENT AND THE GRENADINES");
        add("AI", "Anguilla", ZONE_LATAM);
        add("AW", "Aruba", ZONE_LATAM);
        add("BM", "Bermuda", ZONE_LATAM);
        add("BQ", "Bonaire, Sint Eustatius and Saba", ZONE_LATAM, "CARIBBEAN NETHERLANDS");
        add("CW", "Curaçao", ZONE_LATAM, "CURACAO");
        add("SX", "Sint Maarten", ZONE_LATAM);
        add("MF", "Saint Martin", ZONE_LATAM, "ST MARTIN");
        add("BL", "Saint Barthélemy", ZONE_LATAM, "SAINT BARTHELEMY", "ST BARTS");
        add("GP", "Guadeloupe", ZONE_LATAM);
        add("MQ", "Martinique", ZONE_LATAM);
        add("KY", "Cayman Islands", ZONE_LATAM);
        add("TC", "Turks and Caicos Islands", ZONE_LATAM);
        add("VG", "British Virgin Islands", ZONE_LATAM, "VIRGIN ISLANDS, BRITISH");
        add("VI", "U.S. Virgin Islands", ZONE_LATAM, "US VIRGIN ISLANDS", "VIRGIN ISLANDS, U.S.");
        add("MS", "Montserrat", ZONE_LATAM);
        add("PM", "Saint Pierre and Miquelon", ZONE_LATAM, "ST PIERRE AND MIQUELON");
        // ===== Middle East & Africa =====
        add("AE", "United Arab Emirates", ZONE_MEA, "UAE");
        add("SA", "Saudi Arabia", ZONE_MEA);
        add("QA", "Qatar", ZONE_MEA);
        add("KW", "Kuwait", ZONE_MEA);
        add("BH", "Bahrain", ZONE_MEA);
        add("OM", "Oman", ZONE_MEA);
        add("YE", "Yemen", ZONE_MEA);
        add("IL", "Israel", ZONE_MEA);
        add("PS", "Palestine", ZONE_MEA, "STATE OF PALESTINE");
        add("JO", "Jordan", ZONE_MEA);
        add("LB", "Lebanon", ZONE_MEA);
        add("SY", "Syria", ZONE_MEA, "SYRIAN ARAB REPUBLIC");
        add("IQ", "Iraq", ZONE_MEA);
        add("IR", "Iran", ZONE_MEA, "ISLAMIC REPUBLIC OF IRAN");
        add("TR", "Türkiye", ZONE_MEA, "TURKEY", "TURKIYE");
        add("EG", "Egypt", ZONE_MEA);
        add("LY", "Libya", ZONE_MEA);
        add("TN", "Tunisia", ZONE_MEA);
        add("DZ", "Algeria", ZONE_MEA);
        add("MA", "Morocco", ZONE_MEA);
        add("EH", "Western Sahara", ZONE_MEA);
        add("SD", "Sudan", ZONE_MEA);
        add("SS", "South Sudan", ZONE_MEA);
        add("ET", "Ethiopia", ZONE_MEA);
        add("ER", "Eritrea", ZONE_MEA);
        add("DJ", "Djibouti", ZONE_MEA);
        add("SO", "Somalia", ZONE_MEA);
        add("KE", "Kenya", ZONE_MEA);
        add("UG", "Uganda", ZONE_MEA);
        add("TZ", "Tanzania", ZONE_MEA, "UNITED REPUBLIC OF TANZANIA");
        add("RW", "Rwanda", ZONE_MEA);
        add("BI", "Burundi", ZONE_MEA);
        add("NG", "Nigeria", ZONE_MEA);
        add("GH", "Ghana", ZONE_MEA);
        add("CI", "Côte d'Ivoire", ZONE_MEA, "COTE D'IVOIRE", "IVORY COAST");
        add("SN", "Senegal", ZONE_MEA);
        add("ML", "Mali", ZONE_MEA);
        add("BF", "Burkina Faso", ZONE_MEA);
        add("NE", "Niger", ZONE_MEA);
        add("TD", "Chad", ZONE_MEA);
        add("CM", "Cameroon", ZONE_MEA);
        add("BJ", "Benin", ZONE_MEA);
        add("TG", "Togo", ZONE_MEA);
        add("GN", "Guinea", ZONE_MEA);
        add("GW", "Guinea-Bissau", ZONE_MEA);
        add("SL", "Sierra Leone", ZONE_MEA);
        add("LR", "Liberia", ZONE_MEA);
        add("GM", "Gambia", ZONE_MEA, "THE GAMBIA");
        add("MR", "Mauritania", ZONE_MEA);
        add("CV", "Cabo Verde", ZONE_MEA, "CAPE VERDE");
        add("ST", "Sao Tome and Principe", ZONE_MEA, "SÃO TOMÉ AND PRÍNCIPE");
        add("GQ", "Equatorial Guinea", ZONE_MEA);
        add("GA", "Gabon", ZONE_MEA);
        add("CG", "Congo", ZONE_MEA, "REPUBLIC OF THE CONGO", "CONGO-BRAZZAVILLE");
        add("CD", "Democratic Republic of the Congo", ZONE_MEA, "DR CONGO", "CONGO-KINSHASA", "DRC");
        add("CF", "Central African Republic", ZONE_MEA);
        add("AO", "Angola", ZONE_MEA);
        add("ZM", "Zambia", ZONE_MEA);
        add("ZW", "Zimbabwe", ZONE_MEA);
        add("MW", "Malawi", ZONE_MEA);
        add("MZ", "Mozambique", ZONE_MEA);
        add("MG", "Madagascar", ZONE_MEA);
        add("MU", "Mauritius", ZONE_MEA);
        add("SC", "Seychelles", ZONE_MEA);
        add("KM", "Comoros", ZONE_MEA);
        add("RE", "Réunion", ZONE_MEA, "REUNION");
        add("YT", "Mayotte", ZONE_MEA);
        add("ZA", "South Africa", ZONE_MEA);
        add("NA", "Namibia", ZONE_MEA);
        add("BW", "Botswana", ZONE_MEA);
        add("LS", "Lesotho", ZONE_MEA);
        add("SZ", "Eswatini", ZONE_MEA, "SWAZILAND");
        add("SH", "Saint Helena, Ascension and Tristan da Cunha", ZONE_MEA, "SAINT HELENA", "ST HELENA");
        add("TF", "French Southern Territories", ZONE_MEA);
        add("BV", "Bouvet Island", ZONE_MEA);
        // ===== Rest =====
        add("AQ", "Antarctica", ZONE_REST);

        // ===== 州/省字典 =====
        regions("US",
                r("AL", "Alabama"), r("AK", "Alaska"), r("AZ", "Arizona"), r("AR", "Arkansas"),
                r("CA", "California", "CALIF"), r("CO", "Colorado"), r("CT", "Connecticut"), r("DE", "Delaware"),
                r("DC", "District of Columbia", "WASHINGTON DC", "WASHINGTON D.C.", "D.C."), r("FL", "Florida"),
                r("GA", "Georgia"), r("HI", "Hawaii"), r("ID", "Idaho"), r("IL", "Illinois"), r("IN", "Indiana"),
                r("IA", "Iowa"), r("KS", "Kansas"), r("KY", "Kentucky"), r("LA", "Louisiana"), r("ME", "Maine"),
                r("MD", "Maryland"), r("MA", "Massachusetts", "MASS"), r("MI", "Michigan"), r("MN", "Minnesota"),
                r("MS", "Mississippi"), r("MO", "Missouri"), r("MT", "Montana"), r("NE", "Nebraska"),
                r("NV", "Nevada"), r("NH", "New Hampshire"), r("NJ", "New Jersey"), r("NM", "New Mexico"),
                r("NY", "New York"), r("NC", "North Carolina"), r("ND", "North Dakota"), r("OH", "Ohio"),
                r("OK", "Oklahoma"), r("OR", "Oregon"), r("PA", "Pennsylvania", "PENN"), r("RI", "Rhode Island"),
                r("SC", "South Carolina"), r("SD", "South Dakota"), r("TN", "Tennessee"), r("TX", "Texas"),
                r("UT", "Utah"), r("VT", "Vermont"), r("VA", "Virginia"), r("WA", "Washington"),
                r("WV", "West Virginia"), r("WI", "Wisconsin"), r("WY", "Wyoming"),
                r("PR", "Puerto Rico"), r("GU", "Guam"), r("VI", "U.S. Virgin Islands", "VIRGIN ISLANDS"),
                r("AS", "American Samoa"), r("MP", "Northern Mariana Islands"));
        regions("CA",
                r("AB", "Alberta"), r("BC", "British Columbia"), r("MB", "Manitoba"), r("NB", "New Brunswick"),
                r("NL", "Newfoundland and Labrador", "NEWFOUNDLAND"), r("NS", "Nova Scotia"),
                r("NT", "Northwest Territories"), r("NU", "Nunavut"), r("ON", "Ontario"),
                r("PE", "Prince Edward Island", "PEI"), r("QC", "Quebec", "QUÉBEC", "PQ"), r("SK", "Saskatchewan"),
                r("YT", "Yukon", "YUKON TERRITORY"));
        regions("AU",
                r("NSW", "New South Wales"), r("VIC", "Victoria"), r("QLD", "Queensland"),
                r("SA", "South Australia"), r("WA", "Western Australia"), r("TAS", "Tasmania"),
                r("NT", "Northern Territory"), r("ACT", "Australian Capital Territory", "CANBERRA"));
    }

    private CountryCatalog() {
    }

    private static void add(String code, String name, String zone, String... aliases) {
        BY_CODE.put(code, new Country(code, name, zone, List.of()));
        NAME_TO_CODE.put(norm(name), code);
        for (String alias : aliases) {
            NAME_TO_CODE.put(norm(alias), code);
        }
    }

    private static Region r(String code, String name, String... aliases) {
        return new Region(code, name, List.of(aliases));
    }

    private static void regions(String countryCode, Region... regions) {
        Country existing = BY_CODE.get(countryCode);
        List<Region> list = List.of(regions);
        BY_CODE.put(countryCode, new Country(existing.code(), existing.name(), existing.zone(), list));
        Map<String, Region> index = new HashMap<>();
        for (Region region : list) {
            index.put(norm(region.code()), region);
            index.put(norm(region.name()), region);
            for (String alias : region.aliases()) {
                index.put(norm(alias), region);
            }
        }
        REGION_INDEX.put(countryCode, index);
    }

    /** 规范化：trim + 大写 + 连续空白折叠 + 去掉末尾句点 */
    private static String norm(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }

    /** 全量国家（插入顺序：北美 → UK → 欧洲 → 大洋洲 → 亚洲 → 拉美 → 中东非洲 → 其他） */
    public static List<Country> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_CODE.values()));
    }

    /** 按 alpha-2 码点查（大小写不敏感；未知 → null） */
    public static Country byCode(String code) {
        if (code == null) {
            return null;
        }
        return BY_CODE.get(code.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean isKnownCode(String code) {
        return byCode(code) != null;
    }

    /**
     * 国家自由文本 → alpha-2 码：两位码精确匹配优先；否则英文名/别名匹配；无法解析 → null。
     */
    public static String resolveCode(String countryText) {
        if (countryText == null) {
            return null;
        }
        String n = norm(countryText);
        if (n.isEmpty()) {
            return null;
        }
        if (n.length() == 2 && BY_CODE.containsKey(n)) {
            return n;
        }
        return NAME_TO_CODE.get(n);
    }

    /** country_code → zone（未知码 → REST） */
    public static String zoneOf(String countryCode) {
        Country country = byCode(countryCode);
        return country == null ? ZONE_REST : country.zone();
    }

    /** 是否有州/省字典（US/CA/AU） */
    public static boolean hasRegions(String countryCode) {
        return countryCode != null && REGION_INDEX.containsKey(countryCode.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * 州/省文本（缩写/全称/别名）→ 标准码；国家无字典或无法解析 → null。
     */
    public static String resolveRegionCode(String countryCode, String regionText) {
        if (countryCode == null || regionText == null) {
            return null;
        }
        Map<String, Region> index = REGION_INDEX.get(countryCode.trim().toUpperCase(Locale.ROOT));
        if (index == null) {
            return null;
        }
        Region region = index.get(norm(regionText));
        return region == null ? null : region.code();
    }

    /** 州/省字典（无字典 → 空列表） */
    public static List<Region> regionsOf(String countryCode) {
        Country country = byCode(countryCode);
        return country == null ? List.of() : country.regions();
    }
}
