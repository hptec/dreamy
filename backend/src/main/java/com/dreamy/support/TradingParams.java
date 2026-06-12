package com.dreamy.support;

import com.dreamy.dto.TradingDtos.CustomSizeData;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;

/**
 * store/admin 端查询与业务参数解析校验工具（V-TRD-001/003/005/015/017/023/024/025/030/031/041/043~047 共用；
 * 非法 → TradingFieldErrors 收集 422601）。枚举取值与契约逐字一致（TC-TRD-052）。
 */
public final class TradingParams {

    public static final Set<String> LOCALES = Set.of("en", "es", "fr");
    /** 决策 14：五币种 */
    public static final List<String> CURRENCIES = List.of("USD", "EUR", "CAD", "AUD", "GBP");
    /** F-036：承运商三值全称（= shipping Carrier.name = Order.carrier 快照） */
    public static final List<String> CARRIERS =
            List.of("FedEx International Priority", "UPS Worldwide Express", "DHL Express");
    /** 决策 25：PayPal 置灰不产生数据 */
    public static final List<String> PAYMENT_METHODS =
            List.of("Stripe", "Apple Pay", "Google Pay", "Klarna", "Afterpay");

    private TradingParams() {
    }

    /** V-TRD-001 locale ∈ {en,es,fr} 缺省 en */
    public static String parseLocale(String locale, TradingFieldErrors errors) {
        if (locale == null || locale.isBlank()) {
            return "en";
        }
        if (!LOCALES.contains(locale)) {
            errors.reject("locale", "invalid_enum");
            return "en";
        }
        return locale;
    }

    /** V-TRD-030 page ≥1 缺省 1 */
    public static int parsePage(Integer page, TradingFieldErrors errors) {
        if (page == null) {
            return 1;
        }
        if (page < 1) {
            errors.reject("page", "range_invalid");
            return 1;
        }
        return page;
    }

    /** V-TRD-030 page_size 1..100 缺省 20 */
    public static int parsePageSize(Integer pageSize, TradingFieldErrors errors) {
        if (pageSize == null) {
            return 20;
        }
        if (pageSize < 1 || pageSize > 100) {
            errors.reject("page_size", "range_invalid");
            return 20;
        }
        return pageSize;
    }

    /** trim 后空白归 null */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 必填非空白 + maxLength（V-TRD-011/035/050/053/056 等） */
    public static String requireText(String value, int maxLength, String field, TradingFieldErrors errors) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            errors.reject(field, "required");
            return null;
        }
        if (trimmed.length() > maxLength) {
            errors.reject(field, "too_long");
            return null;
        }
        return trimmed;
    }

    /** 选填 maxLength */
    public static String checkMaxLength(String value, int maxLength, String field, TradingFieldErrors errors) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            errors.reject(field, "too_long");
            return null;
        }
        return trimmed;
    }

    /** V-TRD-015/023 currency ∈ 五币种（违反 → 422605 由调用方抛，本方法仅判定） */
    public static boolean isSupportedCurrency(String currency) {
        return currency != null && CURRENCIES.contains(currency);
    }

    /** V-TRD-017/024 carrier 枚举 */
    public static boolean isSupportedCarrier(String carrier) {
        return carrier != null && CARRIERS.contains(carrier);
    }

    /**
     * V-TRD-005/026/040 custom_size_data 完整性：bust/waist/hips/hollow_to_floor 必填 ≥0，height 选填 ≥0
     * （CV-TRD-006）。
     */
    public static void validateCustomSize(CustomSizeData data, TradingFieldErrors errors) {
        if (data == null) {
            return;
        }
        requireNonNegative(data.bust(), "custom_size_data.bust", errors);
        requireNonNegative(data.waist(), "custom_size_data.waist", errors);
        requireNonNegative(data.hips(), "custom_size_data.hips", errors);
        requireNonNegative(data.hollowToFloor(), "custom_size_data.hollow_to_floor", errors);
        if (data.height() != null && data.height().signum() < 0) {
            errors.reject("custom_size_data.height", "range_invalid");
        }
    }

    private static void requireNonNegative(BigDecimal value, String field, TradingFieldErrors errors) {
        if (value == null) {
            errors.reject(field, "required");
        } else if (value.signum() < 0) {
            errors.reject(field, "range_invalid");
        }
    }

    /**
     * 定制数据规范化哈希（RM-TRD-003 合并判定口径：固定键序规范化 JSON 的 SHA-256；
     * L3 落地为应用计算普通列，语义与 L2 生成列一致——见 trading-backend traceability 注记）。
     */
    public static String customSizeHash(CustomSizeData data) {
        if (data == null) {
            return null;
        }
        String normalized = "{\"bust\":" + plain(data.bust())
                + ",\"waist\":" + plain(data.waist())
                + ",\"hips\":" + plain(data.hips())
                + ",\"hollow_to_floor\":" + plain(data.hollowToFloor())
                + ",\"height\":" + plain(data.height()) + "}";
        return sha256Hex(normalized);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
