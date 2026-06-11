package com.dreamy.marketing.domain.coupon.service;

import com.dreamy.marketing.domain.coupon.entity.Coupon;
import com.dreamy.marketing.domain.coupon.entity.CouponTranslation;
import com.dreamy.marketing.domain.coupon.repository.CouponRepository;
import com.dreamy.marketing.domain.enums.CouponStatus;
import com.dreamy.marketing.error.MarketingErrorCode;
import com.dreamy.marketing.error.MarketingException;
import com.dreamy.marketing.support.Translations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 券域服务实现（SVC-MKT-01）。
 * - validate：可用性判定顺序固定（E-MKT-10 STEP-MKT-02：①不存在/draft/未开始 422701 ②已过期 422701
 *   ③耗尽 422703（优先于门槛）④未达门槛 422702）；通过 → 减免计算（DEC-MKT-4 解析规则）。
 * - redeem：不自启事务（参与 trading TX-TRD-002）；复跑判定防 TOCTOU → redeemCas，affected=0 → 422703
 *   业务性耗尽不重试（EC-MKT-001）。
 * - rollbackRedeem：RM-MKT-108（GREATEST 防负，CV-MKT-010）。
 * L2 TRACE: SVC-MKT-01 / TASK-059 / TC-MKT-001/002/016/017。
 */
@Service
public class CouponDomainServiceImpl implements CouponDomainService {

    private static final Logger log = LoggerFactory.getLogger(CouponDomainServiceImpl.class);

    private final CouponRepository couponRepository;

    public CouponDomainServiceImpl(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Override
    public CouponQuote validate(String code, BigDecimal subtotalUsd, String locale) {
        String normalized = normalizeCode(code);
        Coupon coupon = couponRepository.findByCode(normalized);
        LocalDateTime now = LocalDateTime.now();
        Integer reason = availabilityReason(coupon, subtotalUsd, now);
        if (reason != null) {
            // 不存在不回显任何券信息——不泄露码表（E-MKT-10 STEP-MKT-05）
            CouponBriefView brief = coupon == null ? null : toBrief(coupon, locale);
            return new CouponQuote(false, coupon == null ? null : coupon.getId(), null, false, reason, brief);
        }
        // STEP-MKT-03 减免计算（DEC-MKT-4）
        BigDecimal discount;
        boolean freeShipping = false;
        switch (coupon.getType()) {
            case DISCOUNT -> {
                Integer pct = CouponValueParser.parsePercent(coupon.getValue());
                if (pct == null) {
                    // CV-MKT-009 存量脏数据防御：解析失败按 422701 处置并告警（正常路径不可达）
                    log.error("[SVC-MKT-01] unparseable coupon value coupon_id={} value={}",
                            coupon.getId(), coupon.getValue());
                    return new CouponQuote(false, coupon.getId(), null, false,
                            MarketingErrorCode.COUPON_INVALID.getCode(), toBrief(coupon, locale));
                }
                discount = subtotalUsd.multiply(BigDecimal.valueOf(pct))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
            case FIXED_AMOUNT -> {
                BigDecimal amount = CouponValueParser.parseAmount(coupon.getValue());
                if (amount == null) {
                    log.error("[SVC-MKT-01] unparseable coupon value coupon_id={} value={}",
                            coupon.getId(), coupon.getValue());
                    return new CouponQuote(false, coupon.getId(), null, false,
                            MarketingErrorCode.COUPON_INVALID.getCode(), toBrief(coupon, locale));
                }
                discount = amount.min(subtotalUsd).setScale(2, RoundingMode.HALF_UP);
            }
            default -> {
                discount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                freeShipping = true;
            }
        }
        return new CouponQuote(true, coupon.getId(), discount, freeShipping, null, toBrief(coupon, locale));
    }

    @Override
    public Long redeem(String code, BigDecimal subtotalUsd) {
        String normalized = normalizeCode(code);
        Coupon coupon = couponRepository.findByCode(normalized);
        // 复跑 validate 状态/窗口判定（防 TOCTOU）
        Integer reason = availabilityReason(coupon, subtotalUsd, LocalDateTime.now());
        if (reason != null) {
            throw new MarketingException(byReasonCode(reason));
        }
        // RM-MKT-107 CAS：affected=0 即业务性耗尽（不重试，trading 事务整体回滚——EC-MKT-001）
        int affected = couponRepository.redeemCas(coupon.getId());
        if (affected == 0) {
            throw new MarketingException(MarketingErrorCode.COUPON_EXHAUSTED);
        }
        return coupon.getId();
    }

    @Override
    public void rollbackRedeem(Long couponId) {
        if (couponId == null) {
            return;
        }
        couponRepository.rollbackRedeem(couponId);
    }

    /**
     * 可用性判定（顺序固定，首个命中即返回 reason_code；通过返回 null——E-MKT-10 STEP-MKT-02）：
     * ① 不存在 / draft / scheduled / start_at>now → 422701（未开始）
     * ② expired 或 (end_at 非空且 now>end_at) → 422701（已过期；SCHED 翻转与实时判定双保险 CV-MKT-011）
     * ③ used_count ≥ total_limit → 422703（耗尽优先于门槛，DEC-MKT-5）
     * ④ min_amount > 0 且 subtotal < min_amount → 422702（未达门槛）
     */
    private Integer availabilityReason(Coupon coupon, BigDecimal subtotalUsd, LocalDateTime now) {
        if (coupon == null || coupon.getStatus() == CouponStatus.DRAFT
                || coupon.getStatus() == CouponStatus.SCHEDULED
                || (coupon.getStartAt() != null && coupon.getStartAt().isAfter(now))) {
            return MarketingErrorCode.COUPON_INVALID.getCode();
        }
        if (coupon.getStatus() == CouponStatus.EXPIRED
                || (coupon.getEndAt() != null && now.isAfter(coupon.getEndAt()))) {
            return MarketingErrorCode.COUPON_INVALID.getCode();
        }
        int used = coupon.getUsedCount() == null ? 0 : coupon.getUsedCount();
        int limit = coupon.getTotalLimit() == null ? 100000 : coupon.getTotalLimit();
        if (used >= limit) {
            return MarketingErrorCode.COUPON_EXHAUSTED.getCode();
        }
        if (coupon.getMinAmount() != null && coupon.getMinAmount().signum() > 0
                && subtotalUsd != null && subtotalUsd.compareTo(coupon.getMinAmount()) < 0) {
            return MarketingErrorCode.COUPON_MIN_AMOUNT_NOT_MET.getCode();
        }
        return null;
    }

    /** STEP-MKT-04：coupon.name 按 locale 解析（coupon_translation，缺翻译回退 EN） */
    private CouponBriefView toBrief(Coupon coupon, String locale) {
        String name = coupon.getName();
        if (Translations.needsTranslation(locale)) {
            List<CouponTranslation> rows = couponRepository.listTranslationsByCouponIds(List.of(coupon.getId()));
            for (CouponTranslation row : rows) {
                if (locale.equals(row.getLocale())) {
                    name = Translations.coalesce(row.getName(), name);
                    break;
                }
            }
        }
        return new CouponBriefView(coupon.getCode(), name, coupon.getType().getKey(),
                coupon.getValue(), coupon.getMinAmount());
    }

    private MarketingErrorCode byReasonCode(int reasonCode) {
        if (reasonCode == MarketingErrorCode.COUPON_EXHAUSTED.getCode()) {
            return MarketingErrorCode.COUPON_EXHAUSTED;
        }
        if (reasonCode == MarketingErrorCode.COUPON_MIN_AMOUNT_NOT_MET.getCode()) {
            return MarketingErrorCode.COUPON_MIN_AMOUNT_NOT_MET;
        }
        return MarketingErrorCode.COUPON_INVALID;
    }

    /** CV-MKT-008：code trim+大写归一（判重/点查口径统一） */
    public static String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }
}
