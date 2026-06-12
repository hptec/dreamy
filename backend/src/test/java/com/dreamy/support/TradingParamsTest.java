package com.dreamy.support;

import com.dreamy.dto.TradingDtos.CustomSizeData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 参数校验/规范化纯函数单测。
 * L2 TRACE: TC-TRD-003 [P0]（定制四围缺一 → 422601 字段级）/ CV-TRD-006 / RM-TRD-003 哈希口径 /
 * TC-TRD-052 [P1]（枚举取值与契约逐字相等）。
 */
class TradingParamsTest {

    @Test
    @DisplayName("TC-TRD-003 [P0]: 定制四围缺一 → fields 收集（hollow_to_floor required）")
    void customSizeMissingField() {
        TradingFieldErrors errors = new TradingFieldErrors();
        TradingParams.validateCustomSize(new CustomSizeData(
                new BigDecimal("36"), new BigDecimal("28"), new BigDecimal("38"), null, null), errors);
        assertThat(errors.hasErrors()).isTrue();
        assertThat(errors.fields()).containsKey("custom_size_data.hollow_to_floor");
    }

    @Test
    @DisplayName("CV-TRD-006: 负值 → range_invalid；height 选填合法")
    void customSizeNegative() {
        TradingFieldErrors errors = new TradingFieldErrors();
        TradingParams.validateCustomSize(new CustomSizeData(
                new BigDecimal("-1"), new BigDecimal("28"), new BigDecimal("38"),
                new BigDecimal("58"), null), errors);
        assertThat(errors.fields()).containsEntry("custom_size_data.bust", "range_invalid");

        TradingFieldErrors ok = new TradingFieldErrors();
        TradingParams.validateCustomSize(new CustomSizeData(
                new BigDecimal("36"), new BigDecimal("28"), new BigDecimal("38"),
                new BigDecimal("58"), new BigDecimal("65")), ok);
        assertThat(ok.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("RM-TRD-003: 同定制数据规范化哈希稳定（尾零归一）；不同数据不同哈希")
    void customSizeHashNormalized() {
        CustomSizeData a = new CustomSizeData(new BigDecimal("36.0"), new BigDecimal("28"),
                new BigDecimal("38.00"), new BigDecimal("58"), null);
        CustomSizeData b = new CustomSizeData(new BigDecimal("36"), new BigDecimal("28.0"),
                new BigDecimal("38"), new BigDecimal("58.000"), null);
        CustomSizeData c = new CustomSizeData(new BigDecimal("37"), new BigDecimal("28"),
                new BigDecimal("38"), new BigDecimal("58"), null);
        assertThat(TradingParams.customSizeHash(a)).isEqualTo(TradingParams.customSizeHash(b));
        assertThat(TradingParams.customSizeHash(a)).isNotEqualTo(TradingParams.customSizeHash(c));
        assertThat(TradingParams.customSizeHash(a)).hasSize(64);
    }

    @Test
    @DisplayName("TC-TRD-052 [P1]: 枚举取值与契约逐字相等（币种五值/承运商三值全称/支付方式）")
    void enumLiterals() {
        assertThat(TradingParams.CURRENCIES).containsExactly("USD", "EUR", "CAD", "AUD", "GBP");
        assertThat(TradingParams.CARRIERS).containsExactly(
                "FedEx International Priority", "UPS Worldwide Express", "DHL Express");
        assertThat(TradingParams.PAYMENT_METHODS)
                .containsExactly("Stripe", "Apple Pay", "Google Pay", "Klarna", "Afterpay");
        assertThat(TradingParams.isSupportedCarrier("FedEx")).isFalse();
        assertThat(TradingParams.isSupportedCurrency("JPY")).isFalse();
    }

    @Test
    @DisplayName("V-TRD-030: page/page_size 边界（越界收集错误并回退缺省）")
    void pageBounds() {
        TradingFieldErrors errors = new TradingFieldErrors();
        assertThat(TradingParams.parsePage(null, errors)).isEqualTo(1);
        assertThat(TradingParams.parsePageSize(null, errors)).isEqualTo(20);
        assertThat(errors.hasErrors()).isFalse();
        TradingParams.parsePageSize(101, errors);
        assertThat(errors.fields()).containsEntry("page_size", "range_invalid");
    }
}
