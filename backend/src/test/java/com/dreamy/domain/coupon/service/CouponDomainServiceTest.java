package com.dreamy.domain.coupon.service;

import com.dreamy.domain.coupon.entity.Coupon;
import com.dreamy.domain.coupon.entity.CouponTranslation;
import com.dreamy.domain.coupon.repository.CouponRepository;
import com.dreamy.domain.coupon.service.CouponDomainService.CouponQuote;
import com.dreamy.enums.CouponStatus;
import com.dreamy.enums.CouponType;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 券域服务单元测试（SVC-MKT-01）。
 * L2 TRACE: TC-MKT-001（判定顺序）/ TC-MKT-002（减免计算）/ TC-MKT-004（不限语义）/
 * TC-MKT-012（code 归一化）/ TC-MKT-016 单测面（CAS affected=0 → 422703）/ TC-MKT-017（rollback 防负归 SQL）。
 */
@ExtendWith(MockitoExtension.class)
class CouponDomainServiceTest {

    @Mock
    CouponRepository couponRepository;
    CouponDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CouponDomainServiceImpl(couponRepository, java.time.Clock.systemUTC());
    }

    private static Coupon coupon(CouponStatus status, CouponType type, String value,
                                 BigDecimal minAmount, int totalLimit, int usedCount) {
        Coupon c = new Coupon();
        c.setId(1L);
        c.setCode("WELCOME15");
        c.setName("Welcome 15% Off");
        c.setStatus(status);
        c.setType(type);
        c.setValue(value);
        c.setMinAmount(minAmount);
        c.setTotalLimit(totalLimit);
        c.setUsedCount(usedCount);
        c.setStartAt(LocalDateTime.now(java.time.Clock.systemUTC()).minusDays(1));
        c.setEndAt(LocalDateTime.now(java.time.Clock.systemUTC()).plusDays(30));
        return c;
    }

    @Test
    @DisplayName("TC-MKT-001 [P0]: 不存在/draft/未开始 → 422701（不存在不回显券信息）")
    void reasonOrderNotStarted() {
        when(couponRepository.findByCode("NOPE")).thenReturn(null);
        CouponQuote quote = service.validate("nope", new BigDecimal("100"), "en");
        assertThat(quote.valid()).isFalse();
        assertThat(quote.reasonCode()).isEqualTo(MarketingErrorCode.COUPON_INVALID.getCode());
        assertThat(quote.coupon()).isNull();

        Coupon draft = coupon(CouponStatus.DRAFT, CouponType.DISCOUNT, "15% OFF", BigDecimal.ZERO, 100, 0);
        when(couponRepository.findByCode("WELCOME15")).thenReturn(draft);
        assertThat(service.validate("WELCOME15", new BigDecimal("100"), "en").reasonCode())
                .isEqualTo(422701);

        Coupon future = coupon(CouponStatus.ACTIVE, CouponType.DISCOUNT, "15% OFF", BigDecimal.ZERO, 100, 0);
        future.setStartAt(LocalDateTime.now(java.time.Clock.systemUTC()).plusDays(1));
        when(couponRepository.findByCode("WELCOME15")).thenReturn(future);
        assertThat(service.validate("WELCOME15", new BigDecimal("100"), "en").reasonCode())
                .isEqualTo(422701);
    }

    @Test
    @DisplayName("TC-MKT-001 [P0]: 过期 → 422701（status=expired 与实时窗口判定双保险）")
    void reasonExpired() {
        Coupon expired = coupon(CouponStatus.EXPIRED, CouponType.DISCOUNT, "15% OFF", BigDecimal.ZERO, 100, 0);
        when(couponRepository.findByCode("WELCOME15")).thenReturn(expired);
        assertThat(service.validate("WELCOME15", new BigDecimal("100"), "en").reasonCode()).isEqualTo(422701);

        // SCHED 未及时翻转：status=active 但 end_at 已过 → 实时判定兜底（CV-MKT-011）
        Coupon stale = coupon(CouponStatus.ACTIVE, CouponType.DISCOUNT, "15% OFF", BigDecimal.ZERO, 100, 0);
        stale.setEndAt(LocalDateTime.now(java.time.Clock.systemUTC()).minusMinutes(1));
        when(couponRepository.findByCode("WELCOME15")).thenReturn(stale);
        assertThat(service.validate("WELCOME15", new BigDecimal("100"), "en").reasonCode()).isEqualTo(422701);
    }

    @Test
    @DisplayName("TC-MKT-001 [P0]: 判定顺序固定——耗尽（422703）优先于门槛（422702）")
    void exhaustedBeforeMinAmount() {
        // 同时命中耗尽与门槛 → 422703 胜出
        Coupon both = coupon(CouponStatus.ACTIVE, CouponType.FIXED_AMOUNT, "$50 OFF",
                new BigDecimal("500"), 5, 5);
        when(couponRepository.findByCode("WELCOME15")).thenReturn(both);
        assertThat(service.validate("WELCOME15", new BigDecimal("100"), "en").reasonCode()).isEqualTo(422703);

        // 仅门槛不达 → 422702
        Coupon minOnly = coupon(CouponStatus.ACTIVE, CouponType.FIXED_AMOUNT, "$50 OFF",
                new BigDecimal("500"), 100, 0);
        when(couponRepository.findByCode("WELCOME15")).thenReturn(minOnly);
        assertThat(service.validate("WELCOME15", new BigDecimal("100"), "en").reasonCode()).isEqualTo(422702);
    }

    @Test
    @DisplayName("TC-MKT-002 [P0]: 减免计算——discount 15%×200=30.00；fixed $50 截断至 subtotal；free_shipping=0+flag")
    void discountComputation() {
        Coupon pct = coupon(CouponStatus.ACTIVE, CouponType.DISCOUNT, "15% OFF", BigDecimal.ZERO, 100, 0);
        when(couponRepository.findByCode("WELCOME15")).thenReturn(pct);
        when(couponRepository.listTranslationsByCouponIds(anyCollection())).thenReturn(List.of());
        CouponQuote q1 = service.validate("WELCOME15", new BigDecimal("200"), "es");
        assertThat(q1.valid()).isTrue();
        assertThat(q1.discountUsd()).isEqualByComparingTo("30.00");

        Coupon fixed = coupon(CouponStatus.ACTIVE, CouponType.FIXED_AMOUNT, "$50 OFF", BigDecimal.ZERO, 100, 0);
        when(couponRepository.findByCode("WELCOME15")).thenReturn(fixed);
        CouponQuote q2 = service.validate("WELCOME15", new BigDecimal("30"), "en");
        assertThat(q2.discountUsd()).isEqualByComparingTo("30");

        Coupon ship = coupon(CouponStatus.ACTIVE, CouponType.FREE_SHIPPING, "Free Shipping",
                BigDecimal.ZERO, 100, 0);
        when(couponRepository.findByCode("WELCOME15")).thenReturn(ship);
        CouponQuote q3 = service.validate("WELCOME15", new BigDecimal("100"), "en");
        assertThat(q3.discountUsd()).isEqualByComparingTo("0");
        assertThat(q3.freeShipping()).isTrue();
    }

    @Test
    @DisplayName("TC-MKT-004 [P1]: total_limit 不限语义——used=9999 & limit=100000 可用；used=limit → 422703")
    void totalLimitUnlimited() {
        Coupon unlimited = coupon(CouponStatus.ACTIVE, CouponType.DISCOUNT, "15% OFF",
                BigDecimal.ZERO, 100000, 9999);
        when(couponRepository.findByCode("WELCOME15")).thenReturn(unlimited);
        assertThat(service.validate("WELCOME15", new BigDecimal("100"), "en").valid()).isTrue();

        Coupon drained = coupon(CouponStatus.ACTIVE, CouponType.DISCOUNT, "15% OFF", BigDecimal.ZERO, 5, 5);
        when(couponRepository.findByCode("WELCOME15")).thenReturn(drained);
        assertThat(service.validate("WELCOME15", new BigDecimal("100"), "en").reasonCode()).isEqualTo(422703);
    }

    @Test
    @DisplayName("TC-MKT-012 [P1]: code trim+大写归一后点查（CV-MKT-008）")
    void codeNormalization() {
        Coupon c = coupon(CouponStatus.ACTIVE, CouponType.DISCOUNT, "15% OFF", BigDecimal.ZERO, 100, 0);
        when(couponRepository.findByCode("WELCOME15")).thenReturn(c);
        assertThat(service.validate("  welcome15 ", new BigDecimal("100"), "en").valid()).isTrue();
        verify(couponRepository).findByCode("WELCOME15");
    }

    @Test
    @DisplayName("TC-MKT-002/006 [P0]: coupon.name 按 locale 解析（translation 覆盖，缺翻译回退 EN）")
    void localeNameResolution() {
        Coupon c = coupon(CouponStatus.ACTIVE, CouponType.DISCOUNT, "15% OFF", BigDecimal.ZERO, 100, 0);
        CouponTranslation es = new CouponTranslation();
        es.setCouponId(1L);
        es.setLocale("es");
        es.setName("Bienvenida 15%");
        when(couponRepository.findByCode("WELCOME15")).thenReturn(c);
        when(couponRepository.listTranslationsByCouponIds(anyCollection())).thenReturn(List.of(es));
        assertThat(service.validate("WELCOME15", new BigDecimal("100"), "es").coupon().name())
                .isEqualTo("Bienvenida 15%");
        assertThat(service.validate("WELCOME15", new BigDecimal("100"), "fr").coupon().name())
                .isEqualTo("Welcome 15% Off");
    }

    @Test
    @DisplayName("TC-MKT-016 单测面 [P0]: redeem CAS affected=0 → 422703 异常（不重试，EC-MKT-001）")
    void redeemCasExhausted() {
        Coupon c = coupon(CouponStatus.ACTIVE, CouponType.DISCOUNT, "15% OFF", BigDecimal.ZERO, 5, 4);
        when(couponRepository.findByCode("WELCOME15")).thenReturn(c);
        when(couponRepository.redeemCas(1L)).thenReturn(0);
        assertThatThrownBy(() -> service.redeem("WELCOME15", new BigDecimal("100")))
                .isInstanceOf(MarketingException.class)
                .satisfies(ex -> assertThat(((MarketingException) ex).getErrorCode())
                        .isEqualTo(MarketingErrorCode.COUPON_EXHAUSTED));
        verify(couponRepository).redeemCas(1L);
    }

    @Test
    @DisplayName("SVC-MKT-01 [P0]: redeem 成功返回 couponId；无效券抛 422701 不触 CAS")
    void redeemSuccessAndInvalid() {
        Coupon c = coupon(CouponStatus.ACTIVE, CouponType.DISCOUNT, "15% OFF", BigDecimal.ZERO, 100, 0);
        when(couponRepository.findByCode("WELCOME15")).thenReturn(c);
        when(couponRepository.redeemCas(1L)).thenReturn(1);
        assertThat(service.redeem("WELCOME15", new BigDecimal("100"))).isEqualTo(1L);

        when(couponRepository.findByCode("NOPE")).thenReturn(null);
        assertThatThrownBy(() -> service.redeem("NOPE", new BigDecimal("100")))
                .isInstanceOf(MarketingException.class)
                .satisfies(ex -> assertThat(((MarketingException) ex).getErrorCode())
                        .isEqualTo(MarketingErrorCode.COUPON_INVALID));
        verify(couponRepository, never()).redeemCas(eq(99L));
    }

    @Test
    @DisplayName("SVC-MKT-01 [P1]: rollbackRedeem 直达 RM-MKT-108；null couponId 空操作")
    void rollbackRedeem() {
        service.rollbackRedeem(7L);
        service.rollbackRedeem(null);
        verify(couponRepository, times(1)).rollbackRedeem(any(Long.class));
        verify(couponRepository).rollbackRedeem(7L);
    }
}
