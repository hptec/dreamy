package com.dreamy.support;

import com.dreamy.enums.VoteValue;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * showroom 域入参校验纯函数集（V-SHR-001~023 / CV-SHR-001~003）。
 * 全部静态无状态，错误经 ShowroomFieldErrors 收集映射 422101 fields 结构。
 * L2 TRACE: showroom-api-detail 各端点入参验证段 / showroom-data-detail CV-SHR-001/002/003。
 */
public final class ShowroomValidation {

    /** RFC 5322 实务子集（V-SHR-022，bs-539/363） */
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

    private static final Set<String> LOCALES = Set.of("en", "es", "fr");

    private ShowroomValidation() {
    }

    /** V-SHR-001/006 name 必填，trim 后长度 1..64（bs-353/535） */
    public static String validateName(String name, ShowroomFieldErrors errors) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            errors.reject("name", "blank");
            return null;
        }
        if (trimmed.length() > 64) {
            errors.reject("name", "too_long");
            return null;
        }
        return trimmed;
    }

    /** V-SHR-002/007 wedding_date 可选，ISO yyyy-MM-dd 合法日期（不限制过去日期——原型保真） */
    public static LocalDate validateWeddingDate(String weddingDate, ShowroomFieldErrors errors) {
        if (weddingDate == null || weddingDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(weddingDate.trim());
        } catch (DateTimeParseException ex) {
            errors.reject("wedding_date", "invalid_date");
            return null;
        }
    }

    /** V-SHR-004 locale ∈ {en, es, fr} 缺省 en（枚举外 → 422101 fields.locale=invalid_enum） */
    public static String validateLocale(String locale, ShowroomFieldErrors errors) {
        if (locale == null || locale.isBlank()) {
            return "en";
        }
        String lower = locale.trim().toLowerCase(Locale.ROOT);
        if (!LOCALES.contains(lower)) {
            errors.reject("locale", "invalid_enum");
            return "en";
        }
        return lower;
    }

    /** V-SHR-010 invite_token 必填 ≤64（bs-355/536） */
    public static String validateInviteToken(String token, ShowroomFieldErrors errors) {
        String trimmed = token == null ? "" : token.trim();
        if (trimmed.isEmpty()) {
            errors.reject("invite_token", "required");
            return null;
        }
        if (trimmed.length() > 64) {
            errors.reject("invite_token", "too_long");
            return null;
        }
        return trimmed;
    }

    /** V-SHR-011 nickname 必填，trim 后长度 1..32（bs-362/538） */
    public static String validateNickname(String nickname, ShowroomFieldErrors errors) {
        String trimmed = nickname == null ? "" : nickname.trim();
        if (trimmed.isEmpty()) {
            errors.reject("nickname", "blank");
            return null;
        }
        if (trimmed.length() > 32) {
            errors.reject("nickname", "too_long");
            return null;
        }
        return trimmed;
    }

    /** V-SHR-013 / V-SHR-021 必填正整数 int64（product_id / assigned_item_id） */
    public static Long validateRequiredId(Long id, String field, ShowroomFieldErrors errors) {
        if (id == null) {
            errors.reject(field, "required");
            return null;
        }
        if (id <= 0) {
            errors.reject(field, "invalid");
            return null;
        }
        return id;
    }

    /**
     * V-SHR-014 color 可选 ≤64，trim（bs-537/359）；落库归一化：未提供/trim 空 → 空串 ''
     * （uk 三元唯一生效前提，CV-SHR-003）。
     */
    public static String normalizeColor(String color, ShowroomFieldErrors errors) {
        if (color == null) {
            return "";
        }
        String trimmed = color.trim();
        if (trimmed.length() > 64) {
            errors.reject("color", "too_long");
            return "";
        }
        return trimmed;
    }

    /** V-SHR-017 vote 必填 ∈ {like, dislike}（bs-541/370） */
    public static VoteValue validateVote(Integer vote, ShowroomFieldErrors errors) {
        if (vote == null) {
            errors.reject("vote", "required");
            return null;
        }
        VoteValue value = VoteValue.of(vote);
        if (value == null) {
            errors.reject("vote", "invalid_enum");
            return null;
        }
        return value;
    }

    /** V-SHR-019 content 必填，trim 后长度 1..500（bs-374/542） */
    public static String validateContent(String content, ShowroomFieldErrors errors) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            errors.reject("content", "blank");
            return null;
        }
        if (trimmed.length() > 500) {
            errors.reject("content", "too_long");
            return null;
        }
        return trimmed;
    }

    /** V-SHR-022 email 可选，RFC 5322 格式 + ≤254（bs-539/363）；trim 空视为未提供 */
    public static String validateEmail(String email, ShowroomFieldErrors errors) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 254) {
            errors.reject("email", "too_long");
            return null;
        }
        if (!EMAIL.matcher(trimmed).matches()) {
            errors.reject("email", "invalid_format");
            return null;
        }
        return trimmed;
    }
}
