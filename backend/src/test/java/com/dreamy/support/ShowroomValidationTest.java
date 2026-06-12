package com.dreamy.support;

import com.dreamy.enums.VoteValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 字段边界族单元测试。
 * L2 TRACE: TC-SHR-006 [P0]（V-SHR-001~023 / CV-SHR-001/002 全字段 422101 fields 结构）/
 * TC-SHR-003 [P1]（CV-SHR-003 color 归一化）。
 */
class ShowroomValidationTest {

    // ==================== name（bs-535/353） ====================

    @Test
    @DisplayName("name：64 过 / 65 拒 too_long / trim 空拒 blank")
    void nameBoundary() {
        ShowroomFieldErrors ok = new ShowroomFieldErrors();
        assertThat(ShowroomValidation.validateName("a".repeat(64), ok)).hasSize(64);
        assertThat(ok.hasErrors()).isFalse();

        ShowroomFieldErrors tooLong = new ShowroomFieldErrors();
        ShowroomValidation.validateName("a".repeat(65), tooLong);
        assertThat(tooLong.fields()).containsEntry("name", "too_long");

        ShowroomFieldErrors blank = new ShowroomFieldErrors();
        ShowroomValidation.validateName("   ", blank);
        assertThat(blank.fields()).containsEntry("name", "blank");

        ShowroomFieldErrors nul = new ShowroomFieldErrors();
        ShowroomValidation.validateName(null, nul);
        assertThat(nul.fields()).containsEntry("name", "blank");
    }

    // ==================== wedding_date（V-SHR-002） ====================

    @Test
    @DisplayName("wedding_date：合法 ISO 过 / 非法日期拒 invalid_date / 缺省 null 过（可选）")
    void weddingDateBoundary() {
        ShowroomFieldErrors ok = new ShowroomFieldErrors();
        assertThat(ShowroomValidation.validateWeddingDate("2026-09-19", ok))
                .isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(ok.hasErrors()).isFalse();

        ShowroomFieldErrors invalid = new ShowroomFieldErrors();
        ShowroomValidation.validateWeddingDate("2026-13-45", invalid);
        assertThat(invalid.fields()).containsEntry("wedding_date", "invalid_date");

        ShowroomFieldErrors absent = new ShowroomFieldErrors();
        assertThat(ShowroomValidation.validateWeddingDate(null, absent)).isNull();
        assertThat(absent.hasErrors()).isFalse();

        // 过去日期不限制（原型创建弹窗无此限制，保真）
        ShowroomFieldErrors past = new ShowroomFieldErrors();
        assertThat(ShowroomValidation.validateWeddingDate("2020-01-01", past)).isNotNull();
        assertThat(past.hasErrors()).isFalse();
    }

    // ==================== nickname（bs-538/362） ====================

    @Test
    @DisplayName("nickname：32 过 / 33 拒 too_long / trim 空拒 blank")
    void nicknameBoundary() {
        ShowroomFieldErrors ok = new ShowroomFieldErrors();
        assertThat(ShowroomValidation.validateNickname(" Emma ", ok)).isEqualTo("Emma");
        assertThat(ShowroomValidation.validateNickname("n".repeat(32), ok)).hasSize(32);
        assertThat(ok.hasErrors()).isFalse();

        ShowroomFieldErrors tooLong = new ShowroomFieldErrors();
        ShowroomValidation.validateNickname("n".repeat(33), tooLong);
        assertThat(tooLong.fields()).containsEntry("nickname", "too_long");

        ShowroomFieldErrors blank = new ShowroomFieldErrors();
        ShowroomValidation.validateNickname("  ", blank);
        assertThat(blank.fields()).containsEntry("nickname", "blank");
    }

    // ==================== content（bs-542/374） ====================

    @Test
    @DisplayName("content：500 过 / 501 拒 too_long / trim 空拒 blank")
    void contentBoundary() {
        ShowroomFieldErrors ok = new ShowroomFieldErrors();
        assertThat(ShowroomValidation.validateContent("c".repeat(500), ok)).hasSize(500);
        assertThat(ok.hasErrors()).isFalse();

        ShowroomFieldErrors tooLong = new ShowroomFieldErrors();
        ShowroomValidation.validateContent("c".repeat(501), tooLong);
        assertThat(tooLong.fields()).containsEntry("content", "too_long");

        ShowroomFieldErrors blank = new ShowroomFieldErrors();
        ShowroomValidation.validateContent(" \n ", blank);
        assertThat(blank.fields()).containsEntry("content", "blank");
    }

    // ==================== email（bs-539/363） ====================

    @Test
    @DisplayName("email：合法过 / 非法格式拒 invalid_format / 255 拒 too_long / 空白视为未提供")
    void emailBoundary() {
        ShowroomFieldErrors ok = new ShowroomFieldErrors();
        assertThat(ShowroomValidation.validateEmail("emma@example.com", ok)).isEqualTo("emma@example.com");
        assertThat(ok.hasErrors()).isFalse();

        ShowroomFieldErrors invalid = new ShowroomFieldErrors();
        ShowroomValidation.validateEmail("not-an-email", invalid);
        assertThat(invalid.fields()).containsEntry("email", "invalid_format");

        ShowroomFieldErrors tooLong = new ShowroomFieldErrors();
        ShowroomValidation.validateEmail("a".repeat(248) + "@ex.com", tooLong);
        assertThat(tooLong.fields()).containsEntry("email", "too_long");

        ShowroomFieldErrors absent = new ShowroomFieldErrors();
        assertThat(ShowroomValidation.validateEmail("  ", absent)).isNull();
        assertThat(absent.hasErrors()).isFalse();
    }

    // ==================== color（bs-537/359；CV-SHR-003 归一化） ====================

    @Test
    @DisplayName("color 归一化：null/''/'  ' → 空串；'Dusty Rose' trim 原样；65 拒 too_long")
    void colorNormalization() {
        ShowroomFieldErrors errors = new ShowroomFieldErrors();
        assertThat(ShowroomValidation.normalizeColor(null, errors)).isEmpty();
        assertThat(ShowroomValidation.normalizeColor("", errors)).isEmpty();
        assertThat(ShowroomValidation.normalizeColor("   ", errors)).isEmpty();
        assertThat(ShowroomValidation.normalizeColor(" Dusty Rose ", errors)).isEqualTo("Dusty Rose");
        assertThat(errors.hasErrors()).isFalse();

        ShowroomFieldErrors tooLong = new ShowroomFieldErrors();
        ShowroomValidation.normalizeColor("c".repeat(65), tooLong);
        assertThat(tooLong.fields()).containsEntry("color", "too_long");
    }

    // ==================== invite_token（bs-536/355） ====================

    @Test
    @DisplayName("invite_token：缺失拒 required / 65 拒 too_long")
    void inviteTokenBoundary() {
        ShowroomFieldErrors required = new ShowroomFieldErrors();
        ShowroomValidation.validateInviteToken(null, required);
        assertThat(required.fields()).containsEntry("invite_token", "required");

        ShowroomFieldErrors tooLong = new ShowroomFieldErrors();
        ShowroomValidation.validateInviteToken("t".repeat(65), tooLong);
        assertThat(tooLong.fields()).containsEntry("invite_token", "too_long");
    }

    // ==================== vote（bs-541/370） ====================

    @Test
    @DisplayName("vote：like/dislike 过 / __invalid__ 拒 invalid_enum / 缺失拒 required")
    void voteBoundary() {
        ShowroomFieldErrors ok = new ShowroomFieldErrors();
        assertThat(ShowroomValidation.validateVote(1, ok)).isEqualTo(VoteValue.LIKE);
        assertThat(ShowroomValidation.validateVote(2, ok)).isEqualTo(VoteValue.DISLIKE);
        assertThat(ok.hasErrors()).isFalse();

        ShowroomFieldErrors invalid = new ShowroomFieldErrors();
        ShowroomValidation.validateVote(99, invalid);
        assertThat(invalid.fields()).containsEntry("vote", "invalid_enum");

        ShowroomFieldErrors required = new ShowroomFieldErrors();
        ShowroomValidation.validateVote(null, required);
        assertThat(required.fields()).containsEntry("vote", "required");
    }

    // ==================== locale（V-SHR-004） ====================

    @Test
    @DisplayName("locale：en/es/fr 过、缺省 en / 枚举外拒 invalid_enum")
    void localeBoundary() {
        ShowroomFieldErrors ok = new ShowroomFieldErrors();
        assertThat(ShowroomValidation.validateLocale(null, ok)).isEqualTo("en");
        assertThat(ShowroomValidation.validateLocale("fr", ok)).isEqualTo("fr");
        assertThat(ok.hasErrors()).isFalse();

        ShowroomFieldErrors invalid = new ShowroomFieldErrors();
        ShowroomValidation.validateLocale("zz", invalid);
        assertThat(invalid.fields()).containsEntry("locale", "invalid_enum");
    }

    // ==================== 必填 id（V-SHR-013/021） ====================

    @Test
    @DisplayName("product_id/assigned_item_id：缺失拒 required / 非正拒 invalid")
    void requiredIdBoundary() {
        ShowroomFieldErrors required = new ShowroomFieldErrors();
        ShowroomValidation.validateRequiredId(null, "product_id", required);
        assertThat(required.fields()).containsEntry("product_id", "required");

        ShowroomFieldErrors invalid = new ShowroomFieldErrors();
        ShowroomValidation.validateRequiredId(0L, "assigned_item_id", invalid);
        assertThat(invalid.fields()).containsEntry("assigned_item_id", "invalid");
    }
}
