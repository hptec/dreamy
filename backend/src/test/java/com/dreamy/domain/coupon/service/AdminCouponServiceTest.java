package com.dreamy.domain.coupon.service;

import com.dreamy.domain.coupon.entity.Coupon;
import com.dreamy.domain.coupon.repository.CouponRepository;
import com.dreamy.enums.CouponStatus;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import com.dreamy.infra.MarketingAuditRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCouponServiceTest {

    @Mock
    CouponRepository couponRepository;
    @Mock
    MarketingAuditRecorder audit;
    @InjectMocks
    AdminCouponService service;

    @Test
    @DisplayName("TX-MKT-003: 优惠券仅 draft/expired 且未核销可删，先清译文再物理删除")
    void deleteGuardsAndPhysicallyDeletes() {
        Coupon live = coupon(1L, CouponStatus.ACTIVE, 0);
        when(couponRepository.findById(1L)).thenReturn(live);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(MarketingException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((MarketingException) ex).getErrorCode())
                        .isEqualTo(MarketingErrorCode.CONTENT_STATE_INVALID));
        verify(couponRepository, never()).deleteTranslationsByCouponId(1L);
        verify(couponRepository, never()).deleteById(1L);

        Coupon redeemed = coupon(2L, CouponStatus.DRAFT, 1);
        when(couponRepository.findById(2L)).thenReturn(redeemed);
        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOf(MarketingException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((MarketingException) ex).getErrorCode())
                        .isEqualTo(MarketingErrorCode.CONTENT_STATE_INVALID));
        verify(couponRepository, never()).deleteTranslationsByCouponId(2L);
        verify(couponRepository, never()).deleteById(2L);

        Coupon expired = coupon(3L, CouponStatus.EXPIRED, 0);
        when(couponRepository.findById(3L)).thenReturn(expired);
        service.delete(3L);
        InOrder order = inOrder(couponRepository);
        order.verify(couponRepository).deleteTranslationsByCouponId(3L);
        order.verify(couponRepository).deleteById(3L);
        verify(couponRepository, never()).update(any());
        verify(audit).record("删除优惠券", "SAVE10", null);
    }

    private static Coupon coupon(long id, CouponStatus status, int usedCount) {
        Coupon coupon = new Coupon();
        coupon.setId(id);
        coupon.setCode("SAVE10");
        coupon.setStatus(status);
        coupon.setUsedCount(usedCount);
        return coupon;
    }
}
