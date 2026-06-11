package com.dreamy.marketing.domain.coupon.service;

import com.dreamy.marketing.domain.enums.CouponType;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * coupon.value 可解析约定（DEC-MKT-4，纯函数）：
 * discount → `^\d{1,3}% OFF$`（取百分数）；fixed_amount → `^\$\d+(\.\d{1,2})? OFF$`（取金额）；
 * free_shipping → 任意 ≤32 文案（不解析，discount_amount=0）。
 * L2 TRACE: V-MKT-022 / CV-MKT-009 / TC-MKT-002/003。
 */
public final class CouponValueParser {

    private static final Pattern DISCOUNT_PATTERN = Pattern.compile("^(\\d{1,3})% OFF$");
    private static final Pattern FIXED_PATTERN = Pattern.compile("^\\$(\\d+(?:\\.\\d{1,2})?) OFF$");

    private CouponValueParser() {
    }

    /** V-MKT-022：value 按 type pattern 可解析（free_shipping 任意 ≤32 由长度校验承载） */
    public static boolean matchesType(CouponType type, String value) {
        if (type == null || value == null) {
            return false;
        }
        return switch (type) {
            case DISCOUNT -> DISCOUNT_PATTERN.matcher(value).matches();
            case FIXED_AMOUNT -> FIXED_PATTERN.matcher(value).matches();
            case FREE_SHIPPING -> true;
        };
    }

    /** discount 取百分数（'15% OFF' → 15）；不可解析 → null（CV-MKT-009 存量脏数据防御） */
    public static Integer parsePercent(String value) {
        if (value == null) {
            return null;
        }
        Matcher m = DISCOUNT_PATTERN.matcher(value);
        return m.matches() ? Integer.valueOf(m.group(1)) : null;
    }

    /** fixed_amount 取金额（'$50 OFF' → 50）；不可解析 → null */
    public static BigDecimal parseAmount(String value) {
        if (value == null) {
            return null;
        }
        Matcher m = FIXED_PATTERN.matcher(value);
        return m.matches() ? new BigDecimal(m.group(1)) : null;
    }
}
