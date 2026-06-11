package com.dreamy.marketing.domain.coupon.service;

import com.dreamy.marketing.domain.coupon.entity.Coupon;
import com.dreamy.marketing.domain.enums.CouponStatus;
import com.dreamy.marketing.domain.enums.CouponType;
import com.dreamy.marketing.dto.AdminMarketingDtos.CouponUpsert;
import com.dreamy.marketing.error.MarketingErrorCode;
import com.dreamy.marketing.error.MarketingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CouponUpsert 校验器单元测试。
 * L2 TRACE: TC-MKT-003（value pattern 按 type）/ TC-MKT-005（status-时间窗一致性）/ V-MKT-019~027。
 */
class CouponUpsertValidatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 10, 12, 0);

    private static CouponUpsert upsert(String type, String value, String status,
                                       LocalDateTime startAt, LocalDateTime endAt) {
        return new CouponUpsert("WELCOME15", "Welcome", type, value, BigDecimal.ZERO, 100,
                startAt, endAt, status, null, List.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> fieldsOf(MarketingException ex) {
        return (Map<String, String>) ex.getDetails().get("fields");
    }

    @Test
    @DisplayName("TC-MKT-003 [P0]: value pattern 按 type——discount 拒绝 '$50 OFF'，fixed 拒绝 '15% OFF'，free_shipping 任意 ≤32 通过")
    void valuePatternByType() {
        assertThatThrownBy(() -> CouponUpsertValidator.validate(
                upsert("discount", "$50 OFF", "draft", null, null), null, NOW))
                .isInstanceOf(MarketingException.class)
                .satisfies(ex -> assertThat(fieldsOf((MarketingException) ex)).containsEntry("value", "unparseable"));

        assertThatThrownBy(() -> CouponUpsertValidator.validate(
                upsert("fixed_amount", "15% OFF", "draft", null, null), null, NOW))
                .isInstanceOf(MarketingException.class)
                .satisfies(ex -> assertThat(fieldsOf((MarketingException) ex)).containsEntry("value", "unparseable"));

        var ok = CouponUpsertValidator.validate(
                upsert("free_shipping", "Free Shipping", "draft", null, null), null, NOW);
        assertThat(ok.type()).isEqualTo(CouponType.FREE_SHIPPING);

        var pct = CouponUpsertValidator.validate(
                upsert("discount", "15% OFF", "draft", null, null), null, NOW);
        assertThat(pct.value()).isEqualTo("15% OFF");
    }

    @Test
    @DisplayName("TC-MKT-005 [P0]: scheduled 要求 start_at>now；active 要求窗口内；expiring/expired 创建禁入 → 422704")
    void statusWindowConsistency() {
        // scheduled 但 start_at 过去 → inconsistent
        assertThatThrownBy(() -> CouponUpsertValidator.validate(
                upsert("discount", "15% OFF", "scheduled", NOW.minusDays(1), NOW.plusDays(1)), null, NOW))
                .satisfies(ex -> assertThat(fieldsOf((MarketingException) ex))
                        .containsEntry("status", "inconsistent_with_window"));
        // scheduled 合法
        var scheduled = CouponUpsertValidator.validate(
                upsert("discount", "15% OFF", "scheduled", NOW.plusDays(1), NOW.plusDays(2)), null, NOW);
        assertThat(scheduled.status()).isEqualTo(CouponStatus.SCHEDULED);
        // active 窗口内合法（空端开放）
        var active = CouponUpsertValidator.validate(
                upsert("discount", "15% OFF", "active", null, NOW.plusDays(1)), null, NOW);
        assertThat(active.status()).isEqualTo(CouponStatus.ACTIVE);
        // active 但 end_at 过去 → inconsistent
        assertThatThrownBy(() -> CouponUpsertValidator.validate(
                upsert("discount", "15% OFF", "active", NOW.minusDays(2), NOW.minusDays(1)), null, NOW))
                .satisfies(ex -> assertThat(fieldsOf((MarketingException) ex))
                        .containsEntry("status", "inconsistent_with_window"));
        // expiring/expired 创建禁入
        for (String s : List.of("expiring", "expired")) {
            assertThatThrownBy(() -> CouponUpsertValidator.validate(
                    upsert("discount", "15% OFF", s, NOW.minusDays(1), NOW.plusDays(1)), null, NOW))
                    .satisfies(ex -> assertThat(fieldsOf((MarketingException) ex))
                            .containsEntry("status", "inconsistent_with_window"));
        }
    }

    @Test
    @DisplayName("V-MKT-026 编辑放宽 [P0]: 允许保持 DB 当前 expired 原值不动，仅禁止改入")
    void keepTerminalStatusOnUpdate() {
        Coupon existing = new Coupon();
        existing.setId(1L);
        existing.setStatus(CouponStatus.EXPIRED);
        var kept = CouponUpsertValidator.validate(
                upsert("discount", "15% OFF", "expired", NOW.minusDays(10), NOW.minusDays(1)), existing, NOW);
        assertThat(kept.status()).isEqualTo(CouponStatus.EXPIRED);
    }

    @Test
    @DisplayName("V-MKT-019/025 [P0]: code 大写归一 + pattern；end_at≤start_at → fields.end_at=before_start")
    void codeAndWindowValidation() {
        var normalized = CouponUpsertValidator.validate(new CouponUpsert(" welcome15 ", "Welcome", "discount",
                "15% OFF", null, null, null, null, "draft", null, null), null, NOW);
        assertThat(normalized.code()).isEqualTo("WELCOME15");
        assertThat(normalized.minAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(normalized.totalLimit()).isEqualTo(100000);

        assertThatThrownBy(() -> CouponUpsertValidator.validate(new CouponUpsert("BAD CODE!", "Welcome",
                "discount", "15% OFF", null, null, NOW.plusDays(2), NOW.plusDays(1), "draft", null, null),
                null, NOW))
                .isInstanceOf(MarketingException.class)
                .satisfies(ex -> {
                    MarketingException me = (MarketingException) ex;
                    assertThat(me.getErrorCode()).isEqualTo(MarketingErrorCode.FIELD_VALIDATION_FAILED);
                    assertThat(fieldsOf(me))
                            .containsEntry("code", "pattern_invalid")
                            .containsEntry("end_at", "before_start");
                });
    }
}
