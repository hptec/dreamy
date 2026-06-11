package com.dreamy.marketing.domain.coupon.service;

import com.dreamy.marketing.domain.coupon.entity.Coupon;
import com.dreamy.marketing.domain.enums.CouponStatus;
import com.dreamy.marketing.domain.enums.CouponType;
import com.dreamy.marketing.dto.AdminMarketingDtos.CouponUpsert;
import com.dreamy.marketing.dto.MarketingTranslationDtos.CouponTranslationDto;
import com.dreamy.marketing.support.FieldErrors;
import com.dreamy.marketing.support.MarketingParams;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CouponUpsert 入参校验器（V-MKT-019~027，纯校验——code 唯一性查重归 AdminCouponService 事务内）。
 * L2 TRACE: E-MKT-14/15 入参验证 / CV-MKT-002/003/004/007/008/009/011 / TC-MKT-005。
 */
public final class CouponUpsertValidator {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]+$");

    private CouponUpsertValidator() {
    }

    /** 归一化结果（code 大写、name trim、缺省值落定） */
    public record Normalized(String code, String name, CouponType type, String value, BigDecimal minAmount,
                             Integer totalLimit, CouponStatus status, String description) {
    }

    /**
     * 校验 + 归一化；违反 → 422704 fields 字典。
     *
     * @param existing 编辑时 DB 现行（null=创建）：status 一致性放宽——允许保持 DB 当前
     *                 expiring/expired 原值不动，仅禁止改入该两态（V-MKT-026 / E-MKT-15）
     */
    public static Normalized validate(CouponUpsert req, Coupon existing, LocalDateTime now) {
        FieldErrors errors = new FieldErrors();

        // V-MKT-019 code 必填，大写归一后 ^[A-Z0-9]+$ 且 ≤32（CV-MKT-008）
        String code = CouponDomainServiceImpl.normalizeCode(req.code());
        if (code == null || code.isEmpty()) {
            errors.reject("code", "required");
        } else if (code.length() > 32 || !CODE_PATTERN.matcher(code).matches()) {
            errors.reject("code", "pattern_invalid");
        }

        // V-MKT-020 name 必填 trim 非空 ≤64
        String name = MarketingParams.trimToNull(req.name());
        if (name == null) {
            errors.reject("name", "required");
        } else if (name.length() > 64) {
            errors.reject("name", "too_long");
        }

        // V-MKT-021 type 必填枚举
        CouponType type = CouponType.of(req.type());
        if (type == null) {
            errors.reject("type", "invalid_enum");
        }

        // V-MKT-022 value 必填 ≤32 且按 type 可解析 pattern（DEC-MKT-4）
        String value = req.value();
        if (value == null || value.isBlank()) {
            errors.reject("value", "required");
        } else if (value.length() > 32) {
            errors.reject("value", "too_long");
        } else if (type != null && !CouponValueParser.matchesType(type, value)) {
            errors.reject("value", "unparseable");
        }

        // V-MKT-023 min_amount 可选 ≥0 缺省 0（CV-MKT-003）
        BigDecimal minAmount = req.minAmount() == null ? BigDecimal.ZERO : req.minAmount();
        if (minAmount.signum() < 0) {
            errors.reject("min_amount", "range_invalid");
            minAmount = BigDecimal.ZERO;
        }

        // V-MKT-024 total_limit 可选 ≥0 缺省 100000（DEC-MKT-5）
        Integer totalLimit = req.totalLimit() == null ? 100000 : req.totalLimit();
        if (totalLimit < 0) {
            errors.reject("total_limit", "range_invalid");
            totalLimit = 100000;
        }

        // V-MKT-025 start/end 均给定时 end_at > start_at（CV-MKT-004）
        if (req.startAt() != null && req.endAt() != null && !req.endAt().isAfter(req.startAt())) {
            errors.reject("end_at", "before_start");
        }

        // V-MKT-026 status 必填枚举 + 时间窗一致性（coupon_lifecycle guard 前移为提交校验，CV-MKT-011）
        CouponStatus status = CouponStatus.of(req.status());
        if (status == null) {
            errors.reject("status", "invalid_enum");
        } else {
            boolean keepingTerminal = existing != null && existing.getStatus() == status
                    && (status == CouponStatus.EXPIRING || status == CouponStatus.EXPIRED);
            if (!keepingTerminal) {
                switch (status) {
                    case SCHEDULED -> {
                        if (req.startAt() == null || !req.startAt().isAfter(now)) {
                            errors.reject("status", "inconsistent_with_window");
                        }
                    }
                    case ACTIVE -> {
                        boolean startOk = req.startAt() == null || !req.startAt().isAfter(now);
                        boolean endOk = req.endAt() == null || req.endAt().isAfter(now);
                        if (!startOk || !endOk) {
                            errors.reject("status", "inconsistent_with_window");
                        }
                    }
                    case EXPIRING, EXPIRED -> errors.reject("status", "inconsistent_with_window");
                    default -> {
                        // draft 无窗口约束
                    }
                }
            }
        }

        // DEC-MKT-1 description 可选 ≤255
        String description = MarketingParams.checkMaxLength(req.description(), 255, "description", errors);

        // V-MKT-027 translations locale ∈ {es,fr} 不重复；name ≤64 / description ≤255（CV-MKT-007）
        validateTranslations(req.translations(), errors);

        errors.throwIfAny();
        return new Normalized(code, name, type, value, minAmount, totalLimit, status, description);
    }

    private static void validateTranslations(java.util.List<CouponTranslationDto> translations, FieldErrors errors) {
        if (translations == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (CouponTranslationDto t : translations) {
            if (t.locale() == null || !MarketingParams.TRANSLATION_LOCALES.contains(t.locale())) {
                errors.reject("translations", "invalid_locale");
            } else if (!seen.add(t.locale())) {
                errors.reject("translations", "duplicate_locale");
            }
            if (t.name() != null && t.name().length() > 64) {
                errors.reject("translations", "name_too_long");
            }
            if (t.description() != null && t.description().length() > 255) {
                errors.reject("translations", "description_too_long");
            }
        }
    }
}
