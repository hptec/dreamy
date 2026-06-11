package com.dreamy.shipping.domain.rate.service;

/**
 * zone 文本规范化（DEC-SHP-1）：trim 首尾空白 + 连续空白折叠为单空格；
 * 唯一性比较（409901）与报价匹配（SVC-SHP-01）一律对规范化文本做忽略大小写比较；
 * 存储保留规范化后原始大小写。纯函数，独立可单测（TC-SHP-009）。
 */
public final class ZoneNormalizer {

    private ZoneNormalizer() {
    }

    /** 规范化：trim + 连续空白折叠单空格；null → null */
    public static String normalize(String zone) {
        if (zone == null) {
            return null;
        }
        return zone.trim().replaceAll("\\s+", " ");
    }

    /** 忽略大小写等值比较（入参先各自规范化） */
    public static boolean sameZone(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na == null || nb == null) {
            return na == nb;
        }
        return na.equalsIgnoreCase(nb);
    }
}
