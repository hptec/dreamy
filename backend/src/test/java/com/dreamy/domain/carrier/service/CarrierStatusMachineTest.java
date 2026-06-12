package com.dreamy.domain.carrier.service;

import com.dreamy.enums.CarrierStatus;
import com.dreamy.error.ShippingErrorCode;
import com.dreamy.error.ShippingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TC-SHP-011：carrier_status 迁移纯 guard（TASK-050；state-machine carrier_status + 409902 业务 guard 叠加）。
 */
class CarrierStatusMachineTest {

    @Test
    @DisplayName("enabled --disable--> disabled 合法（enabledCount>1）")
    void disableLegalWhenMoreThanOneEnabled() {
        assertThat(CarrierStatusMachine.evaluate(CarrierStatus.ENABLED, CarrierStatus.DISABLED, 3))
                .isEqualTo(CarrierStatusMachine.Transition.APPLY);
    }

    @Test
    @DisplayName("enabledCount==1 时 disable 拒绝 → 409902 LAST_ENABLED_CARRIER")
    void disableRejectedWhenLastEnabled() {
        assertThatThrownBy(() ->
                CarrierStatusMachine.evaluate(CarrierStatus.ENABLED, CarrierStatus.DISABLED, 1))
                .isInstanceOf(ShippingException.class)
                .satisfies(ex -> {
                    ShippingException se = (ShippingException) ex;
                    assertThat(se.getErrorCode()).isEqualTo(ShippingErrorCode.LAST_ENABLED_CARRIER);
                    assertThat(se.getErrorCode().getCode()).isEqualTo(409902);
                    assertThat(se.getDetails()).containsEntry("enabled_count", 1);
                });
    }

    @Test
    @DisplayName("disabled --enable--> enabled 恒合法（无 guard）")
    void enableAlwaysLegal() {
        assertThat(CarrierStatusMachine.evaluate(CarrierStatus.DISABLED, CarrierStatus.ENABLED, 0))
                .isEqualTo(CarrierStatusMachine.Transition.APPLY);
        assertThat(CarrierStatusMachine.evaluate(CarrierStatus.DISABLED, CarrierStatus.ENABLED, 5))
                .isEqualTo(CarrierStatusMachine.Transition.APPLY);
    }

    @Test
    @DisplayName("同值迁移 = 幂等短路（NOOP 无副作用，含最后启用行提交 enabled）")
    void sameValueIsIdempotentNoop() {
        assertThat(CarrierStatusMachine.evaluate(CarrierStatus.ENABLED, CarrierStatus.ENABLED, 1))
                .isEqualTo(CarrierStatusMachine.Transition.NOOP);
        assertThat(CarrierStatusMachine.evaluate(CarrierStatus.DISABLED, CarrierStatus.DISABLED, 2))
                .isEqualTo(CarrierStatusMachine.Transition.NOOP);
    }
}
