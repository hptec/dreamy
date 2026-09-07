package com.dreamy.domain.checkout.service;

import com.dreamy.domain.checkout.entity.CheckoutConfig;
import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.dto.TradingDtos.CheckoutConfigDto;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingAuditRecorder;
import com.dreamy.testsupport.TradingImmediateTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 结算配置服务单测（order-flow-complete G：5 个新字段范围校验 + 旧签名兼容 + 审计快照）。 */
@ExtendWith(MockitoExtension.class)
class CheckoutConfigServiceTest {

    @Mock
    CheckoutConfigRepository repository;
    @Mock
    TradingAuditRecorder audit;

    CheckoutConfigService service;

    @BeforeEach
    void setUp() {
        service = new CheckoutConfigService(repository, new TradingImmediateTxRunner(), audit);
        CheckoutConfig current = new CheckoutConfig();
        current.setGiftWrapFeeUsd(new BigDecimal("15.00"));
        current.setCustomRefundGraceHours(24);
        current.setAutoCompleteDays(7);
        current.setAutoDeliverDays(30);
        current.setPendingTimeoutMinutes(30);
        current.setExchangeRateSpreadScaled(0);
        current.setProductionDaysDefault(21);
        when(repository.getSingleton()).thenReturn(current);
    }

    private CheckoutConfigDto valid() {
        return new CheckoutConfigDto(new BigDecimal("12.00"), 48, 10, 45, 60, 150, 28);
    }

    @Test
    @DisplayName("get 返回 7 字段；update 全字段落库 + 审计 before/after 含新字段")
    void getAndUpdate() {
        CheckoutConfigDto dto = service.get();
        assertThat(dto.autoCompleteDays()).isEqualTo(7);
        assertThat(dto.productionDaysDefault()).isEqualTo(21);

        service.update(valid());
        ArgumentCaptor<CheckoutConfig> captor = ArgumentCaptor.forClass(CheckoutConfig.class);
        verify(repository).update(captor.capture());
        CheckoutConfig saved = captor.getValue();
        assertThat(saved.getAutoCompleteDays()).isEqualTo(10);
        assertThat(saved.getAutoDeliverDays()).isEqualTo(45);
        assertThat(saved.getPendingTimeoutMinutes()).isEqualTo(60);
        assertThat(saved.getExchangeRateSpreadScaled()).isEqualTo(150);
        assertThat(saved.getProductionDaysDefault()).isEqualTo(28);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq(TradingAuditRecorder.ACTION_CHECKOUT_CONFIG), eq("checkout_config"), json.capture());
        assertThat(json.getValue()).contains("\"pending_timeout_minutes\":30").contains("\"pending_timeout_minutes\":60");
    }

    @Test
    @DisplayName("旧签名 update(fee, grace) 保留其余字段现值")
    void legacyUpdateKeepsOthers() {
        service.update(new BigDecimal("20.00"), 12);
        ArgumentCaptor<CheckoutConfig> captor = ArgumentCaptor.forClass(CheckoutConfig.class);
        verify(repository).update(captor.capture());
        assertThat(captor.getValue().getGiftWrapFeeUsd()).isEqualByComparingTo("20.00");
        assertThat(captor.getValue().getCustomRefundGraceHours()).isEqualTo(12);
        assertThat(captor.getValue().getAutoDeliverDays()).isEqualTo(30);
        assertThat(captor.getValue().getProductionDaysDefault()).isEqualTo(21);
    }

    @Test
    @DisplayName("范围校验：auto_complete_days 1..60 / auto_deliver_days 1..120 / pending_timeout_minutes 5..1440 / spread 0..2000 / production_days 1..180；越界 → 422601 fields")
    void rangeValidation() {
        assertRejected(new CheckoutConfigDto(new BigDecimal("12.00"), 48, 0, 45, 60, 150, 28), "auto_complete_days");
        assertRejected(new CheckoutConfigDto(new BigDecimal("12.00"), 48, 61, 45, 60, 150, 28), "auto_complete_days");
        assertRejected(new CheckoutConfigDto(new BigDecimal("12.00"), 48, 10, 121, 60, 150, 28), "auto_deliver_days");
        assertRejected(new CheckoutConfigDto(new BigDecimal("12.00"), 48, 10, 45, 4, 150, 28), "pending_timeout_minutes");
        assertRejected(new CheckoutConfigDto(new BigDecimal("12.00"), 48, 10, 45, 1441, 150, 28), "pending_timeout_minutes");
        assertRejected(new CheckoutConfigDto(new BigDecimal("12.00"), 48, 10, 45, 60, 2001, 28), "exchange_rate_spread_scaled");
        assertRejected(new CheckoutConfigDto(new BigDecimal("12.00"), 48, 10, 45, 60, -1, 28), "exchange_rate_spread_scaled");
        assertRejected(new CheckoutConfigDto(new BigDecimal("12.00"), 48, 10, 45, 60, 150, 181), "production_days_default");
        assertRejected(new CheckoutConfigDto(new BigDecimal("12.00"), 48, 10, 45, 60, 150, null), "production_days_default");
        // 边界值通过
        service.update(new CheckoutConfigDto(BigDecimal.ZERO, 1, 1, 1, 5, 0, 1));
        service.update(new CheckoutConfigDto(BigDecimal.ZERO, 168, 60, 120, 1440, 2000, 180));
    }

    @SuppressWarnings("unchecked")
    private void assertRejected(CheckoutConfigDto dto, String field) {
        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOfSatisfying(TradingException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED);
                    assertThat((Map<String, String>) ex.getDetails().get("fields")).containsKey(field);
                });
        verify(repository, never()).update(any());
        verify(audit, never()).record(anyString(), anyString(), anyString());
    }
}
