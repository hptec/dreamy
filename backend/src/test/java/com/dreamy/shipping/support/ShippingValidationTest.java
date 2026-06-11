package com.dreamy.shipping.support;

import com.dreamy.shipping.domain.enums.CarrierStatus;
import com.dreamy.shipping.dto.ShippingDtos.CarrierUpsert;
import com.dreamy.shipping.dto.ShippingDtos.ShippingRateUpsert;
import com.dreamy.shipping.error.ShippingErrorCode;
import com.dreamy.shipping.error.ShippingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V-SHP-002~011 入参校验单测（422901 details.field 口径；CV-SHP-001/002/004/007；bs-259~267 单测可达部分）。
 */
class ShippingValidationTest {

    @Test
    @DisplayName("V-SHP-002 id 路径参数：非数字/0/-1 → 422901 field=id")
    void parseIdRejectsInvalid() {
        assertThat(ShippingValidation.parseId("42")).isEqualTo(42L);
        for (String bad : new String[]{"abc", "0", "-1", "1.5", ""}) {
            assertThatThrownBy(() -> ShippingValidation.parseId(bad))
                    .isInstanceOf(ShippingException.class)
                    .satisfies(ex -> assertThat(((ShippingException) ex).getDetails())
                            .containsEntry("field", "id"));
        }
    }

    @Test
    @DisplayName("V-SHP-003/004 name/status 必填；name>64 拒绝（bs-259/262）")
    void carrierRequiredFields() {
        assertThatField(() -> ShippingValidation.validateCarrier(
                new CarrierUpsert(null, null, null, "enabled")), "name");
        assertThatField(() -> ShippingValidation.validateCarrier(
                new CarrierUpsert("  ", null, null, "enabled")), "name");
        assertThatField(() -> ShippingValidation.validateCarrier(
                new CarrierUpsert("x".repeat(65), null, null, "enabled")), "name");
        assertThatField(() -> ShippingValidation.validateCarrier(
                new CarrierUpsert("FedEx", null, null, null)), "status");
        assertThatField(() -> ShippingValidation.validateCarrier(
                new CarrierUpsert("FedEx", null, null, "paused")), "status");
    }

    @Test
    @DisplayName("V-SHP-005/006 zones/lead_time 可空容忍（bs-260/261）；超长拒绝")
    void carrierOptionalFields() {
        var valid = ShippingValidation.validateCarrier(new CarrierUpsert(" FedEx ", null, null, "disabled"));
        assertThat(valid.name()).isEqualTo("FedEx");
        assertThat(valid.zones()).isNull();
        assertThat(valid.leadTime()).isNull();
        assertThat(valid.status()).isEqualTo(CarrierStatus.DISABLED);
        assertThatField(() -> ShippingValidation.validateCarrier(
                new CarrierUpsert("FedEx", "z".repeat(256), null, "enabled")), "zones");
        assertThatField(() -> ShippingValidation.validateCarrier(
                new CarrierUpsert("FedEx", null, "t".repeat(65), "enabled")), "lead_time");
    }

    @Test
    @DisplayName("V-SHP-009 zone 必填规范化非空 ≤128（bs-264）")
    void rateZoneRequired() {
        assertThatField(() -> ShippingValidation.validateRate(
                new ShippingRateUpsert(null, null, null, null)), "zone");
        assertThatField(() -> ShippingValidation.validateRate(
                new ShippingRateUpsert("   ", null, null, null)), "zone");
        assertThatField(() -> ShippingValidation.validateRate(
                new ShippingRateUpsert("z".repeat(129), null, null, null)), "zone");
        var valid = ShippingValidation.validateRate(
                new ShippingRateUpsert(" North  America ", null, null, null));
        assertThat(valid.zoneNorm()).isEqualTo("North America");
    }

    @Test
    @DisplayName("V-SHP-010 费用字段：null 容忍（bs-265~267）；-1/超上限/3 位小数拒绝")
    void rateFeeValidation() {
        var allNull = ShippingValidation.validateRate(new ShippingRateUpsert("Europe", null, null, null));
        assertThat(allNull.feeUnder()).isNull();
        assertThat(allNull.feeOver()).isNull();
        assertThat(allNull.threshold()).isNull();
        assertThatField(() -> ShippingValidation.validateRate(
                new ShippingRateUpsert("Europe", new BigDecimal("-1"), null, null)), "fee_under");
        assertThatField(() -> ShippingValidation.validateRate(
                new ShippingRateUpsert("Europe", null, new BigDecimal("100000000.00"), null)), "fee_over");
        assertThatField(() -> ShippingValidation.validateRate(
                new ShippingRateUpsert("Europe", null, null, new BigDecimal("1.005"))), "threshold");
    }

    private void assertThatField(Runnable call, String field) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ShippingException.class)
                .satisfies(ex -> {
                    ShippingException se = (ShippingException) ex;
                    assertThat(se.getErrorCode()).isEqualTo(ShippingErrorCode.FIELD_VALIDATION_FAILED);
                    assertThat(se.getDetails()).containsEntry("field", field);
                });
    }
}
